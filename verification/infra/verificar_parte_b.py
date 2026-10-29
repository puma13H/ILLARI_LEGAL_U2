"""Fase 5 B: verificaciones por etapas exclusivamente en el proyecto etiquetado.

Reutiliza verificadores de Fases 3/4; no imprime tokens ni contraseñas.
El estado privado permite continuar sin recrear fixtures ni repetir etapas.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import re
from pathlib import Path
import threading
import time
import uuid

from verificar_observabilidad import ObservabilitySuite


class FinalSuite(ObservabilitySuite):
    def __init__(self, args):
        super().__init__(args)
        self.private = Path(args.private_state).resolve()
        if Path.cwd().resolve() in self.private.parents:
            raise RuntimeError('El estado con credenciales debe quedar fuera del proyecto')
        self.checkpoint = json.loads(self.private.read_text(encoding='utf-8')) if self.private.exists() else {}

    def persist(self):
        for key in ['users', 'order', 'paid', 'traces', 'services', 'legal_case', 'run']:
            if hasattr(self, key):
                self.checkpoint[key] = getattr(self, key)
        self.private.parent.mkdir(parents=True, exist_ok=True)
        self.private.write_text(json.dumps(self.checkpoint, indent=2), encoding='utf-8')

    def resume(self):
        for key, value in self.checkpoint.items():
            setattr(self, key, value)
        self.tokens = {role: self.login(user)['access_token'] for role, user in self.users.items()}

    def security_stage(self):
        if self.checkpoint.get('users'):
            raise RuntimeError('Fixtures ya preparadas: continuar otra etapa; para un ensayo nuevo usar otro estado privado')
        self.eureka()
        self.setup()
        self.security()
        self.commerce()
        self.services = self.expect('Catalog for final scenarios', 'GET', self.gateway + '/api/v1/servicios', 200)[0]
        self.legal()
        self.persist()

    def publish(self, topic, events):
        self.cmd(['docker', 'exec', '-i', self.cid('kafka'), '/opt/kafka/bin/kafka-console-producer.sh',
                  '--bootstrap-server', 'localhost:9092', '--topic', topic],
                 ''.join(json.dumps(e) + '\n' for e in events))

    def offsets(self, group):
        result = self.cmd(['docker', 'exec', self.cid('kafka'), '/opt/kafka/bin/kafka-consumer-groups.sh',
                           '--bootstrap-server', 'localhost:9092', '--describe', '--group', group], ok=False)
        lines = [line.split() for line in result.stdout.splitlines() if line.startswith(group + ' ')]
        rows = [{'group': p[0], 'topic': p[1], 'partition': int(p[2]), 'committed': p[3],
                 'end': p[4], 'lag': p[5]} for p in lines if len(p) >= 6]
        return rows

    def drained(self, group):
        rows = self.offsets(group)
        return rows if rows and all(r['lag'] == '0' for r in rows) else None

    def consistency_stage(self):
        self.resume()
        # The HTTP security stage already created one approved fixture; never reuse purchase #53.
        approved = self.order
        raw = self.sql('illari_pagos', 'SELECT payload FROM pago_outbox WHERE orden_id=' + str(approved['id']))
        old_approval = json.loads(raw)
        rejected, _ = self.purchase('TARJETA_RECHAZADA')
        self.state(rejected, 'CANCELADA')
        self.record('Rejected payment is durably FALLIDO', self.sql('illari_pagos',
                    'SELECT estado FROM pagos_legales WHERE id_orden=' + str(rejected['id'])) == 'FALLIDO')
        barrier = threading.Barrier(8)
        bases = [self.gateway, self.base('orden-legal-ms', 8083)]
        token = self.tokens['ADMIN']
        def cancel(i):
            barrier.wait(timeout=15)
            return self.http('PATCH', bases[i % 2] + '/api/v1/ordenes/' + str(approved['id']) + '/estado',
                             token, {'estado': 'CANCELADA'})[0]
        with ThreadPoolExecutor(max_workers=8) as pool:
            statuses = list(pool.map(cancel, range(8)))
        self.record('Eight concurrent cancellations are idempotent', statuses == [200] * 8, statuses)
        self.state(approved, 'CANCELADA')
        self.record('Concurrent cancellation produces one compensation command', self.sql('illari_ordenes',
                    "SELECT count(*) FROM orden_outbox WHERE orden_id=" + str(approved['id'])
                    + " AND payload::jsonb->>'tipoEvento'='orden.cancelacion.solicitada'") == '1')
        self.record('Approved payment becomes REEMBOLSADO', self.sql('illari_pagos',
                    'SELECT estado FROM pagos_legales WHERE id_orden=' + str(approved['id'])) == 'REEMBOLSADO')
        old_approval['eventId'] = str(uuid.uuid4())
        self.publish('illari.pago.resultado', [old_approval])
        self.record('Late revision-one approval consumed after confirmed refund', bool(self.wait(lambda:
                    self.sql('illari_ordenes', "SELECT count(*) FROM orden_inbox WHERE event_id='"
                             + old_approval['eventId'] + "'") == '1', 25)))
        self.record('Late approval cannot reverse confirmed compensation', self.sql('illari_ordenes',
                    'SELECT estado||\',\'||estado_pago||\',\'||revision_pago FROM ordenes_legales WHERE id='
                    + str(approved['id'])) == 'CANCELADA,REEMBOLSADO,2')
        request = {'metodoPago': 'YAPE', 'detalles': [
            {'idServicio': self.services[0]['id'], 'cantidad': 2},
            {'idServicio': self.services[1]['id'], 'cantidad': 1}]}
        key, trace = 'fase5b-' + str(uuid.uuid4()), str(uuid.uuid4())
        barrier = threading.Barrier(8)
        client = self.tokens['CLIENTE']
        def buy(i):
            barrier.wait(timeout=15)
            status, body, _ = self.http('POST', bases[i % 2] + '/api/v1/ordenes', client, request,
                                        headers={'Idempotency-Key': key, 'X-Trace-ID': trace})
            return {'status': status, 'body': json.loads(body)}
        with ThreadPoolExecutor(max_workers=8) as pool:
            replies = list(pool.map(buy, range(8)))
        self.record('Eight simultaneous purchases return 200', all(r['status'] == 200 for r in replies), [r['status'] for r in replies])
        ids = {r['body'].get('id') for r in replies}
        self.record('Gateway and direct concurrent purchase share one order', len(ids) == 1 and None not in ids, sorted(ids))
        if len(ids) != 1 or None in ids:
            raise RuntimeError('Concurrent purchase did not yield one order')
        self.paid = replies[0]['body']
        self.traces['paid'] = trace
        self.persist()
        self.state(self.paid, 'PAGADA')
        self.record('Concurrent purchase creates one payment and two relational details',
                    self.sql('illari_pagos', 'SELECT count(*) FROM pagos_legales WHERE id_orden=' + str(self.paid['id'])) == '1'
                    and self.sql('illari_ordenes', 'SELECT count(*) FROM orden_detalle WHERE orden_id=' + str(self.paid['id'])) == '2')
        replay, _ = self.expect('HTTP replay preserves original response', 'POST', self.gateway + '/api/v1/ordenes',
                               200, client, request, headers={'Idempotency-Key': key}, fatal=True)
        self.record('Replay response is identical to stored original', replay == self.paid)
        self.expect('Same Idempotency-Key with different data returns 409', 'POST', self.gateway + '/api/v1/ordenes',
                    409, client, dict(request, metodoPago='PLIN'), headers={'Idempotency-Key': key})
        original = json.loads(self.sql('illari_ordenes', 'SELECT payload FROM orden_outbox WHERE orden_id=' + str(self.paid['id'])))
        self.publish('illari.orden.creada', [original] * 4)
        self.record('Payment offsets commit past duplicate messages', bool(self.wait(lambda: self.drained('illari-pago-ms'), 30)))
        self.record('Duplicate event has one inbox row and one payment',
                    self.sql('illari_pagos', "SELECT count(*) FROM pago_inbox WHERE event_id='" + original['eventId'] + "'") == '1'
                    and self.sql('illari_pagos', 'SELECT count(*) FROM pagos_legales WHERE id_orden=' + str(self.paid['id'])) == '1')
        result = json.loads(self.sql('illari_pagos', 'SELECT payload FROM pago_outbox WHERE orden_id=' + str(self.paid['id'])))
        self.publish('illari.pago.resultado', [result] * 4)
        self.record('Order offsets commit past duplicate results', bool(self.wait(lambda: self.drained('illari-orden-legal-ms'), 30)))
        self.record('Duplicate results leave order PAGADA revision one', self.sql('illari_ordenes',
                    'SELECT estado||\',\'||revision_pago FROM ordenes_legales WHERE id=' + str(self.paid['id'])) == 'PAGADA,1')
        (self.evidence / 'offsets-after-duplicates.json').write_text(json.dumps(
            {g: self.offsets(g) for g in ['illari-pago-ms', 'illari-orden-legal-ms']}, indent=2), encoding='utf-8')
        self.persist()

    def diagnostic_stage(self):
        self.resume()
        self.infra()
        self.trace_evidence()
        self.catalogue_business_and_timeout()
        self.dlt()
        self.catalogue_failure()
        self.outbox_failure()
        # Unlike the Fase4 unlinked user, this fixture has commercial and legal profiles.
        self.own_profile_status = self.own_cases_status = 200
        self.dashboards()
        self.persist()

    def multi_instance_stage(self):
        self.resume()
        original = json.loads(self.cmd(['docker', 'inspect', self.cid('orden-legal-ms')]))[0]
        labels = original['Config']['Labels']
        if labels.get('com.docker.compose.project') != self.args.project or labels.get('illari.environment') != 'verification':
            raise RuntimeError('Foreign instance refused')
        network = next(n for n in original['NetworkSettings']['Networks'] if n.startswith(self.args.project + '_'))
        environment = dict(item.split('=', 1) for item in original['Config']['Env'])
        environment.update(EUREKA_CLIENT_REGISTER_WITH_EUREKA='false',
                           ILLARI_OBSERVATION_DIRECTORY='/tmp/illari-observations')
        env_path = self.private.with_name('replica-private.env')
        env_path.write_text(''.join(k + '=' + v + '\n' for k, v in environment.items()), encoding='utf-8')
        replica = self.cmd(['docker', 'create', '--name', self.args.project + '-replica-b-' + uuid.uuid4().hex[:8],
                            '--label', 'com.docker.compose.project=' + self.args.project,
                            '--label', 'illari.environment=verification', '--label', 'illari.test.phase=5b',
                            '--network', network, '--memory', '512m', '--read-only', '--tmpfs', '/tmp:rw,nosuid,size=128m',
                            '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true',
                            '--publish', '127.0.0.1::8083', '--env-file', str(env_path), original['Image']])
        self.checkpoint['temporary_replica_id'] = replica
        self.persist()
        try:
            self.cmd(['docker', 'start', replica])
            self.ids['orden-replica-b'] = replica
            base = self.base('orden-replica-b', 8083)
            self.record('Second isolated Orders JVM reaches readiness', bool(self.wait(
                        lambda: self.http('GET', base + '/actuator/health/readiness')[0] == 200, 100)))
            self.tokens_fresh()
            request = {'metodoPago': 'YAPE', 'detalles': [
                {'idServicio': self.services[0]['id'], 'cantidad': 2},
                {'idServicio': self.services[1]['id'], 'cantidad': 1}]}
            key, trace = 'two-jvms-' + str(uuid.uuid4()), str(uuid.uuid4())
            barrier = threading.Barrier(8)
            bases = [self.base('orden-legal-ms', 8083), base]
            token = self.tokens['CLIENTE']
            def buy(i):
                barrier.wait(timeout=15)
                status, body, _ = self.http('POST', bases[i % 2] + '/api/v1/ordenes', token, request,
                                            headers={'Idempotency-Key': key, 'X-Trace-ID': trace})
                return status, json.loads(body).get('id')
            with ThreadPoolExecutor(max_workers=8) as pool:
                replies = list(pool.map(buy, range(8)))
            ids = {row[1] for row in replies}
            self.record('Eight purchases across two JVMs return one order',
                        all(row[0] == 200 for row in replies) and len(ids) == 1 and None not in ids, replies)
            if len(ids) != 1 or None in ids:
                raise RuntimeError('Two-instance purchase did not converge')
            order = {'id': next(iter(ids))}
            self.checkpoint['multi_instance_order'] = order
            self.persist()
            self.state(order, 'PAGADA')
            self.record('Two-JVM purchase has one payment and two details',
                        self.sql('illari_pagos', 'SELECT count(*) FROM pagos_legales WHERE id_orden=' + str(order['id'])) == '1'
                        and self.sql('illari_ordenes', 'SELECT count(*) FROM orden_detalle WHERE orden_id=' + str(order['id'])) == '2')
            barrier = threading.Barrier(8)
            token = self.tokens['ADMIN']
            def cancel(i):
                barrier.wait(timeout=15)
                return self.http('PATCH', bases[i % 2] + '/api/v1/ordenes/' + str(order['id']) + '/estado',
                                 token, {'estado': 'CANCELADA'})[0]
            with ThreadPoolExecutor(max_workers=8) as pool:
                statuses = list(pool.map(cancel, range(8)))
            self.record('Concurrent cancellations across two JVMs are accepted', statuses == [200] * 8, statuses)
            self.state(order, 'CANCELADA')
            self.record('Two-JVM cancellation emits one command and confirms refund',
                        self.sql('illari_ordenes', "SELECT count(*) FROM orden_outbox WHERE orden_id=" + str(order['id'])
                                 + " AND payload::jsonb->>'tipoEvento'='orden.cancelacion.solicitada'") == '1'
                        and self.sql('illari_pagos', 'SELECT estado FROM pagos_legales WHERE id_orden=' + str(order['id'])) == 'REEMBOLSADO')
        finally:
            # Only the NEW fixture container, with no volumes. Existing databases and services remain.
            self.cmd(['docker', 'stop', replica])
            self.cmd(['docker', 'rm', replica])
            self.checkpoint.pop('temporary_replica_id', None)
            self.persist()

    def closure_stage(self):
        self.resume()
        from decimal import Decimal
        def rows(db, select):
            return json.loads(self.sql(db, "SELECT COALESCE(json_agg(q),'[]'::json) FROM (" + select + ') q'))
        orders = rows('illari_ordenes', 'SELECT id,id_cliente,estado,total::text,pago_id,estado_pago,revision_pago FROM ordenes_legales')
        payments = rows('illari_pagos', 'SELECT id,id_orden,id_cliente,estado,monto::text FROM pagos_legales')
        by_order = {}
        for pay in payments:
            by_order.setdefault(pay['id_orden'], []).append(pay)
        order_map = {o['id']: o for o in orders}
        checks = {
            'No duplicate payment per order': [oid for oid, ps in by_order.items() if len(ps) != 1],
            'No CANCELADA order has VALIDADO payment': [o['id'] for o in orders if o['estado'] == 'CANCELADA'
                                                       and any(p['estado'] == 'VALIDADO' for p in by_order.get(o['id'], []))],
            'Every PAGADA order has exactly one VALIDADO payment': [o['id'] for o in orders if o['estado'] == 'PAGADA'
                  and not (len(by_order.get(o['id'], [])) == 1 and by_order[o['id']][0]['estado'] == 'VALIDADO')],
            'Payments reference existing orders with matching owner and amount': [p['id'] for p in payments
                  if p['id_orden'] not in order_map or p['id_cliente'] != order_map[p['id_orden']]['id_cliente']
                  or Decimal(p['monto']) != Decimal(order_map[p['id_orden']]['total'])],
            'All order totals requiring payment are positive': [o['id'] for o in orders if Decimal(o['total']) <= 0],
            'All order payment IDs agree with payment service': [o['id'] for o in orders if o['pago_id'] is not None
                  and not any(p['id'] == o['pago_id'] and p['estado'] == o['estado_pago'] for p in by_order.get(o['id'], []))],
            'No cancellation intent remains unconfirmed at closure': [o['id'] for o in orders if o['estado'] == 'CANCELACION_PENDIENTE'],
        }
        for name, violations in checks.items():
            self.record(name, not violations, {'violating_ids': violations})
        self.record('Every relational detail matches quantity times historical price and order total',
                    self.sql('illari_ordenes', 'SELECT count(*) FROM ordenes_legales o LEFT JOIN '
                             '(SELECT orden_id,sum(subtotal) total,bool_and(cantidad>0 AND subtotal=precio_unitario*cantidad) valid '
                             'FROM orden_detalle GROUP BY orden_id) d ON d.orden_id=o.id '
                             'WHERE d.valid IS DISTINCT FROM true OR d.total<>o.total') == '0')
        self.record('Historical migration fixture retains JSON, total and two relational details', self.sql('illari_fase3_historico',
                    "SELECT count(*) FROM ordenes_legales o WHERE id=9000 AND total=120 AND jsonb_array_length(detalles_json::jsonb)=2 "
                    'AND (SELECT count(*) FROM orden_detalle d WHERE d.orden_id=o.id)=2 '
                    'AND (SELECT sum(subtotal) FROM orden_detalle d WHERE d.orden_id=o.id)=120') == '1')
        self.record('Part A purchase 53 remains PAGADA and payment 53 VALIDADO at 310.00',
                    self.sql('illari_ordenes', "SELECT count(*) FROM ordenes_legales WHERE id=53 AND estado='PAGADA' AND total=310 AND pago_id=53") == '1'
                    and self.sql('illari_pagos', "SELECT count(*) FROM pagos_legales WHERE id=53 AND id_orden=53 AND estado='VALIDADO' AND monto=310") == '1')
        for db, prefix in [('illari_ordenes', 'orden'), ('illari_pagos', 'pago')]:
            self.record(prefix + ' outbox fully drained and DLT resolved',
                        self.sql(db, 'SELECT count(*) FROM ' + prefix + '_outbox WHERE publicado=false OR fallido=true') == '0'
                        and self.sql(db, "SELECT count(*) FROM " + prefix + "_dead_letter WHERE estado IN ('DLT','REPLAY_FALLIDO','REPLAY_PENDIENTE')") == '0')
        for group in ['illari-pago-ms', 'illari-orden-legal-ms', 'illari-pago-dlt', 'illari-orden-dlt']:
            self.record(group + ' final committed offsets equal log end', bool(self.wait(lambda: self.drained(group), 20)))
        self.record('All six scrape targets and readiness restored', self.values('sum(up)') == [12.0]
                    and self.values('sum(probe_success{job="illari-readiness"})') == [6.0])
        self.record('No active laboratory alerts at closure', not self.prom('ALERTS{scope="laboratorio",alertstate="firing"}'))
        # Smoke authorization after rebuilding the only changed Java service.
        base = self.base('orden-legal-ms', 8083)
        for via, target in [('gateway', self.gateway), ('direct', base)]:
            path = '/api/v1/ordenes/' + str(self.paid['id'])
            self.expect('Rebuilt Orders ' + via + ' anonymous', 'GET', target + path, 401)
            self.expect('Rebuilt Orders ' + via + ' owner', 'GET', target + path, 200, self.tokens['CLIENTE'])
            self.expect('Rebuilt Orders ' + via + ' foreign client', 'GET', target + path, 404, self.tokens['OTRO_CLIENTE'])
        (self.evidence / 'invariantes-alcance.json').write_text(json.dumps({'orders_examined': len(orders),
            'payments_examined': len(payments), 'violations': checks}, indent=2), encoding='utf-8')

    def payment_validation_stage(self):
        self.resume()
        result = json.loads(self.sql('illari_pagos', 'SELECT payload FROM pago_outbox WHERE orden_id=' + str(self.paid['id'])))
        cases = [('wrong-client', {'idCliente': self.users['OTRO_CLIENTE']['sub']}),
                 ('wrong-amount', {'monto': 0.01}), ('wrong-order', {'ordenId': self.order['id']})]
        for name, change in cases:
            corrected = dict(result, eventId=str(uuid.uuid4()))
            invalid = dict(corrected, **change)
            self.publish('illari.pago.resultado', [invalid])
            def lookup():
                return self.sql('illari_ordenes', "SELECT id FROM orden_dead_letter WHERE payload::jsonb->>'eventId'='"
                                + corrected['eventId'] + "'") or None
            dead = self.wait(lookup, 25)
            self.record('Inconsistent payment ' + name + ' rejected to Orders DLT', bool(dead))
            self.record('Rejected ' + name + ' has no successful inbox receipt',
                        self.sql('illari_ordenes', "SELECT count(*) FROM orden_inbox WHERE event_id='" + corrected['eventId'] + "'") == '0')
            self.record('Invalid ' + name + ' leaves paid order and refunded order unchanged',
                        self.sql('illari_ordenes', "SELECT estado FROM ordenes_legales WHERE id=" + str(self.paid['id'])) == 'PAGADA'
                        and self.sql('illari_ordenes', "SELECT estado_pago FROM ordenes_legales WHERE id=" + str(self.order['id'])) == 'REEMBOLSADO')
            if not dead:
                raise RuntimeError('Expected validation DLT was not captured')
            for role in ['CLIENTE', 'ABOGADO']:
                self.expect(name + ' real DLT replay denied to ' + role, 'POST', self.gateway
                            + '/api/v1/ordenes/mensajeria/fallidos/' + dead + '/reprocesar', 403,
                            self.tokens[role], {'motivo': 'Intento de fixture sin permiso'})
            self.expect(name + ' DLT corrected replay authorized ADMIN', 'POST', self.gateway
                        + '/api/v1/ordenes/mensajeria/fallidos/' + dead + '/reprocesar', 202,
                        self.tokens['ADMIN'], {'payloadCorregido': json.dumps(corrected),
                                              'motivo': 'Fase5B: corregir contrato de fixture ' + name}, fatal=True)
            self.record(name + ' corrected event consumed once', bool(self.wait(lambda:
                        self.sql('illari_ordenes', "SELECT count(*) FROM orden_inbox WHERE event_id='" + corrected['eventId'] + "'") == '1', 25)))
        self.record('Orders DLT and business offsets now commit after actual records',
                    bool(self.wait(lambda: self.drained('illari-orden-dlt') and self.drained('illari-orden-legal-ms'), 25)))
        self.record('Validation and replay never duplicate payment', self.sql('illari_pagos',
                    'SELECT count(*) FROM pagos_legales WHERE id_orden=' + str(self.paid['id'])) == '1')
        (self.evidence / 'offsets-after-payment-validation.json').write_text(json.dumps(
            {g: self.offsets(g) for g in ['illari-orden-dlt', 'illari-orden-legal-ms']}, indent=2), encoding='utf-8')

    def raw_resilience(self):
        status, raw, _ = self.http('GET', self.base('orden-legal-ms', 8083) + '/actuator/prometheus')
        if status != 200:
            raise RuntimeError('Order metrics unavailable')
        lines = raw.decode().splitlines()
        def total(name, labels):
            return sum(float(line.rsplit(' ', 1)[1]) for line in lines
                       if line.startswith(name + '{') and all(k in line for k in labels))
        return {'failed': total('resilience4j_circuitbreaker_calls_seconds_count', ['kind="failed"']),
                'closed': total('resilience4j_circuitbreaker_state', ['state="closed"']),
                'open': total('resilience4j_circuitbreaker_state', ['state="open"']),
                'retry_failed': total('resilience4j_retry_calls_total', ['kind="failed_with_retry"'])}

    def catalogue_business_and_timeout(self):
        before = self.raw_resilience()
        self.record('Circuit initially CLOSED before business errors', before['closed'] == 1)
        self.tokens_fresh()
        for i in range(6):
            self.expect('Purchase missing service business error ' + str(i + 1), 'POST', self.gateway + '/api/v1/ordenes',
                        400, self.tokens['CLIENTE'], {'metodoPago': 'YAPE', 'detalles': [{'idServicio': 999999999, 'cantidad': 1}]},
                        headers={'Idempotency-Key': 'missing-' + str(uuid.uuid4())})
            self.expect('Diagnostic missing service business error ' + str(i + 1), 'GET',
                        self.gateway + '/api/v1/ordenes/resiliencia/servicio/999999999', 400, self.tokens['CLIENTE'])
        after = self.raw_resilience()
        self.record('Both routes exclude business errors from shared breaker',
                    after['closed'] == 1 and after['failed'] == before['failed'], {'before': before, 'after': after})
        service = self.cid('servicio-legal-ms')
        started = time.monotonic()
        self.cmd(['docker', 'pause', service])
        try:
            self.expect('Paused catalogue triggers bounded Feign read timeout and 503 fallback', 'POST',
                        self.gateway + '/api/v1/ordenes', 503, self.tokens['CLIENTE'],
                        {'metodoPago': 'YAPE', 'detalles': [{'idServicio': self.services[0]['id'], 'cantidad': 1}]},
                        headers={'Idempotency-Key': 'timeout-' + str(uuid.uuid4())})
            elapsed = time.monotonic() - started
        finally:
            self.cmd(['docker', 'unpause', service])
        after_timeout = self.raw_resilience()
        self.record('Real timeout is bounded and Retry registers failure', 2.5 <= elapsed < 15
                    and after_timeout['failed'] > after['failed'] and after_timeout['retry_failed'] > after['retry_failed'],
                    {'elapsed_seconds': round(elapsed, 3), 'metrics': after_timeout})
        def close():
            status, _, _ = self.http('GET', self.gateway + '/api/v1/ordenes/resiliencia/servicio/'
                                     + str(self.services[0]['id']), self.tokens['CLIENTE'])
            return status == 200 and self.raw_resilience()['closed'] == 1
        self.record('Timeout recovers with successful Feign calls', bool(self.wait(close, 45)))

    def outbox_failure(self):
        kafka = self.cid('kafka')
        self.cmd(['docker', 'stop', kafka])
        try:
            order, trace = self.purchase()
            self.checkpoint['broker_fault_order'] = order
            self.persist()
            self.record('Broker-down order and outbox survive durably', self.sql('illari_ordenes',
                        'SELECT count(*) FROM orden_outbox WHERE orden_id=' + str(order['id']) + ' AND publicado=false') == '1')
            # Cancellation races with the approval which will arrive only after broker recovery.
            self.expect('Cancel while Kafka is unavailable', 'PATCH', self.gateway + '/api/v1/ordenes/'
                        + str(order['id']) + '/estado', 200, self.tokens['ADMIN'], {'estado': 'CANCELADA'}, fatal=True)
            self.record('Unconfirmed cancellation stays CANCELACION_PENDIENTE', self.sql('illari_ordenes',
                        'SELECT estado FROM ordenes_legales WHERE id=' + str(order['id'])) == 'CANCELACION_PENDIENTE')
            self.record('Outbox pending metric detects both durable commands', bool(self.wait(
                        lambda: any(v >= 2 for v in self.values('illari_outbox_pendientes{job="orden-legal-ms"}')), 20)))
            self.snapshot('outbox-queued')
            self.record('Outbox retry counter grows during outage', bool(self.wait(
                        lambda: any(v > 0 for v in self.values('illari_outbox_reintentados_total{job="orden-legal-ms"}')), 20)))
            self.record('Old pending outbox alert fires', bool(self.wait(
                        lambda: self.prom('ALERTS{alertname="IllariOutboxAntiguo",alertstate="firing"}'), 25)))
            exhausted = self.wait(lambda: self.sql('illari_ordenes', 'SELECT count(*) FROM orden_outbox WHERE orden_id='
                                  + str(order['id']) + ' AND fallido=true') == '2', 80)
            self.record('Bounded attempts exhaust both commands without deleting them', bool(exhausted))
            self.record('Exhausted outbox alert fires', bool(self.wait(
                        lambda: self.prom('ALERTS{alertname="IllariOutboxAgotado",alertstate="firing"}'), 25)))
            self.snapshot('outbox-exhausted')
        finally:
            self.cmd(['docker', 'start', kafka])
        self.record('Kafka healthy after controlled stop', bool(self.wait(
                    lambda: self.cmd(['docker', 'inspect', '--format', '{{.State.Health.Status}}', kafka]) == 'healthy', 100)))
        self.tokens_fresh()
        other, _ = self.purchase()
        self.state(other, 'PAGADA')
        self.record('Exhausted older outbox does not block a newer valid purchase', self.sql('illari_ordenes',
                    'SELECT count(*) FROM orden_outbox WHERE orden_id=' + str(order['id']) + ' AND fallido=true') == '2')
        for event_id in self.sql('illari_ordenes', 'SELECT id FROM orden_outbox WHERE orden_id=' + str(order['id'])
                                 + ' AND publicado=false ORDER BY id').splitlines():
            self.expect('ADMIN retries preserved outbox ' + event_id, 'POST', self.gateway
                        + '/api/v1/ordenes/mensajeria/outbox/' + event_id + '/reintentar', 202,
                        self.tokens['ADMIN'], {'motivo': 'Fase5B: broker restaurado y cancelacion coordinada'}, fatal=True)
        self.state(order, 'CANCELADA')
        self.record('Approval after cancellation intent is compensated', self.sql('illari_pagos',
                    'SELECT estado FROM pagos_legales WHERE id_orden=' + str(order['id'])) in ('CANCELADO', 'REEMBOLSADO'))
        self.record('Both preserved commands published after authorized recovery', self.sql('illari_ordenes',
                    'SELECT count(*) FROM orden_outbox WHERE orden_id=' + str(order['id']) + ' AND publicado=false') == '0')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', required=True)
    parser.add_argument('--project', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--private-state', required=True)
    parser.add_argument('--frontend-dev', action='store_true',help='Use existing local ng serve; nginx remains unverified')
    parser.add_argument('--stage', required=True, choices=['security', 'consistency', 'multi-instance', 'diagnostics', 'payment-validation', 'closure'])
    args = parser.parse_args()
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    suite = None
    try:
        suite = FinalSuite(args)
        getattr(suite, {'security': 'security_stage', 'consistency': 'consistency_stage',
                        'multi-instance': 'multi_instance_stage', 'diagnostics': 'diagnostic_stage',
                        'payment-validation': 'payment_validation_stage', 'closure': 'closure_stage'}[args.stage])()
    except Exception as error:
        if suite:
            suite.persist()
            suite.record('Stage completed', False, type(error).__name__ + ': ' + str(error))
        else:
            print('Setup refused: ' + type(error).__name__, flush=True)
        raise SystemExit(1)
    raise SystemExit(1 if any(not r['passed'] for r in suite.results) else 0)


if __name__ == '__main__':
    main()
