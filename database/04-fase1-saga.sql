-- Fase 1. Migración ADITIVA para PostgreSQL. No se ejecuta automáticamente.
-- Ejecutar en la BD propietaria de ordenes_legales, después de revisar/resguardar el entorno.
-- No modifica estados, importes, contraseñas ni registros existentes.
BEGIN;
ALTER TABLE ordenes_legales ADD COLUMN IF NOT EXISTS pago_id BIGINT;
ALTER TABLE ordenes_legales ADD COLUMN IF NOT EXISTS revision_pago BIGINT;
ALTER TABLE ordenes_legales ADD COLUMN IF NOT EXISTS estado_pago VARCHAR(255);
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_orden_pago_id' AND conrelid = 'ordenes_legales'::regclass
    ) THEN
        ALTER TABLE ordenes_legales ADD CONSTRAINT uk_orden_pago_id UNIQUE (pago_id);
    END IF;
END;
$$;
COMMIT;
