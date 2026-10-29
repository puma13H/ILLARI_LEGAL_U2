"""Corroboración SOLO LECTURA de una compra ya realizada en el navegador.

No crea usuarios, pedidos ni pagos. No sustituye la prueba de interfaz.
Requiere el proyecto local de laboratorio etiquetado por verificar_s12.Suite.
"""
import argparse
import json
import uuid
from pathlib import Path

from verificar_s12 import Suite


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', required=True)
    parser.add_argument('--project', required=True)
    parser.add_argument('--order-id', type=int, required=True)
    parser.add_argument('--client-sub', type=uuid.UUID, required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if args.order_id < 1:
        parser.error('order-id debe ser positivo')
    suite = Suite(args)
    oid, client = str(args.order_id), str(args.client_sub)

    def read(db, query):
        # La sesión rechaza cualquier modificación accidental, incluso en el laboratorio.
        return suite.sql(db, 'BEGIN READ ONLY;\n' + query + ';\nROLLBACK;').splitlines()

    def rows(db, query):
        result = read(db, 'SELECT COALESCE(json_agg(q),\'[]\'::json) FROM (' + query + ') q')
        return json.loads('\n'.join(line for line in result if line not in ('BEGIN', 'ROLLBACK')))

    orders = rows('illari_ordenes', 'SELECT id,estado,total::text,metodo_pago,pago_id,estado_pago '
                  "FROM ordenes_legales WHERE id=" + oid + " AND id_cliente='" + client + "'")
    payments = rows('illari_pagos', 'SELECT id,id_orden,estado,monto::text,id_cliente FROM pagos_legales WHERE id_orden=' + oid)
    details = rows('illari_ordenes', 'SELECT servicio_id,nombre_historico,cantidad,precio_unitario::text,subtotal::text '
                   'FROM orden_detalle WHERE orden_id=' + oid + ' ORDER BY posicion')
    from decimal import Decimal
    suite.record('Compra de navegador pertenece al CLIENTE esperado', len(orders) == 1)
    suite.record('Existe exactamente una orden del CLIENTE de laboratorio',
                 rows('illari_ordenes', "SELECT count(*) n FROM ordenes_legales WHERE id_cliente='" + client + "'")[0]['n'] == 1)
    suite.record('Un solo pago VALIDADO coincide con orden, cliente e importe',
                 len(orders) == len(payments) == 1 and payments[0]['estado'] == 'VALIDADO'
                 and payments[0]['id_cliente'] == client and payments[0]['id'] == orders[0]['pago_id']
                 and Decimal(payments[0]['monto']) == Decimal(orders[0]['total']))
    suite.record('Orden PAGADA y estado de pago VALIDADO', bool(orders)
                 and orders[0]['estado'] == 'PAGADA' and orders[0]['estado_pago'] == 'VALIDADO')
    suite.record('Carrito N conserva dos servicios distintos y cantidades 2 y 1', len(details) == 2
                 and len({d['servicio_id'] for d in details}) == 2 and [d['cantidad'] for d in details] == [2, 1])
    suite.record('Precios históricos, subtotales y total 310.00 exactos', len(details) == 2 and bool(orders)
                 and [Decimal(d['precio_unitario']) for d in details] == [Decimal('80'), Decimal('150')]
                 and all(Decimal(d['subtotal']) == d['cantidad'] * Decimal(d['precio_unitario']) for d in details)
                 and sum(Decimal(d['subtotal']) for d in details) == Decimal(orders[0]['total']) == Decimal('310'))
    suite.record('Idempotency-Key persistida con respuesta y orden',
                 rows('illari_ordenes', 'SELECT count(*) n FROM compra_idempotencia WHERE orden_id=' + oid
                      + ' AND respuesta IS NOT NULL')[0]['n'] == 1)
    events = {}
    for service, db, prefix, peer_db, peer_prefix in [
        ('orden-legal-ms', 'illari_ordenes', 'orden', 'illari_pagos', 'pago'),
        ('pago-ms', 'illari_pagos', 'pago', 'illari_ordenes', 'orden'),
    ]:
        out = rows(db, "SELECT event_id,publicado,fallido,payload::jsonb->>'traceId' AS trace,"
                   "payload::jsonb->>'tipoEvento' AS tipo FROM " + prefix + '_outbox WHERE orden_id=' + oid)
        suite.record(service + ': outbox publicado sin agotamiento', len(out) == 1
                     and out[0]['publicado'] and not out[0]['fallido'])
        if out:
            event = out[0]
            event_id = str(uuid.UUID(event['event_id']))
            trace = str(uuid.UUID(event['trace']))
            suite.record(service + ': eventId consumido una vez en inbox destino',
                         rows(peer_db, "SELECT count(*) n FROM " + peer_prefix + "_inbox WHERE event_id='" + event_id + "'")[0]['n'] == 1)
            events[service] = {'eventId': event_id, 'traceId': trace, 'tipo': event['tipo']}
    suite.record('Kafka conserva una correlación técnica común a la compra', len(events) == 2
                 and len({e['traceId'] for e in events.values()}) == 1)
    # Solo exportar campos diagnósticos permitidos; nunca payloads, tokens ni datos de usuarios.
    observations = []
    for service in ['orden-legal-ms', 'pago-ms']:
        raw = suite.cmd(['docker', 'exec', suite.cid(service), 'tail', '-n', '2000',
                         '/observations/' + service + '.json'], ok=False)
        for line in (raw.stdout + '\n' + raw.stderr).splitlines():
            try:
                record = json.loads(line)
            except (ValueError, TypeError):
                continue
            if record.get('traceId') in {e['traceId'] for e in events.values()}:
                observations.append({'service': service, **{k: record[k] for k in
                                     ['timestamp', 'traceId', 'eventId', 'message'] if k in record}})
    suite.record('Logs confirman publicación y consumo en ambos microservicios',
                 all(any(o['service'] == service and o.get('message') == 'kafka_processed' for o in observations)
                     for service in ['orden-legal-ms', 'pago-ms']))
    evidence = {'orderId': args.order_id, 'orders': orders,
                'payments': [{k: v for k, v in p.items() if k != 'id_cliente'} for p in payments],
                'details': details, 'events': events, 'observations': observations}
    Path(args.output).with_name('compra-persistencia.json').write_text(
        json.dumps(evidence, indent=2, ensure_ascii=False), encoding='utf-8')
    raise SystemExit(1 if any(not r['passed'] for r in suite.results) else 0)


if __name__ == '__main__':
    main()
