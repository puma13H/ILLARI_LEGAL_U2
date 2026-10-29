"""Integration checks for a NEW, labelled phase-3 verification stack. Standard library only.
Never target a real realm/database: writes are guarded by compose project and environment label.
No tokens, passwords or raw token responses are written to the results file.
"""
import argparse, base64, hashlib, html, http.cookiejar, json, re, secrets, subprocess, time, uuid
from pathlib import Path
from urllib.request import Request, build_opener, HTTPCookieProcessor, HTTPRedirectHandler, ProxyHandler
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse, parse_qs
from html.parser import HTMLParser

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None

class LocalBrowserCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    # Browsers treat HTTP localhost as a trustworthy origin; urllib otherwise drops Secure cookies there.
    def return_ok_secure(self,cookie,request):
        if urlparse(request.full_url).hostname in ("localhost","127.0.0.1"):
            return True
        return super().return_ok_secure(cookie,request)

class LoginForm(HTMLParser):
    def __init__(self): super().__init__(); self.action=None
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if tag=="form" and attrs.get("id")=="kc-form-login": self.action=attrs.get("action")

class Suite:
    def __init__(self,args):
        self.args=args; self.results=[]; self.ids={}; self.run=uuid.uuid4().hex[:10]
        self.env={}
        for line in Path(args.env_file).read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k,v=line.split("=",1); self.env[k]=v
        if not re.fullmatch(r"illari-fase3-[a-z0-9-]+",args.project): raise RuntimeError("Use a new phase-3 test project.")
        self.compose=["docker","compose","--env-file",str(Path(args.env_file).resolve()),"-p",args.project]
        labels=json.loads(self.cmd(["docker","inspect","--format","{{json .Config.Labels}}",self.cid("postgres")]))
        if labels.get("illari.environment")!="verification": raise RuntimeError("Refusing writes to a non-verification database.")
        self.gateway=self.base("legal-gateway",18080)
        self.kc=self.env["KEYCLOAK_PUBLIC_URL"].rstrip("/")
        self.front=self.env["FRONTEND_PUBLIC_URL"].rstrip("/")
        self.frontend_dev=getattr(args,"frontend_dev",False)
        endpoints=[("keycloak",8080,self.kc)]
        if not self.frontend_dev: endpoints.append(("frontend",80,self.front))
        elif urlparse(self.front).scheme!="http" or urlparse(self.front).hostname not in ("localhost","127.0.0.1"):
            raise RuntimeError("Development Angular must be an explicit local HTTP origin.")
        for service,port,public in endpoints:
            target=urlparse(public);owned=urlparse(self.base(service,port))
            if target.scheme!="http" or target.hostname not in ("localhost","127.0.0.1") or (target.port or 80)!=owned.port:
                raise RuntimeError("Public verification endpoint is outside the labelled project: "+service)
        self.realm="illari-legal"
        self.issuer=self.kc+"/realms/"+self.realm
        self.token_endpoint=self.issuer+"/protocol/openid-connect/token"
    def cmd(self,args,data=None,ok=True):
        p=subprocess.run(args,input=data,text=True,encoding="utf-8",errors="replace",capture_output=True)
        if ok and p.returncode: raise RuntimeError("Command failed: "+str(args[:3])+"; exit="+str(p.returncode))
        return p.stdout.strip() if ok else p
    def cid(self,service):
        if service not in self.ids:
            self.ids[service]=self.cmd(self.compose+["ps","-q",service])
            if not self.ids[service]: raise RuntimeError("Missing container: "+service)
        return self.ids[service]
    def base(self,service,port):
        mapped=json.loads(self.cmd(["docker","inspect","--format","{{json .NetworkSettings.Ports}}",self.cid(service)]))
        return "http://localhost:"+mapped[str(port)+"/tcp"][0]["HostPort"]
    def sql(self,db,sql):
        return self.cmd(["docker","exec","-i",self.cid("postgres"),"psql","-X","-U","postgres","-d",db,"-v","ON_ERROR_STOP=1","-At"],sql)
    def record(self,name,passed,actual=None):
        self.results.append({"name":name,"passed":bool(passed),"actual":actual,"at":time.time()})
        print(("PASS " if passed else "FAIL ")+name,flush=True)
        self.save()
    def save(self):
        Path(self.args.output).write_text(json.dumps({"project":self.args.project,"run":self.run,
            "frontend_mode":"ng-serve; nginx not tested" if self.frontend_dev else "compose-nginx",
            "passed":sum(r["passed"] for r in self.results),"failed":sum(not r["passed"] for r in self.results),
            "results":self.results},indent=2,ensure_ascii=False),encoding="utf-8")
    def http(self,method,url,token=None,data=None,form=False,opener=None,headers=None,raw_body=None):
        h=dict(headers or {})
        parsed=urlparse(url)
        if parsed.hostname not in ("localhost","127.0.0.1"): raise RuntimeError("Refusing external verification destination.")
        if parsed.hostname=="localhost" and not (self.frontend_dev and url.startswith(self.front+"/")):
            # Host ports bind IPv4. Preserve the public Host/issuer while avoiding Windows IPv6 fallback delays.
            h.setdefault("Host",parsed.netloc)
            url=url.replace("://localhost","://127.0.0.1",1)
        if token: h["Authorization"]="Bearer "+token
        body=raw_body
        if data is not None:
            h["Content-Type"]="application/x-www-form-urlencoded" if form else "application/json"
            body=(urlencode(data) if form else json.dumps(data)).encode()
        req=Request(url,data=body,headers=h,method=method)
        try:
            with (opener or build_opener(ProxyHandler({}))).open(req,timeout=30) as response:
                return response.status,response.read(),dict(response.headers)
        except HTTPError as error:
            return error.code,error.read(),dict(error.headers)
    def expect(self,name,method,url,expected,token=None,data=None,fatal=False,**kwargs):
        status,body,headers=self.http(method,url,token,data,**kwargs)
        success=status in ([expected] if isinstance(expected,int) else expected)
        diagnostic=status if success else {"status":status,"authentication":headers.get("WWW-Authenticate") or headers.get("Www-Authenticate")}
        self.record(name,success,diagnostic)
        if fatal and not success: raise RuntimeError("Setup check failed: "+name+"; HTTP "+str(status))
        try: result=json.loads(body)
        except (ValueError,UnicodeDecodeError): result=body
        return result,headers
    def admin(self,method,path,data=None,expected=200):
        if self.payload(self.admin_token)["exp"] < time.time()+15:
            tokens,_=self.expect("Refresh Keycloak admin session","POST",self.kc+"/realms/master/protocol/openid-connect/token",
                200,data={"client_id":"admin-cli","grant_type":"refresh_token","refresh_token":self.admin_refresh},form=True,fatal=True)
            self.admin_token=tokens["access_token"];self.admin_refresh=tokens["refresh_token"]
        return self.expect("Keycloak admin "+method+" "+path.split("/")[0],method,
            self.kc+"/admin/realms/"+self.realm+"/"+path,expected,self.admin_token,data,fatal=True)[0]
    def create_user(self,role):
        login="phase3-"+role.lower()+"-"+self.run+"-"+secrets.token_hex(2)
        password=secrets.token_urlsafe(24)
        # Accounts are NEW. Never reset an existing user's credentials.
        self.admin("POST","users",{"username":login,"enabled":True,"email":login+"@example.test",
            "emailVerified":True,"firstName":"Phase3","lastName":role,
            "credentials":[{"type":"password","value":password,"temporary":False}]},201)
        users=self.admin("GET","users?username="+login)
        uid=next(u["id"] for u in users if u["username"]==login)
        role_data=self.admin("GET","roles/"+role)
        self.admin("POST","users/"+uid+"/role-mappings/realm",[role_data],204)
        return {"username":login,"password":password,"sub":uid}
    def authorization(self,user,client="illari-frontend",challenge=True):
        verifier=secrets.token_urlsafe(64)
        state=secrets.token_urlsafe(24); nonce=secrets.token_urlsafe(24)
        params={"client_id":client,"response_type":"code","redirect_uri":self.front+"/auth/callback",
            "scope":"openid profile email","state":state,"nonce":nonce}
        if challenge: params.update(code_challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("="),code_challenge_method="S256")
        jar=http.cookiejar.CookieJar(policy=LocalBrowserCookiePolicy())
        opener=build_opener(ProxyHandler({}),HTTPCookieProcessor(jar),*([NoRedirect()] if not challenge else []))
        status,body,auth_headers=self.http("GET",self.issuer+"/protocol/openid-connect/auth?"+urlencode(params),opener=opener)
        if not challenge:
            target=auth_headers.get("Location") or auth_headers.get("location") or ""
            error=parse_qs(urlparse(target).query).get("error")
            self.record("PKCE rejects authorization without S256 challenge",status==400 or (status==302 and error==["invalid_request"]),status); return
        parser=LoginForm(); parser.feed(body.decode())
        if status!=200 or not parser.action: raise RuntimeError("No Keycloak login form.")
        if urlparse(parser.action).netloc!=urlparse(self.kc).netloc: raise RuntimeError("Unexpected credential destination.")
        no_redirect=build_opener(ProxyHandler({}),HTTPCookieProcessor(jar),NoRedirect())
        status,login_body,headers=self.http("POST",html.unescape(parser.action),data={"username":user["username"],
            "password":user["password"],"credentialId":""},form=True,opener=no_redirect)
        location=headers.get("Location") or headers.get("location") or ""
        query=parse_qs(urlparse(location).query)
        if status!=302 or query.get("state")!=[state] or "code" not in query:
            title=re.search(r'<title>(.*?)</title>',login_body.decode(errors="replace"),re.S)
            raise RuntimeError("Authorization Code login failed; HTTP "+str(status)+"; route="+urlparse(location).path+
                "; fields="+str(sorted(query))+"; page="+(title.group(1).strip() if title else "none"))
        return {"client_id":client,"grant_type":"authorization_code","code":query["code"][0],
            "redirect_uri":self.front+"/auth/callback","code_verifier":verifier},nonce
    @staticmethod
    def payload(token):
        return json.loads(base64.urlsafe_b64decode(token.split(".")[1]+"==="))
    def login(self,user,client="illari-frontend"):
        exchange,nonce=self.authorization(user,client)
        tokens,_=self.expect("PKCE code exchange "+client,"POST",self.token_endpoint,200,data=exchange,form=True,fatal=True)
        claims=self.payload(tokens["access_token"]); identity=self.payload(tokens["id_token"])
        self.record("OIDC issuer/audience/nonce "+client,
            claims.get("iss")==self.issuer and claims.get("sub")==user["sub"] and "illari-api" in claims.get("aud",[])
            and identity.get("aud")==client and identity.get("nonce")==nonce)
        return tokens
    def setup(self):
        token_data,_=self.expect("Keycloak bootstrap admin login","POST",self.kc+"/realms/master/protocol/openid-connect/token",
            200,data={"client_id":"admin-cli","grant_type":"password","username":self.env.get("KEYCLOAK_ADMIN","admin"),
            "password":self.env["KEYCLOAK_ADMIN_PASSWORD"]},form=True,fatal=True)
        self.admin_token=token_data["access_token"]
        self.admin_refresh=token_data["refresh_token"]
        self.fresh_template()
        self.users={role:self.create_user(role) for role in ["ADMIN","ABOGADO","CLIENTE"]}
        self.users["OTRO_CLIENTE"]=self.create_user("CLIENTE")
        self.users["OTRO_ABOGADO"]=self.create_user("ABOGADO")
        self.tokens={role:self.login(user)["access_token"] for role,user in self.users.items()}
        self.authorization(self.users["CLIENTE"],challenge=False)
        wrong,_=self.authorization(self.users["CLIENTE"]); wrong["code_verifier"]=secrets.token_urlsafe(64)
        self.expect("PKCE rejects wrong verifier","POST",self.token_endpoint,400,data=wrong,form=True)
        exchange,_=self.authorization(self.users["CLIENTE"])
        self.expect("PKCE first exchange","POST",self.token_endpoint,200,data=exchange,form=True,fatal=True)
        self.expect("Authorization code cannot be reused","POST",self.token_endpoint,400,data=exchange,form=True)
        self.expect("Application rejects password grant","POST",self.token_endpoint,400,
            data={"client_id":"illari-frontend","grant_type":"password","username":self.users["CLIENTE"]["username"],
            "password":self.users["CLIENTE"]["password"]},form=True)
    def fresh_template(self):
        template=json.loads((Path(__file__).resolve().parents[2]/"infra/keycloak/realm-illari-legal.json").read_text(encoding="utf-8-sig"))
        template=json.loads(json.dumps(template).replace("${ILLARI_FRONTEND_URL}",self.front))
        template["realm"]="illari-template-"+self.run
        self.expect("Import current template as NEW realm","POST",self.kc+"/admin/realms",201,self.admin_token,template,fatal=True)
        previous=(self.realm,self.issuer,self.token_endpoint)
        try:
            self.realm=template["realm"];self.issuer=self.kc+"/realms/"+self.realm
            self.token_endpoint=self.issuer+"/protocol/openid-connect/token"
            user=self.create_user("CLIENTE")
            token=self.login(user)["access_token"]
            self.record("Fresh realm template emits stable subject and API audience",self.payload(token).get("sub")==user["sub"])
        finally:
            self.realm,self.issuer,self.token_endpoint=previous
    def security(self):
        admin=self.tokens["ADMIN"]; lawyer=self.tokens["ABOGADO"]; client=self.tokens["CLIENTE"]
        paths=[("cliente-ms",8081,"GET","/api/v1/clientes/me",None),
            ("servicio-legal-ms",8082,"POST","/api/v1/servicios",{}),
            ("orden-legal-ms",8083,"POST","/api/v1/ordenes",{}),
            ("pago-ms",8084,"GET","/api/v1/pagos/orden/999999",None),
            ("backend-legacy",8080,"GET","/api/casos",None)]
        for service,port,method,path,data in paths:
            for via,base in [("gateway",self.gateway),("direct",self.base(service,port))]:
                self.expect(service+" "+via+" no token",method,base+path,401,data=data)
                self.expect(service+" "+via+" invalid token",method,base+path,401,"invalid-token",data)
                parts=client.split("."); parts[2]=("A" if parts[2][0]!="A" else "B")+parts[2][1:]
                self.expect(service+" "+via+" tampered signature",method,base+path,401,".".join(parts),data)
        for via,base in [("gateway",self.gateway),("direct",self.base("servicio-legal-ms",8082))]:
            self.expect("catalog "+via+" public GET","GET",base+"/api/v1/servicios",200)
            self.expect("catalog "+via+" CLIENTE write forbidden","POST",base+"/api/v1/servicios",403,client,{})
            self.expect("catalog "+via+" ABOGADO write forbidden","POST",base+"/api/v1/servicios",403,lawyer,{})
            self.expect("catalog "+via+" ADMIN write","POST",base+"/api/v1/servicios",200,admin,
                {"nombre":"Phase3-"+self.run+"-"+via,"descripcion":"Test","precio":25,"categoria":"LEGAL","estado":"ACTIVO"},fatal=True)
        for via,base in [("gateway",self.gateway),("direct",self.base("cliente-ms",8081))]:
            self.expect("clientes "+via+" ADMIN list","GET",base+"/api/v1/clientes",200,admin)
            self.expect("clientes "+via+" ABOGADO list","GET",base+"/api/v1/clientes",200,lawyer)
            self.expect("clientes "+via+" CLIENTE list denied","GET",base+"/api/v1/clientes",403,client)
            self.expect("clientes "+via+" CLIENTE admin create denied","POST",base+"/api/v1/clientes",403,client,{})
        for via,base in [("gateway",self.gateway),("direct",self.base("backend-legacy",8080))]:
            self.expect("legal "+via+" valid but unlinked CLIENTE denied","GET",base+"/api/casos",403,client)
        for service,port,kind in [("orden-legal-ms",8083,"ordenes"),("pago-ms",8084,"pagos")]:
            for via,base in [("gateway",self.gateway),("direct",self.base(service,port))]:
                path="/api/v1/"+kind+"/mensajeria/fallidos"
                self.expect(kind+" "+via+" Kafka recovery ADMIN","GET",base+path,200,admin)
                self.expect(kind+" "+via+" Kafka recovery CLIENTE denied","GET",base+path,403,client)
                self.expect(kind+" "+via+" Kafka recovery ABOGADO denied","GET",base+path,403,lawyer)
                self.expect(kind+" "+via+" Kafka recovery anonymous denied","GET",base+path,401)
                self.expect(kind+" "+via+" Kafka replay ADMIN protected business lookup","POST",base+path+"/unknown/reprocesar",404,admin,{"motivo":"Prueba controlada"})
                self.expect(kind+" "+via+" Kafka replay CLIENTE denied","POST",base+path+"/unknown/reprocesar",403,client,{"motivo":"Prueba controlada"})
                self.expect(kind+" "+via+" Kafka replay ABOGADO denied","POST",base+path+"/unknown/reprocesar",403,lawyer,{"motivo":"Prueba controlada"})
        # A client with a short-lived token exercises real expiration, not a fake signature.
        template=self.admin("GET","clients?clientId=illari-frontend")[0]
        template.pop("id",None); template["clientId"]="phase3-expiry-"+self.run
        for mapper in template.get("protocolMappers",[]): mapper.pop("id",None)
        template.setdefault("attributes",{})["access.token.lifespan"]="1"
        self.admin("POST","clients",template,201)
        expired=self.login(self.users["CLIENTE"],template["clientId"])["access_token"]
        time.sleep(7)
        for service,port,method,path,data in paths:
            for via,base in [("gateway",self.gateway),("direct",self.base(service,port))]:
                self.expect(service+" "+via+" expired token",method,base+path,401,expired,data)
        # Correctly signed token from the same realm, but without the API audience.
        template.pop("id",None); template["clientId"]="phase3-noaud-"+self.run
        template["protocolMappers"]=[]; template["attributes"].pop("access.token.lifespan",None)
        self.admin("POST","clients",template,201)
        exchange,_=self.authorization(self.users["CLIENTE"],template["clientId"])
        tokens,_=self.expect("No-audience token exchange","POST",self.token_endpoint,200,data=exchange,form=True,fatal=True)
        for service,port,method,path,data in paths:
            for via,base in [("gateway",self.gateway),("direct",self.base(service,port))]:
                self.expect(service+" "+via+" JWT wrong audience rejected",method,base+path,401,tokens["access_token"],data)
                self.expect(service+" "+via+" JWT wrong issuer rejected",method,base+path,401,self.admin_token,data)
    def eureka(self):
        base=self.base("legal-eureka",8761)
        expected={"CLIENTE-MS","SERVICIO-LEGAL-MS","ORDEN-LEGAL-MS","PAGO-MS","CASO-MS","LEGAL-GATEWAY"}
        actual=set()
        for _ in range(45):
            status,body,_=self.http("GET",base+"/eureka/apps",headers={"Accept":"application/json"})
            if status==200:
                apps=json.loads(body).get("applications",{}).get("application",[])
                if isinstance(apps,dict): apps=[apps]
                actual={app["name"] for app in apps if any(i.get("status")=="UP" for i in
                    (app["instance"] if isinstance(app["instance"],list) else [app["instance"]]))}
                if expected<=actual: break
            time.sleep(2)
        self.record("Eureka six applications registered UP",expected<=actual,sorted(actual))
        self.expect("Config Server serves S12 profile","GET",self.base("legal-config",8888)+"/cliente-ms/s12",200,fatal=True)
        self.expect("Angular development server" if self.frontend_dev else "Frontend served by nginx","GET",self.front+"/",200,fatal=True)
        if not self.frontend_dev:
            runtime,_=self.expect("Frontend runtime Keycloak config","GET",self.front+"/assets/runtime-config.js",200,fatal=True)
            self.record("Frontend references public issuer at runtime",self.kc.encode() in runtime if isinstance(runtime,bytes) else False)
            self.expect("nginx forwards catalog through Gateway","GET",self.front+"/api/v1/servicios",200,fatal=True)
        _,headers=self.expect("CORS Idempotency-Key preflight","OPTIONS",self.gateway+"/api/v1/ordenes",200,headers={
            "Origin":self.front,"Access-Control-Request-Method":"POST","Access-Control-Request-Headers":"authorization,idempotency-key,content-type"})
        self.record("CORS permits only configured frontend",headers.get("Access-Control-Allow-Origin")==self.front)
    def database_isolation(self):
        owners=[("clientes","CLIENTES"),("servicios","SERVICIOS"),("ordenes","ORDENES"),("pagos","PAGOS"),("juridico","LEGACY")]
        for db,prefix in owners:
            command='PGPASSWORD="$'+prefix+'_DB_PASSWORD" psql -h 127.0.0.1 -U illari_'+db+' -d illari_'+db+' -At -c "SELECT 1"'
            result=self.cmd(["docker","exec",self.cid("postgres"),"sh","-c",command],ok=False)
            self.record("DB "+db+" own credentials",result.returncode==0 and result.stdout.strip()=="1")
            targets=[other for other,_ in owners if other!=db]
            denied=self.sql("postgres","SELECT bool_and(NOT has_database_privilege('illari_"+db+"',datname,'CONNECT')) FROM pg_database WHERE datname IN ("+
                ",".join("'illari_"+other+"'" for other in targets)+");")
            self.record("DB "+db+" cannot connect to other contexts",denied=="t")
        command='PGPASSWORD="$CLIENTES_DB_PASSWORD" psql -h 127.0.0.1 -U illari_clientes -d illari_ordenes -At -c "SELECT 1"'
        result=self.cmd(["docker","exec",self.cid("postgres"),"sh","-c",command],ok=False)
        self.record("PostgreSQL physically denies cross-database client connection",result.returncode!=0 and "permission denied" in result.stderr.lower())
    def commerce(self):
        admin=self.tokens["ADMIN"]; client=self.tokens["CLIENTE"]; other=self.tokens["OTRO_CLIENTE"]
        profiles={}
        for key in ["CLIENTE","OTRO_CLIENTE"]:
            profiles[key],_=self.expect("Client own profile create "+key,"POST",self.gateway+"/api/v1/clientes/me",201,self.tokens[key],
                {"nombres":"Phase3","apellidos":key,"email":"contact-"+key+"-"+self.run+"@example.test"},fatal=True)
        for via,base in [("gateway",self.gateway),("direct",self.base("cliente-ms",8081))]:
            profile,_=self.expect("clientes "+via+" CLIENTE own profile","GET",base+"/api/v1/clientes/me",200,client,fatal=True)
            self.record("clientes "+via+" own profile subject is stable",profile["keycloakSub"]==self.users["CLIENTE"]["sub"])
            self.expect("clientes "+via+" other client profile forbidden","GET",base+"/api/v1/clientes/"+str(profiles["OTRO_CLIENTE"]["id"]),403,client)
        services,_=self.expect("Real catalog for purchase","GET",self.gateway+"/api/v1/servicios",200,fatal=True)
        request={"metodoPago":"YAPE","detalles":[{"idServicio":services[0]["id"],"cantidad":2},{"idServicio":services[1]["id"],"cantidad":1}]}
        key="phase3-"+str(uuid.uuid4())
        order,_=self.expect("Purchase N through Gateway","POST",self.gateway+"/api/v1/ordenes",200,client,request,
            headers={"Idempotency-Key":key},fatal=True)
        repeated,_=self.expect("HTTP idempotency replay via Gateway","POST",self.gateway+"/api/v1/ordenes",200,client,request,headers={"Idempotency-Key":key},fatal=True)
        self.record("Repeated purchase has one order",repeated["id"]==order["id"])
        self.order=order
        for _ in range(45):
            status,body,_=self.http("GET",self.gateway+"/api/v1/ordenes/"+str(order["id"]),client)
            if status==200 and json.loads(body)["estado"]=="PAGADA": break
            time.sleep(1)
        self.record("Kafka real order converges PAGADA",status==200 and json.loads(body).get("estado")=="PAGADA")
        for via,base in [("gateway",self.gateway),("direct",self.base("orden-legal-ms",8083))]:
            path="/api/v1/ordenes/"+str(order["id"])
            self.expect("order "+via+" CLIENTE owner","GET",base+path,200,client)
            self.expect("order "+via+" ADMIN","GET",base+path,200,admin)
            self.expect("order "+via+" other CLIENTE hidden","GET",base+path,404,other)
            self.expect("order "+via+" ABOGADO purchase forbidden","POST",base+"/api/v1/ordenes",403,self.tokens["ABOGADO"],request,headers={"Idempotency-Key":"lawyer-"+key})
            self.expect("order "+via+" impersonation blocked","POST",base+"/api/v1/ordenes",403,client,
                dict(request,idCliente=self.users["OTRO_CLIENTE"]["sub"]),headers={"Idempotency-Key":"foreign-"+key})
            self.expect("order "+via+" other client list blocked","GET",base+"/api/v1/ordenes/cliente/"+self.users["CLIENTE"]["sub"],403,other)
        for via,base in [("gateway",self.gateway),("direct",self.base("pago-ms",8084))]:
            path="/api/v1/pagos/orden/"+str(order["id"])
            payments,_=self.expect("payment "+via+" CLIENTE owner","GET",base+path,200,client,fatal=True)
            self.record("payment "+via+" single validated payment",len(payments)==1 and payments[0]["estado"]=="VALIDADO")
            self.expect("payment "+via+" ADMIN","GET",base+path,200,admin)
            self.expect("payment "+via+" other CLIENTE hidden","GET",base+path,404,other)
        rows=self.sql("illari_ordenes","SELECT count(*)||','||sum(subtotal)::text FROM orden_detalle WHERE orden_id="+str(order["id"]))
        self.record("PostgreSQL persisted two historical detail rows",rows.split(",")[0]=="2",rows)
        if not self.frontend_dev:
            self.expect("Nginx forwards authenticated order","GET",self.front+"/api/v1/ordenes/"+str(order["id"]),200,client)
    def legal(self):
        admin=self.tokens["ADMIN"]
        email="legal-admin-"+self.run+"@example.test"
        self.sql("illari_juridico","INSERT INTO users(name,email,password,role_id) SELECT 'Phase3 Admin','"+email+
            "',md5(random()::text),id FROM roles WHERE name='admin';")
        uid=self.sql("illari_juridico","SELECT id FROM users WHERE email='"+email+"'")
        self.expect("ADMIN explicit legal binding","POST",self.gateway+"/api/identidades/vinculos",204,admin,
            {"userId":int(uid),"subject":self.users["ADMIN"]["sub"]},fatal=True)
        legal_ids={}
        for key in ["ABOGADO","OTRO_ABOGADO","CLIENTE","OTRO_CLIENTE"]:
            lawyer="ABOGADO" in key
            email="legal-"+key.lower()+"-"+self.run+"@example.test"
            request={"nombre":"Phase3 "+key,"email":email,"telefono":"900000001","direccion":"Prueba"}
            if lawyer: request["especialidad"]="Civil"
            self.expect("Legal create "+key,"POST",self.gateway+("/api/abogados" if lawyer else "/api/clientes"),200,admin,request,fatal=True)
            uid=self.sql("illari_juridico","SELECT id FROM users WHERE email='"+email+"'")
            self.expect("Legal stable binding "+key,"POST",self.gateway+"/api/identidades/vinculos",204,admin,
                {"userId":int(uid),"subject":self.users[key]["sub"]},fatal=True)
            legal_ids[key]=int(self.sql("illari_juridico","SELECT id FROM "+("abogados" if lawyer else "clientes")+" WHERE user_id="+uid))
        request={"nombreCaso":"Phase3 case "+self.run,"clienteId":legal_ids["CLIENTE"],"abogadoId":legal_ids["ABOGADO"],
            "estado":"Iniciado","fechaApertura":"2026-10-27","descripcion":"Solo pruebas","tarifa":100}
        case,_=self.expect("Legal create assigned case","POST",self.gateway+"/api/casos",200,admin,request,fatal=True)
        other_request=dict(request,clienteId=legal_ids["OTRO_CLIENTE"],abogadoId=legal_ids["OTRO_ABOGADO"])
        self.expect("Legal create other case","POST",self.gateway+"/api/casos",200,admin,other_request,fatal=True)
        for via,base in [("gateway",self.gateway),("direct",self.base("backend-legacy",8080))]:
            for role in ["ADMIN","ABOGADO","CLIENTE"]:
                self.expect("legal "+via+" own case "+role,"GET",base+"/api/casos/"+str(case["id"]),200,self.tokens[role])
            for role in ["OTRO_CLIENTE","OTRO_ABOGADO"]:
                self.expect("legal "+via+" foreign case "+role,"GET",base+"/api/casos/"+str(case["id"]),403,self.tokens[role])
            self.expect("legal "+via+" CLIENTE cannot bind","POST",base+"/api/identidades/vinculos",403,self.tokens["CLIENTE"],{"userId":1,"subject":"unknown"})
            self.expect("legal "+via+" CLIENTE cannot list others","GET",base+"/api/clientes",403,self.tokens["CLIENTE"])
            self.expect("legal "+via+" ABOGADO list","GET",base+"/api/clientes",200,self.tokens["ABOGADO"])
            self.expect("legal "+via+" agenda ADMIN","GET",base+"/api/agenda",200,admin)
            self.expect("legal "+via+" agenda CLIENTE forbidden","GET",base+"/api/agenda",403,self.tokens["CLIENTE"])
            self.expect("legal "+via+" classic auth endpoint denied","POST",base+"/api/auth/login",403,admin,{"email":"unused","password":"unused"})
            self.expect("legal "+via+" other client messages denied","GET",base+"/api/casos/"+str(case["id"])+"/mensajes",403,self.tokens["OTRO_CLIENTE"])
            self.expect("legal "+via+" other client documents denied","GET",base+"/api/documentos/caso/"+str(case["id"]),403,self.tokens["OTRO_CLIENTE"])
        self.expect("Legal messages write by owner","POST",self.gateway+"/api/casos/"+str(case["id"])+"/mensajes",200,self.tokens["CLIENTE"],{"contenido":"Prueba Fase 3"},fatal=True)
        self.expect("Legal messages read by assigned lawyer","GET",self.gateway+"/api/casos/"+str(case["id"])+"/mensajes",200,self.tokens["ABOGADO"])
        for via,base in [("gateway",self.gateway),("direct",self.base("backend-legacy",8080))]:
            dashboard,_=self.expect("legal "+via+" CLIENTE dashboard scope","GET",base+"/api/dashboard",200,self.tokens["CLIENTE"],fatal=True)
            self.record("legal "+via+" dashboard hides other clients",dashboard["totalCasos"]==1 and dashboard["totalClientes"]==1)
        boundary="illari-"+uuid.uuid4().hex
        multipart=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"nombre\"\r\n\r\nPhase3 documento\r\n"+
            "--"+boundary+"\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"prueba.txt\"\r\nContent-Type: text/plain\r\n\r\nPrueba Fase 3\r\n--"+boundary+"--\r\n").encode()
        document,_=self.expect("Legal document upload by assigned lawyer","POST",self.gateway+"/api/documentos/caso/"+str(case["id"]),
            200,self.tokens["ABOGADO"],headers={"Content-Type":"multipart/form-data; boundary="+boundary},raw_body=multipart,fatal=True)
        self.document=document
        self.expect("Legal owner document download","GET",self.gateway+"/api/documentos/"+str(document["id"])+"/download",200,self.tokens["CLIENTE"])
        self.expect("Legal foreign client document download denied","GET",self.gateway+"/api/documentos/"+str(document["id"])+"/download",403,self.tokens["OTRO_CLIENTE"])
        self.admin("PUT","users/"+self.users["CLIENTE"]["sub"],{"email":"changed-contact-"+self.run+"@example.test"},204)
        fresh=self.login(self.users["CLIENTE"])["access_token"]
        self.expect("Legal identity survives Keycloak email change","GET",self.gateway+"/api/casos/"+str(case["id"]),200,fresh)
        self.expect("Existing binding cannot be reassigned","POST",self.gateway+"/api/identidades/vinculos",409,admin,
            {"userId":int(uid),"subject":str(uuid.uuid4())})
        self.legal_case=case
    def persistence(self):
        before=self.sql("illari_juridico","SELECT md5(string_agg(id::text||password,',' ORDER BY id)) FROM users")
        self.cmd(self.compose+["restart","keycloak","cliente-ms","backend-legacy"])
        ready=False
        urls=[self.kc+"/realms/illari-legal/.well-known/openid-configuration",self.base("cliente-ms",8081)+"/actuator/health/readiness",
              self.base("backend-legacy",8080)+"/actuator/health/readiness"]
        for _ in range(90):
            try: ready=all(self.http("GET",url)[0]==200 for url in urls)
            except Exception: ready=False
            if ready:break
            time.sleep(1)
        self.record("Keycloak/Cliente/juridico recover after own-container restart",ready)
        after=self.sql("illari_juridico","SELECT md5(string_agg(id::text||password,',' ORDER BY id)) FROM users")
        self.record("Legal credentials unchanged after restart",before==after)
        token=self.login(self.users["CLIENTE"])["access_token"]
        self.expect("Keycloak credentials survive realm re-import on restart","GET",self.base("backend-legacy",8080)+"/api/casos/"+str(self.legal_case["id"]),200,token)
        self.expect("Cliente profile survives restart in PostgreSQL","GET",self.base("cliente-ms",8081)+"/api/v1/clientes/me",200,token)
        self.expect("Uploaded legal document survives backend restart","GET",self.base("backend-legacy",8080)+"/api/documentos/"+str(self.document["id"])+"/download",200,token)
    def run_all(self):
        self.eureka(); self.database_isolation(); self.setup(); self.security(); self.commerce(); self.legal(); self.persistence()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--project",required=True); parser.add_argument("--env-file",required=True);parser.add_argument("--output",required=True)
    parser.add_argument("--frontend-dev",action="store_true",help="Explicit ng serve mode; nginx integration is NOT asserted")
    args=parser.parse_args();suite=None
    try:
        suite=Suite(args);suite.run_all()
    except Exception as error:
        if suite: suite.record("Execution completed",False,type(error).__name__+": "+str(error))
        else: print("Setup failed: "+type(error).__name__,flush=True)
        raise SystemExit(1)
    raise SystemExit(1 if any(not r["passed"] for r in suite.results) else 0)
if __name__=="__main__": main()

