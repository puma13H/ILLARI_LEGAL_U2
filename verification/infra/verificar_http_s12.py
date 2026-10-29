"""HTTP S12 without Docker access. Default read-only; optional NEW OIDC fixture users.
Never writes business orders/payments, resets passwords, changes existing roles or logs tokens.
This does NOT replace direct-service security, database, Kafka or fault verification.
"""
import argparse
import json
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse
from urllib.parse import urlencode
from urllib.request import Request, build_opener, ProxyHandler
from urllib.error import HTTPError
from verificar_s12 import Suite


class HttpSuite(Suite):
    def __init__(self, args):
        # Deliberately independent from the Docker/write-business suite constructor:
        # only local HTTP probes and explicitly requested NEW OIDC fixtures are allowed.
        self.args = args
        self.results = []
        self.run = uuid.uuid4().hex[:10]
        self.env = {}
        if args.env_file:
            for line in Path(args.env_file).read_text(encoding='utf-8-sig').splitlines():
                if '=' in line and not line.lstrip().startswith('#'):
                    key, value = line.split('=', 1)
                    if key in ('KEYCLOAK_ADMIN', 'KEYCLOAK_ADMIN_PASSWORD'):
                        self.env[key] = value
        self.gateway, self.kc, self.front = [v.rstrip('/') for v in (args.gateway, args.keycloak, args.frontend)]
        for base in (self.gateway, self.kc, self.front):
            parsed = urlparse(base)
            if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.username or parsed.password or parsed.path:
                raise ValueError('Only explicit local HTTP origins are allowed')
        self.realm = 'illari-legal'
        self.issuer = self.kc + '/realms/' + self.realm
        self.token_endpoint = self.issuer + '/protocol/openid-connect/token'
        self.services = json.loads(Path(args.services_json).read_text(encoding='utf-8-sig')) if args.services_json else {}
        for origin in self.services.values():
            parsed = urlparse(origin)
            if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.path:
                raise ValueError('Direct service origins must be local')

    def http(self, method, url, token=None, data=None, form=False, opener=None, headers=None, raw_body=None):
        # ng serve may listen on IPv6 localhost while Compose binds IPv4.
        # Preserve localhost and let the OS try both; do not force 127.0.0.1.
        if urlparse(url).hostname not in ('localhost', '127.0.0.1'):
            raise ValueError('Non-local HTTP destination refused')
        h = dict(headers or {})
        if token:
            h['Authorization'] = 'Bearer ' + token
        body = raw_body
        if data is not None:
            h['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
            body = (urlencode(data) if form else json.dumps(data)).encode()
        request = Request(url, data=body, headers=h, method=method)
        try:
            with (opener or build_opener(ProxyHandler({}))).open(request, timeout=15) as response:
                return response.status, response.read(), dict(response.headers)
        except HTTPError as error:
            return error.code, error.read(), dict(error.headers)

    def save(self):
        Path(self.args.output).parent.mkdir(parents=True, exist_ok=True)
        Path(self.args.output).write_text(json.dumps({
            'scope': 'live-http; direct services only when explicitly supplied; no Docker/database/Kafka assertion',
            'direct_service_origins': self.services,
            'new_oidc_fixtures_requested': self.args.crear_usuarios_prueba,
            'run': self.run, 'passed': sum(r['passed'] for r in self.results),
            'failed': sum(not r['passed'] for r in self.results), 'results': self.results,
        }, indent=2, ensure_ascii=False), encoding='utf-8')

    def public(self):
        health, _ = self.expect('Live Gateway health', 'GET', self.gateway + '/actuator/health', 200)
        self.record('Live Gateway reports UP', isinstance(health, dict) and health.get('status') == 'UP')
        catalogue, headers = self.expect('Live catalog through Gateway', 'GET', self.gateway + '/api/v1/servicios', 200)
        self.record('Live catalog has persisted API identifiers', isinstance(catalogue, list) and len(catalogue) > 0
                    and all(isinstance(p.get('id'), int) and p['id'] > 0 for p in catalogue),
                    {'count': len(catalogue)} if isinstance(catalogue, list) else None)
        self.record('Gateway propagates a trace identifier', bool({k.lower(): v for k, v in headers.items()}.get('x-trace-id')))
        discovery, _ = self.expect('Live Keycloak discovery', 'GET', self.issuer + '/.well-known/openid-configuration', 200)
        self.record('Discovery issuer matches frontend configuration', isinstance(discovery, dict) and discovery.get('issuer') == self.issuer)
        self.expect('Live Keycloak public JWKS', 'GET', self.issuer + '/protocol/openid-connect/certs', 200)
        for path in ('/', '/login', '/tienda-juridica'):
            self.expect('Angular serves ' + path, 'GET', self.front + path, 200)
        for path in ('/api/v1/clientes/me', '/api/v1/ordenes/1', '/api/v1/pagos/orden/1', '/api/casos'):
            self.expect('Gateway rejects anonymous ' + path, 'GET', self.gateway + path, 401)
            self.expect('Gateway rejects invalid JWT ' + path, 'GET', self.gateway + path, 401, 'invalid-token')
        for service, origin in self.services.items():
            health, _ = self.expect('Direct health ' + service, 'GET', origin + '/actuator/health', 200)
            self.record('Direct UP ' + service, isinstance(health, dict) and health.get('status') == 'UP')
        if 'legal-eureka' in self.services:
            apps, _ = self.expect('Live Eureka registry', 'GET', self.services['legal-eureka'] + '/eureka/apps', 200,
                                   headers={'Accept': 'application/json'})
            entries = apps.get('applications', {}).get('application', [])
            if isinstance(entries, dict):
                entries = [entries]
            up = {app['name'] for app in entries if any(i.get('status') == 'UP' for i in
                  (app['instance'] if isinstance(app['instance'], list) else [app['instance']]))}
            self.record('Six applications registered UP in Eureka',
                {'CLIENTE-MS', 'SERVICIO-LEGAL-MS', 'ORDEN-LEGAL-MS', 'PAGO-MS', 'CASO-MS', 'LEGAL-GATEWAY'} <= up, sorted(up))
        if 'legal-config' in self.services:
            self.expect('Live Config Server serves S12', 'GET', self.services['legal-config'] + '/cliente-ms/s12', 200)

    def protected_paths(self):
        paths = {'cliente-ms': ('GET', '/api/v1/clientes/me'),
                 'servicio-legal-ms': ('POST', '/api/v1/servicios'),
                 'orden-legal-ms': ('GET', '/api/v1/ordenes/mensajeria/fallidos'),
                 'pago-ms': ('GET', '/api/v1/pagos/mensajeria/fallidos'),
                 'backend-legacy': ('GET', '/api/casos')}
        return [(name, method, self.services[name] + path) for name, (method, path) in paths.items() if name in self.services]

    def roles(self):
        if not self.env.get('KEYCLOAK_ADMIN_PASSWORD'):
            raise ValueError('Private KEYCLOAK_ADMIN_PASSWORD required for NEW fixture users')
        tokens, _ = self.expect('Keycloak bootstrap administration authenticates', 'POST',
            self.kc + '/realms/master/protocol/openid-connect/token', 200, data={
                'client_id': 'admin-cli', 'grant_type': 'password',
                'username': self.env.get('KEYCLOAK_ADMIN', 'admin'),
                'password': self.env['KEYCLOAK_ADMIN_PASSWORD']}, form=True, fatal=True)
        self.admin_token, self.admin_refresh = tokens['access_token'], tokens['refresh_token']
        for service, method, url in self.protected_paths():
            self.expect(service + ' direct anonymous rejected', method, url, 401, data={} if method == 'POST' else None)
            self.expect(service + ' direct invalid JWT rejected', method, url, 401, 'invalid-token', {} if method == 'POST' else None)
        for role in ('ADMIN', 'ABOGADO', 'CLIENTE'):
            user = self.create_user(role)
            tokens = self.login(user)
            token = tokens['access_token']
            claims = self.payload(token)
            self.record('Real Keycloak token has role ' + role, role in claims.get('realm_access', {}).get('roles', []))
            self.expect(role + ' client listing', 'GET', self.gateway + '/api/v1/clientes', 403 if role == 'CLIENTE' else 200, token)
            for kind in ('ordenes', 'pagos'):
                self.expect(role + ' ' + kind + ' recovery authorization', 'GET',
                    self.gateway + '/api/v1/' + kind + '/mensajeria/fallidos', 200 if role == 'ADMIN' else 403, token)
            # Empty catalogue POST is intentionally invalid even for ADMIN: no business row is created.
            self.expect(role + ' catalog mutation boundary without business write', 'POST',
                self.gateway + '/api/v1/servicios', 400 if role == 'ADMIN' else 403, token, {})
            parts = token.split('.')
            parts[2] = ('A' if parts[2][0] != 'A' else 'B') + parts[2][1:]
            self.expect(role + ' forged signature rejected', 'GET', self.gateway + '/api/v1/clientes/me', 401, '.'.join(parts))
            for service, method, url in self.protected_paths():
                self.expect(service + ' direct forged JWT ' + role, method, url, 401, '.'.join(parts), {} if method == 'POST' else None)
            if 'cliente-ms' in self.services:
                self.expect('Direct client listing ' + role, 'GET', self.services['cliente-ms'] + '/api/v1/clientes',
                            403 if role == 'CLIENTE' else 200, token)
            if 'servicio-legal-ms' in self.services:
                self.expect('Direct catalog validation/authorization ' + role, 'POST',
                    self.services['servicio-legal-ms'] + '/api/v1/servicios', 400 if role == 'ADMIN' else 403, token, {})
            for service, kind in (('orden-legal-ms', 'ordenes'), ('pago-ms', 'pagos')):
                if service in self.services:
                    self.expect('Direct recovery ' + service + ' ' + role, 'GET', self.services[service]
                        + '/api/v1/' + kind + '/mensajeria/fallidos', 200 if role == 'ADMIN' else 403, token)
            if 'backend-legacy' in self.services:
                self.expect('Direct legal unlinked identity denied ' + role, 'GET',
                    self.services['backend-legacy'] + '/api/casos', 403, token)
            self.expect(role + ' app password grant disabled', 'POST', self.token_endpoint, 400, data={
                'client_id': 'illari-frontend', 'grant_type': 'password',
                'username': user['username'], 'password': user['password']}, form=True)
            # No role or password of any existing account is modified.
        self.authorization(user, challenge=False)
        self.signed_claim_boundaries(user)

    def signed_claim_boundaries(self, user):
        import copy
        template = self.admin('GET', 'clients?clientId=illari-frontend')[0]
        for scenario in ('expired', 'no-audience'):
            client = copy.deepcopy(template)
            client.pop('id', None)
            client['clientId'] = 's12-http-' + scenario + '-' + self.run
            for mapper in client.get('protocolMappers', []):
                mapper.pop('id', None)
            if scenario == 'expired':
                client.setdefault('attributes', {})['access.token.lifespan'] = '1'
            else:
                client['protocolMappers'] = []
            self.admin('POST', 'clients', client, 201)
            exchange, _ = self.authorization(user, client['clientId'])
            tokens, _ = self.expect('Signed ' + scenario + ' fixture token exchange', 'POST',
                self.token_endpoint, 200, data=exchange, form=True, fatal=True)
            if scenario == 'expired':
                time.sleep(7)
            else:
                self.record('No-audience fixture really lacks illari-api',
                            'illari-api' not in self.payload(tokens['access_token']).get('aud', []))
            targets = [('Gateway', 'GET', self.gateway + '/api/v1/clientes/me')] + self.protected_paths()
            for service, method, url in targets:
                self.expect(service + ' signed ' + scenario + ' token rejected', method, url, 401,
                            tokens['access_token'], {} if method == 'POST' else None)
                if scenario == 'no-audience':
                    self.expect(service + ' other realm token rejected', method, url, 401,
                                self.admin_token, {} if method == 'POST' else None)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--gateway', default='http://localhost:18080')
    p.add_argument('--keycloak', default='http://localhost:18180')
    p.add_argument('--frontend', default='http://localhost:4200')
    p.add_argument('--env-file')
    p.add_argument('--services-json', help='Private-free JSON mapping service names to explicitly verified local origins')
    p.add_argument('--output', required=True)
    p.add_argument('--crear-usuarios-prueba', action='store_true')
    suite = None
    try:
        suite = HttpSuite(p.parse_args())
        suite.public()
        if suite.args.crear_usuarios_prueba:
            suite.roles()
    except Exception as error:
        if suite:
            # No raw HTTP response, token or credentials are included in exception output.
            suite.record('HTTP audit completed', False, type(error).__name__ + ': ' + str(error))
        else:
            print('HTTP setup refused', flush=True)
        raise SystemExit(1)
    raise SystemExit(1 if any(not r['passed'] for r in suite.results) else 0)


if __name__ == '__main__':
    main()
