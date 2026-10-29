-- BD de Órdenes. Aplicar DESPUÉS de 04-fase1-saga.sql. No elimina detalles_json.
-- Todo el backfill es transaccional: un histórico inválido aborta sin perder información.
BEGIN;
CREATE TABLE IF NOT EXISTS orden_detalle (
    id BIGSERIAL PRIMARY KEY,
    orden_id BIGINT NOT NULL REFERENCES ordenes_legales(id),
    posicion INTEGER NOT NULL,
    servicio_id BIGINT NOT NULL CHECK (servicio_id > 0),
    nombre_historico VARCHAR(255) NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal = precio_unitario * cantidad),
    CONSTRAINT uk_detalle_posicion UNIQUE (orden_id,posicion)
);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ordenes_legales WHERE jsonb_typeof(detalles_json::jsonb) IS DISTINCT FROM 'array') THEN
        RAISE EXCEPTION 'Revisar detalles_json históricos: se requiere un array; no se modificó el histórico';
    END IF;
    -- Validate BEFORE assigning NUMERIC(12,2), which would otherwise round historical amounts.
    IF EXISTS (
        SELECT 1 FROM ordenes_legales o
        CROSS JOIN LATERAL jsonb_array_elements(o.detalles_json::jsonb) AS d(item)
        WHERE (d.item->>'idServicio')::bigint IS NULL OR (d.item->>'idServicio')::bigint <= 0
           OR d.item->>'nombreServicio' IS NULL OR length(d.item->>'nombreServicio') > 255
           OR (d.item->>'cantidad')::integer IS NULL OR (d.item->>'cantidad')::integer <= 0
           OR (d.item->>'precioUnitario')::numeric IS NULL OR (d.item->>'precioUnitario')::numeric < 0
           OR (d.item->>'precioUnitario')::numeric <> round((d.item->>'precioUnitario')::numeric,2)
           OR (d.item->>'subtotal')::numeric IS NULL
           OR (d.item->>'subtotal')::numeric <> (d.item->>'precioUnitario')::numeric * (d.item->>'cantidad')::integer
    ) THEN RAISE EXCEPTION 'Detalle histórico inválido; no se redondearon ni descartaron datos'; END IF;
END;
$$;
INSERT INTO orden_detalle (orden_id,posicion,servicio_id,nombre_historico,cantidad,precio_unitario,subtotal)
SELECT o.id, (d.posicion-1)::integer, (d.item->>'idServicio')::bigint,
       d.item->>'nombreServicio', (d.item->>'cantidad')::integer,
       (d.item->>'precioUnitario')::numeric, (d.item->>'subtotal')::numeric
FROM ordenes_legales o
CROSS JOIN LATERAL jsonb_array_elements(o.detalles_json::jsonb) WITH ORDINALITY AS d(item,posicion)
ON CONFLICT (orden_id,posicion) DO NOTHING;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM ordenes_legales o
        WHERE jsonb_array_length(o.detalles_json::jsonb) > 0 AND (
            (SELECT count(*) FROM orden_detalle d WHERE d.orden_id=o.id) <> jsonb_array_length(o.detalles_json::jsonb)
            OR (SELECT sum(subtotal) FROM orden_detalle d WHERE d.orden_id=o.id) <> o.total
        )
    ) THEN RAISE EXCEPTION 'Detalle histórico incompleto o total inconsistente; migración revertida'; END IF;
END;
$$;
CREATE INDEX IF NOT EXISTS ix_detalle_servicio ON orden_detalle(servicio_id);
CREATE TABLE IF NOT EXISTS compra_idempotencia (
    id VARCHAR(64) PRIMARY KEY,
    cliente VARCHAR(255) NOT NULL,
    clave VARCHAR(128) NOT NULL,
    huella VARCHAR(64) NOT NULL,
    respuesta TEXT,
    orden_id BIGINT REFERENCES ordenes_legales(id),
    creada TIMESTAMPTZ NOT NULL
);
COMMIT;
