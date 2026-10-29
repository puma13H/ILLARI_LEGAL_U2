-- Aplicar en la BD de Órdenes y en la BD de Pagos (una sola vez si aún comparten BD).
-- Solo crea/añade datos y columnas. Los payloads y tablas existentes se conservan.
BEGIN;
DO $$
DECLARE tabla TEXT; prefijo TEXT; huella TEXT;
BEGIN
    FOREACH prefijo IN ARRAY ARRAY['orden','pago'] LOOP
        tabla := prefijo || '_outbox';
        IF to_regclass(tabla) IS NULL THEN CONTINUE; END IF;
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS event_id VARCHAR(255)',tabla);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS intentos INTEGER NOT NULL DEFAULT 0',tabla);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS proximo_intento TIMESTAMPTZ',tabla);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS fallido BOOLEAN NOT NULL DEFAULT FALSE',tabla);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS ultimo_error VARCHAR(255)',tabla);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS ultimo_operador VARCHAR(255)',tabla);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS motivo_reintento VARCHAR(500)',tabla);
        EXECUTE format('UPDATE %I SET event_id = COALESCE(payload::jsonb->>''eventId'', '
            || '(substr(md5(%L||id::text),1,8)||''-''||substr(md5(%L||id::text),9,4)||''-''||'
            || 'substr(md5(%L||id::text),13,4)||''-''||substr(md5(%L||id::text),17,4)||''-''||substr(md5(%L||id::text),21,12))) '
            || 'WHERE event_id IS NULL',tabla,tabla,tabla,tabla,tabla,tabla);
        EXECUTE format('UPDATE %I SET payload=jsonb_set(payload::jsonb,''{eventId}'',to_jsonb(event_id),true)::text '
            || 'WHERE NOT (payload::jsonb ? ''eventId'')',tabla);
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uk_'||tabla||'_event' AND conrelid=tabla::regclass) THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE(event_id)',tabla,'uk_'||tabla||'_event');
        END IF;
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (proximo_intento,id) WHERE publicado=FALSE AND fallido=FALSE',prefijo||'_outbox_listo',tabla);
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I (event_id VARCHAR(36) PRIMARY KEY, huella VARCHAR(64) NOT NULL, procesado TIMESTAMPTZ NOT NULL)',prefijo||'_inbox');
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I (
            id VARCHAR(36) PRIMARY KEY, topic VARCHAR(255) NOT NULL, particion INTEGER NOT NULL,
            offset_original BIGINT NOT NULL, payload TEXT NOT NULL, error_tipo VARCHAR(255),
            estado VARCHAR(255) NOT NULL, creado TIMESTAMPTZ, payload_replay TEXT,
            operador VARCHAR(255), motivo VARCHAR(500), solicitado TIMESTAMPTZ,
            proximo_intento TIMESTAMPTZ, intentos INTEGER NOT NULL DEFAULT 0, reprocesos INTEGER NOT NULL DEFAULT 0
        )',prefijo||'_dead_letter');
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I (
            id BIGSERIAL PRIMARY KEY, tipo VARCHAR(255), referencia VARCHAR(255), operador VARCHAR(255),
            motivo VARCHAR(500), payload TEXT, fecha TIMESTAMPTZ
        )',prefijo||'_replay_auditoria');
    END LOOP;
END;
$$;
COMMIT;
