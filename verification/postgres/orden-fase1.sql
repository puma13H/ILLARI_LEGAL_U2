-- EXCLUSIVO de la base VACÍA de pruebas. CREATE falla si ya contiene tablas.
CREATE TABLE ordenes_legales (
 id BIGSERIAL PRIMARY KEY, id_cliente VARCHAR(255) NOT NULL, fecha_creacion TIMESTAMPTZ NOT NULL,
 estado VARCHAR(255) NOT NULL, total NUMERIC(12,2) NOT NULL, metodo_pago VARCHAR(255) NOT NULL, detalles_json TEXT NOT NULL
);
CREATE TABLE orden_outbox (
 id BIGSERIAL PRIMARY KEY, orden_id BIGINT NOT NULL, payload TEXT NOT NULL, publicado BOOLEAN NOT NULL, creado TIMESTAMPTZ NOT NULL
);
INSERT INTO ordenes_legales VALUES (9000,'historical-client',NOW(),'PAGADA',120.00,'YAPE',
'[{"idServicio":7,"nombreServicio":"Consulta histórica","cantidad":2,"precioUnitario":50.00,"subtotal":100.00},{"idServicio":8,"nombreServicio":"Contrato histórico","cantidad":1,"precioUnitario":20.00,"subtotal":20.00}]');
INSERT INTO orden_outbox VALUES(9000,9000,
'{"tipoEvento":"orden.creada","ordenId":9000,"idCliente":"historical-client","total":120.00,"metodoPago":"YAPE","origen":"orden-legal-ms","timestamp":"2026-10-27T00:00:00Z"}',TRUE,NOW());
SELECT setval(pg_get_serial_sequence('ordenes_legales','id'),9000);
SELECT setval(pg_get_serial_sequence('orden_outbox','id'),9000);
