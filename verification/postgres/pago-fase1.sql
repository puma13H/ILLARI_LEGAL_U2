-- EXCLUSIVO de la base VACÍA de pruebas. No ejecutar en la BD del usuario.
CREATE TABLE pagos_legales (
 id BIGSERIAL PRIMARY KEY, id_orden BIGINT NOT NULL UNIQUE, id_cliente VARCHAR(255) NOT NULL,
 monto NUMERIC(12,2) NOT NULL, estado VARCHAR(255) NOT NULL, fecha_creacion TIMESTAMPTZ NOT NULL, fecha_aprobacion TIMESTAMPTZ
);
CREATE TABLE pago_outbox (
 id BIGSERIAL PRIMARY KEY, orden_id BIGINT NOT NULL UNIQUE, payload TEXT NOT NULL, publicado BOOLEAN NOT NULL, creado TIMESTAMPTZ NOT NULL
);
INSERT INTO pagos_legales VALUES(9000,9000,'historical-client',120.00,'VALIDADO',NOW(),NOW());
INSERT INTO pago_outbox VALUES(9000,9000,
'{"tipoEvento":"pago.validado","ordenId":9000,"idCliente":"historical-client","monto":120.00,"estado":"VALIDADO","origen":"pago-ms","timestamp":"2026-10-27T00:00:00Z","pagoId":9000,"revisionPago":1}',TRUE,NOW());
SELECT setval(pg_get_serial_sequence('pagos_legales','id'),9000);
SELECT setval(pg_get_serial_sequence('pago_outbox','id'),9000);
