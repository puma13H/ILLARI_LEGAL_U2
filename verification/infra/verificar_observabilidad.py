"""Fase 4: only labelled existing audit stack; fault injection always restores owned containers.
No tokens, passwords, payloads or personal/legal fields are exported.
"""
import argparse, base64, json, time, uuid, sys
from pathlib import Path
from urllib.parse import urlencode
from verificar_s12 import Suite

class ObservabilitySuite(Suite):
    def __init__(self,args):
        super().__init__(args)
        self.compose += ['-f','docker-compose.yml','-f','obs/compose.s12.yml','--profile','ui','--profile','obs']
        self.grafana=self.base('grafana',3000)
        self.basic='Basic '+base64.b64encode((self.env.get('GRAFANA_ADMIN_USER','admin')+':'+self.env['GRAFANA_ADMIN_PASSWORD']).encode()).decode()
        self.evidence=Path(args.output).parent
        self.traces={}
        for svc in ['legal-gateway','cliente-ms','servicio-legal-ms','orden-legal-ms','pago-ms','backend-legacy','kafka']:
            labels=json.loads(self.cmd(['docker','inspect','--format','{{json .Config.Labels}}',self.cid(svc)]))
            if labels.get('com.docker.compose.project')!=args.project:raise RuntimeError('Foreign service refused')
    def graf(self,path):
        status,body,_=self.http('GET',self.grafana+path,headers={'Authorization':self.basic})
        if status!=200: raise RuntimeError('Grafana API HTTP '+str(status))
        return json.loads(body)
    def prom(self,expression):
        data=self.graf('/api/datasources/proxy/uid/prometheus/api/v1/query?'+urlencode({'query':expression}))
        if data.get('status')!='success':raise RuntimeError('PromQL failed')
        return data['data']['result']
    def values(self,expression):return [float(x['value'][1]) for x in self.prom(expression)]
    def loki(self,query):
        return self.graf('/api/datasources/proxy/uid/loki/loki/api/v1/query_range?'+urlencode({'query':query,'start':str(int((time.time()-3600)*1e9)),'end':str(int(time.time()*1e9)),'limit':1000,'direction':'forward'}))['data']['result']
    def wait(self,predicate,timeout=60):
        end=time.time()+timeout
        while time.time()<end:
            try:
                value=predicate()
                if value:return value
            except Exception:pass
            time.sleep(1)
        return None
    def tokens_fresh(self):
        if self.payload(self.tokens['CLIENTE'])['exp']<time.time()+90:
            self.tokens={role:self.login(user)['access_token'] for role,user in self.users.items()}
    def purchase(self,method='YAPE',expected=200,trace=None):
        self.tokens_fresh()
        trace=trace or str(uuid.uuid4())
        request={'metodoPago':method,'detalles':[{'idServicio':self.services[0]['id'],'cantidad':2},{'idServicio':self.services[1]['id'],'cantidad':1}]}
        key='fase4-'+str(uuid.uuid4())
        order,headers=self.expect('Purchase '+method+' HTTP '+str(expected),'POST',self.gateway+'/api/v1/ordenes',expected,self.tokens['CLIENTE'],request,
            headers={'Idempotency-Key':key,'X-Trace-ID':trace},fatal=True)
        self.record('Gateway returns supplied technical correlation',headers.get('X-Trace-ID',headers.get('X-trace-id'))==trace)
        return order,trace
    def state(self,order,expected):
        def poll():
            status,body,_=self.http('GET',self.gateway+'/api/v1/ordenes/'+str(order['id']),self.tokens['CLIENTE'])
            data=json.loads(body) if status==200 else {}
            return data if data.get('estado')==expected else None
        result=self.wait(poll,65)
        self.record('Order converges '+expected,bool(result),order['id']);return result
    def infra(self):
        self.expect('Grafana dashboard requires authentication','GET',self.grafana+'/api/dashboards/uid/illari-s12-overview',401)
        d=self.graf('/api/dashboards/uid/illari-s12-overview');self.dashboard=d['dashboard']
        self.record('Provisioned Grafana dashboard',len(self.dashboard['panels'])==22)
        for svc in ['prometheus','loki','alloy','blackbox']:
            ports=json.loads(self.cmd(['docker','inspect','--format','{{json .HostConfig.PortBindings}}',self.cid(svc)]))
            self.record(svc+' has no published port',not ports)
        targets=self.graf('/api/datasources/proxy/uid/prometheus/api/v1/targets')['data']['activeTargets']
        self.record('Six metrics and six real readiness probes',len(targets)==12 and all(t['health']=='up' for t in targets),
            [{'job':t['labels']['job'],'target':t['discoveredLabels']['__address__'],'health':t['health']} for t in targets])
        self.record('Readiness endpoints return UP',self.values('sum(probe_success{job="illari-readiness"})')==[6.0])
        status,_,headers=self.http('OPTIONS',self.gateway+'/api/v1/ordenes',headers={'Origin':self.front,'Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,content-type,idempotency-key,x-trace-id'})
        h={k.lower():v for k,v in headers.items()}
        self.record('Browser preflight allows correlation and purchase key',status==200 and all(k in h.get('access-control-allow-headers','').lower() for k in ['x-trace-id','idempotency-key']))
        status,_,headers=self.http('GET',self.gateway+'/api/v1/servicios',headers={'Origin':self.front})
        h={k.lower():v for k,v in headers.items()}
        self.record('Browser can read returned X-Trace-ID',status==200 and 'x-trace-id' in h.get('access-control-expose-headers','').lower() and bool(h.get('x-trace-id')))
        status,_,_=self.http('OPTIONS',self.gateway+'/api/v1/ordenes',headers={'Origin':'https://external.example.test','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'x-trace-id'})
        self.record('Foreign browser origin remains rejected',status==403)
        self.services,_=self.expect('Read real catalogue','GET',self.gateway+'/api/v1/servicios',200,fatal=True)
        if len(self.services)<2:raise RuntimeError('Need existing two-service audit catalogue')
    def purchases(self):
        approved,trace=self.purchase();self.state(approved,'PAGADA');self.approved=approved;self.traces['approved']=trace
        rejected,rejected_trace=self.purchase('TARJETA_RECHAZADA');self.state(rejected,'CANCELADA');self.traces['rejected']=rejected_trace
        self.record('Rejected purchase has FALLIDO payment',self.sql('illari_pagos',"SELECT estado FROM pagos_legales WHERE id_orden="+str(rejected['id']))=='FALLIDO')
        self.tokens_fresh()
        self.expect('Cancel approved order for simulated refund','PATCH',self.gateway+'/api/v1/ordenes/'+str(approved['id'])+'/estado',200,self.tokens['ADMIN'],{'estado':'CANCELADA'},headers={'X-Trace-ID':trace},fatal=True)
        self.state(approved,'CANCELADA')
        self.record('Simulated refund persisted',self.sql('illari_pagos','SELECT estado FROM pagos_legales WHERE id_orden='+str(approved['id']))=='REEMBOLSADO')
        # Leave a paid order too: every dashboard state has a real source.
        paid,paid_trace=self.purchase();self.state(paid,'PAGADA');self.paid=paid;self.traces['paid']=paid_trace
        self.record('Cart N persisted two relational details',self.sql('illari_ordenes','SELECT count(*) FROM orden_detalle WHERE orden_id='+str(paid['id']))=='2')
        self.record('No cancelled order has a validated payment',all(self.sql('illari_pagos','SELECT count(*) FROM pagos_legales WHERE id_orden='+str(o['id'])+" AND estado='VALIDADO'")=='0' for o in [approved,rejected]))
    def trace_evidence(self):
        trace=self.traces['paid']
        def obtain():
            result=self.loki('{job="illari-observations"} | json | traceId="'+trace+'"')
            services={s['stream'].get('service') for s in result}
            return result if {'legal-gateway','orden-legal-ms','servicio-legal-ms','pago-ms'}.issubset(services) else None
        result=self.wait(obtain,45)
        self.record('Purchase trace found in Gateway Orders Catalogue Payments',bool(result),trace)
        if result:
            by_event={};events=[]
            for stream in result:
                for timestamp,line in stream['values']:
                    data=json.loads(line);service=stream['stream']['service'];event=data.get('eventId')
                    if event:by_event.setdefault(event,set()).add(service)
                    events.append(dict(service=service,**data))
            self.record('Same Kafka event ID in publisher and consumer',any(len(v)>=2 for v in by_event.values()),{k:sorted(v) for k,v in by_event.items()})
            self.record('Technical trace differs from order ID',trace!=str(self.paid['id']))
            (self.evidence/'trace-purchase.json').write_text(json.dumps({'traceId':trace,'orderId':self.paid['id'],'events':events},indent=2),encoding='utf-8')
    def dlt(self):
        # Poison NEW event, referring to an already paid test order. Replay must not charge twice.
        raw=self.sql('illari_ordenes','SELECT payload FROM orden_outbox WHERE orden_id='+str(self.paid['id'])+' ORDER BY id LIMIT 1')
        good=json.loads(raw);good['eventId']=str(uuid.uuid4());good['traceId']=str(uuid.uuid4())
        bad=dict(good,metodoPago='UNKNOWN')
        self.cmd(['docker','exec','-i',self.cid('kafka'),'/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9092','--topic','illari.orden.creada'],json.dumps(bad)+'\n')
        def lookup():
            self.tokens_fresh()
            status,body,_=self.http('GET',self.gateway+'/api/v1/pagos/mensajeria/fallidos',self.tokens['ADMIN'])
            rows=json.loads(body) if status==200 else []
            for row in rows:
                detail_status,detail_body,_=self.http('GET',self.gateway+'/api/v1/pagos/mensajeria/fallidos/'+row['id'],self.tokens['ADMIN'])
                if detail_status==200 and good['eventId'] in json.loads(detail_body).get('payloadOriginal',''):return row
        row=self.wait(lookup,45);self.record('Failed message persisted from real DLT',bool(row))
        if not row:return
        self.record('Prometheus DLT gauge detects real backlog',bool(self.wait(lambda:any(v>0 for v in self.values('illari_kafka_dlt_pendientes{job="pago-ms"}')),20)))
        alert=self.wait(lambda:self.prom('ALERTS{alertname="IllariDltPendiente",alertstate="firing",job="pago-ms"}'),25)
        self.record('DLT alert fires on real data',bool(alert));self.snapshot('dlt')
        self.expect('ADMIN controlled DLT replay','POST',self.gateway+'/api/v1/pagos/mensajeria/fallidos/'+row['id']+'/reprocesar',202,self.tokens['ADMIN'],{'payloadCorregido':json.dumps(good),'motivo':'Diagnostico Fase4 - metodo de fixture corregido'},fatal=True)
        replayed=self.wait(lambda:self.sql('illari_pagos',"SELECT count(*) FROM pago_inbox WHERE event_id='"+good['eventId']+"'")=='1',30)
        self.record('Replayed event processed and inbox confirmed',bool(replayed))
        self.record('Replay does not duplicate payment',self.sql('illari_pagos','SELECT count(*) FROM pagos_legales WHERE id_orden='+str(self.paid['id']))=='1')
        self.record('Replay counter increments',bool(self.wait(lambda:any(v>0 for v in self.values('illari_kafka_reprocesados_total{job="pago-ms"}')),20)))
    def snapshot(self,name):
        metrics={q:self.prom(q) for q in ['up','probe_success','resilience4j_circuitbreaker_state','resilience4j_retry_calls_total','illari_outbox_pendientes','illari_outbox_fallidos','illari_outbox_edad_segundos','illari_kafka_dlt_pendientes','ALERTS']}
        (self.evidence/('metrics-'+name+'.json')).write_text(json.dumps(metrics,indent=2),encoding='utf-8')
    def catalogue_failure(self):
        service=self.cid('servicio-legal-ms')
        self.cmd(['docker','stop',service])
        try:
            for _ in range(4):self.purchase(expected=503)
            self.record('Prometheus detects unavailable catalogue',bool(self.wait(lambda:self.values('up{job="servicio-legal-ms"}')==[0.0],20)))
            self.record('Readiness probe detects catalogue failure',bool(self.wait(lambda:any(v==0 for v in self.values('probe_success{instance=~".*servicio-legal-ms.*"}')),20)))
            # Fill a real HTTP error window, then stop business calls: otherwise new requests
            # repeatedly enter HALF_OPEN and reset the alert's pending period around scrape edges.
            for _ in range(10): self.purchase(expected=503)
            self.record('Circuit breaker OPEN metric',bool(self.wait(lambda:any(v==1 for v in self.values('resilience4j_circuitbreaker_state{state="open"}')),20)))
            self.record('Service unavailable alert fires',bool(self.wait(lambda:self.prom('ALERTS{alertname="IllariServicioNoDisponible",job="servicio-legal-ms",alertstate="firing"}'),30)))
            self.record('Circuit breaker alert fires',bool(self.wait(lambda:self.prom('ALERTS{alertname="IllariCircuitoAbierto",alertstate="firing"}'),25)))
            self.record('HTTP error-rate alert fires',bool(self.wait(lambda:self.prom('ALERTS{alertname="IllariErroresHttp",alertstate="firing"}'),25)))
            self.record('Retry records failed calls',any(v>0 for v in self.values('resilience4j_retry_calls_total{kind=~"failed.*"}')))
            self.snapshot('catalogue-failure')
        finally:self.cmd(['docker','start',service])
        self.record('Catalogue readiness recovers',bool(self.wait(lambda:self.http('GET',self.base('servicio-legal-ms',8082)+'/actuator/health/readiness')[0]==200,100)))
        # A half-open breaker needs its permitted successful calls, not a manual forced reset.
        closed=False
        recovery_orders=[]
        recovery_deadline=time.time()+150
        while time.time()<recovery_deadline:
            self.tokens_fresh();status,body,_=self.http('POST',self.gateway+'/api/v1/ordenes',self.tokens['CLIENTE'],{'metodoPago':'YAPE','detalles':[{'idServicio':self.services[0]['id'],'cantidad':1}]},headers={'Idempotency-Key':'recovery-'+str(uuid.uuid4()),'X-Trace-ID':str(uuid.uuid4())})
            if status==200: recovery_orders.append(json.loads(body))
            if status==200 and any(v==1 for v in self.values('resilience4j_circuitbreaker_state{state="closed"}')):closed=True;break
            time.sleep(3)
        self.record('Circuit breaker closes after real successful calls',closed)
        self.record('Prometheus target recovers',bool(self.wait(lambda:self.values('up{job="servicio-legal-ms"}')==[1.0],20)))
        # Do not carry an uncommitted transport backlog into the next, independent broker fault.
        for order in recovery_orders:
            if not self.state(order,'PAGADA'):raise RuntimeError('Catalogue-recovery purchase did not settle before Kafka fault')
    def outbox_failure(self):
        kafka=self.cid('kafka');self.cmd(['docker','stop',kafka])
        try:
            order,trace=self.purchase();self.outbox_order=order
            self.record('Outbox remains durable while broker is down',self.sql('illari_ordenes','SELECT count(*) FROM orden_outbox WHERE orden_id='+str(order['id'])+' AND publicado=false')=='1')
            self.record('Outbox pending gauge detects queued event',bool(self.wait(lambda:any(v>0 for v in self.values('illari_outbox_pendientes{job="orden-legal-ms"}')),15)))
            self.snapshot('outbox-queued')
            self.record('Outbox retry counter grows',bool(self.wait(lambda:any(v>0 for v in self.values('illari_outbox_reintentados_total{job="orden-legal-ms"}')),20)))
            age_alert=self.wait(lambda:self.prom('ALERTS{alertname="IllariOutboxAntiguo",alertstate="firing"}'),20)
            self.record('Old pending outbox alert fires',bool(age_alert));self.snapshot('outbox-pending')
            exhausted=self.wait(lambda:self.sql('illari_ordenes','SELECT fallido FROM orden_outbox WHERE orden_id='+str(order['id']))=='t',65)
            self.record('Outbox bounded retries exhaust durably',bool(exhausted))
            self.record('Exhausted event retains age until publication',bool(self.wait(lambda:any(v>0 for v in self.values('illari_outbox_fallidos{job="orden-legal-ms"}')) and any(v>5 for v in self.values('illari_outbox_edad_segundos{job="orden-legal-ms"}')),20)))
            self.record('Exhausted outbox alert fires',bool(self.wait(lambda:self.prom('ALERTS{alertname="IllariOutboxAgotado",alertstate="firing"}'),25)))
            self.snapshot('outbox-exhausted')
        finally:self.cmd(['docker','start',kafka])
        def kafka_ready():
            state=self.cmd(['docker','inspect','--format','{{.State.Health.Status}}',kafka]);return state=='healthy'
        self.record('Kafka becomes healthy after restart',bool(self.wait(kafka_ready,100)))
        self.tokens_fresh();event_id=self.sql('illari_ordenes','SELECT id FROM orden_outbox WHERE orden_id='+str(order['id']))
        self.expect('ADMIN retries exhausted outbox','POST',self.gateway+'/api/v1/ordenes/mensajeria/outbox/'+event_id+'/reintentar',202,self.tokens['ADMIN'],{'motivo':'Fase4 broker restaurado'},fatal=True)
        self.state(order,'PAGADA')
        self.record('Outbox publishes preserved event after recovery',self.sql('illari_ordenes','SELECT publicado FROM orden_outbox WHERE id='+event_id)=='t')
    def dashboards(self):
        self.tokens_fresh()
        # Real bounded HTTP probes keep latency samples for every API in the final one-minute window.
        for sample_round in range(2):
            for service,port,path,expected in [('cliente-ms',8081,'/api/v1/clientes/me',getattr(self,'own_profile_status',404)),
                ('servicio-legal-ms',8082,'/api/v1/servicios',200),
                ('orden-legal-ms',8083,'/api/v1/ordenes/'+str(self.paid['id']),200),
                ('pago-ms',8084,'/api/v1/pagos/orden/'+str(self.paid['id']),200),
                ('backend-legacy',8080,'/api/casos',getattr(self,'own_cases_status',403)),
                ('legal-gateway',18080,'/api/v1/ordenes/'+str(self.paid['id']),200)]:
                self.expect('Real API latency sample '+service+' round '+str(sample_round+1),'GET',self.base(service,port)+path,expected,self.tokens['CLIENTE'])
            if sample_round==0: time.sleep(6)
        time.sleep(7)
        evidence=[]
        for panel in self.dashboard['panels']:
            expr=panel['targets'][0]['expr']
            if panel['datasource']['uid']=='loki':
                result=self.loki(expr.replace('${traceId:raw}',self.traces['paid']))
                self.record('Dashboard panel '+str(panel['id'])+' Loki returns purchase logs',bool(result))
                evidence.append({'id':panel['id'],'title':panel['title'],'query':expr,'services':sorted({x['stream']['service'] for x in result}),'entries':sum(len(x['values']) for x in result)})
            else:
                result=self.prom(expr)
                # Alerts intentionally disappear when healthy. Their actual firing series were saved during faults.
                valid=bool(result) or (panel['id']==20 and all(any(x.get('metric',{}).get('alertstate')=='firing' for x in json.loads(path.read_text(encoding='utf-8')).get('ALERTS',[])) for path in [self.evidence/'metrics-dlt.json',self.evidence/'metrics-catalogue-failure.json',self.evidence/'metrics-outbox-exhausted.json']))
                if panel['id']==4:
                    import math
                    valid=len(result)==6 and all(math.isfinite(float(x['value'][1])) for x in result)
                self.record('Dashboard panel '+str(panel['id'])+' PromQL returns real data',valid,len(result))
                evidence.append({'id':panel['id'],'title':panel['title'],'query':expr,'result':result})
        (self.evidence/'grafana-panel-results.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False),encoding='utf-8')
        streams=self.loki('{job="illari-observations"}')
        self.record('Logs from all six applications reach Loki',{'legal-gateway','cliente-ms','servicio-legal-ms','orden-legal-ms','pago-ms','backend-legacy'}.issubset({s['stream'].get('service') for s in streams}))
        forbidden=['authorization','bearer ','password','@example.test','payload','documento de prueba']
        self.record('Central structured records exclude credentials bodies and fixture PII',not any(word in line.lower() for stream in streams for _,line in stream['values'] for word in forbidden))
        self.record('All audit outboxes drained and no exhausted events',bool(self.wait(lambda:all(v==0 for v in self.values('illari_outbox_pendientes or illari_outbox_fallidos')),25)))
        self.record('All controlled-fault alerts clear after recovery',bool(self.wait(lambda:not self.prom('ALERTS{scope="laboratorio",alertstate="firing"}'),70)))
        self.record('All six readiness probes recover',bool(self.wait(lambda:self.values('sum(probe_success{job="illari-readiness"})')==[6.0],20)))
        self.snapshot('recovered')
    def run_all(self):
        self.infra();self.setup();self.purchases();self.trace_evidence();self.dlt();self.catalogue_failure();self.outbox_failure();self.dashboards()

def main():
    p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--project',required=True);p.add_argument('--output',required=True)
    p.add_argument('--frontend-dev',action='store_true',help='Use existing local ng serve; do not assert nginx integration')
    suite=None
    try:
        suite=ObservabilitySuite(p.parse_args());suite.run_all()
    except Exception as error:
        if suite:suite.record('Execution completed',False,type(error).__name__+': '+str(error))
        else:print('Setup refused: '+type(error).__name__,flush=True)
        raise SystemExit(1)
    raise SystemExit(1 if any(not r['passed'] for r in suite.results) else 0)
if __name__=='__main__':main()
