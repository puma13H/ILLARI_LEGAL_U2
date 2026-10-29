-- Casos Legales - PostgreSQL
-- psql -U postgres -d casos_legales -f 01-schema.sql

BEGIN;

CREATE TABLE IF NOT EXISTS roles (
                                     id           BIGSERIAL PRIMARY KEY,
                                     name         VARCHAR(50)  NOT NULL UNIQUE,
                                     display_name VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS users (
                                     id            BIGSERIAL PRIMARY KEY,
                                     name          VARCHAR(255) NOT NULL,
                                     email         VARCHAR(255) NOT NULL UNIQUE,
                                     password      VARCHAR(255) NOT NULL,
                                     role_id       BIGINT NOT NULL REFERENCES roles(id),
                                     profile_photo VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS clientes (
                                        id        BIGSERIAL PRIMARY KEY,
                                        nombre    VARCHAR(255) NOT NULL,
                                        telefono  VARCHAR(50)  NOT NULL,
                                        email     VARCHAR(255) NOT NULL UNIQUE,
                                        direccion TEXT,
                                        user_id   BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS abogados (
                                        id           BIGSERIAL PRIMARY KEY,
                                        nombre       VARCHAR(255) NOT NULL,
                                        email        VARCHAR(255) NOT NULL UNIQUE,
                                        telefono     VARCHAR(50)  NOT NULL,
                                        especialidad VARCHAR(255) NOT NULL,
                                        direccion    TEXT,
                                        user_id      BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS casos (
                                     id              BIGSERIAL PRIMARY KEY,
                                     nombre_caso     VARCHAR(255) NOT NULL,
                                     cliente_id      BIGINT NOT NULL REFERENCES clientes(id),
                                     abogado_id      BIGINT NOT NULL REFERENCES abogados(id),
                                     estado          VARCHAR(100) NOT NULL DEFAULT 'Iniciado',
                                     fecha_apertura  DATE NOT NULL,
                                     descripcion     TEXT,
                                     tarifa          NUMERIC(12,2) DEFAULT 0,
                                     fecha_audiencia DATE
);

CREATE TABLE IF NOT EXISTS documentos (
                                          id             BIGSERIAL PRIMARY KEY,
                                          nombre         VARCHAR(255) NOT NULL,
                                          descripcion    TEXT,
                                          ruta_archivo   VARCHAR(500) NOT NULL,
                                          tipo_archivo   VARCHAR(50)  NOT NULL,
                                          tamano_archivo BIGINT       NOT NULL,
                                          caso_id        BIGINT REFERENCES casos(id) ON DELETE CASCADE,
                                          user_id        BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS mensajes (
                                        id        BIGSERIAL PRIMARY KEY,
                                        caso_id   BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
                                        user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                        contenido TEXT   NOT NULL
);

CREATE TABLE IF NOT EXISTS agendas (
                                       id           BIGSERIAL PRIMARY KEY,
                                       titulo       VARCHAR(255) NOT NULL,
                                       descripcion  TEXT,
                                       fecha_inicio TIMESTAMP NOT NULL,
                                       fecha_fin    TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS notificaciones (
                                              id      BIGSERIAL PRIMARY KEY,
                                              titulo  VARCHAR(255) NOT NULL,
                                              mensaje TEXT NOT NULL,
                                              leida   BOOLEAN NOT NULL DEFAULT FALSE,
                                              user_id BIGINT REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS settings (
                                        id    BIGSERIAL PRIMARY KEY,
                                        clave VARCHAR(100) NOT NULL UNIQUE,
                                        valor TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_casos_abogado    ON casos(abogado_id);
CREATE INDEX IF NOT EXISTS idx_casos_cliente    ON casos(cliente_id);
CREATE INDEX IF NOT EXISTS idx_mensajes_caso    ON mensajes(caso_id);
CREATE INDEX IF NOT EXISTS idx_documentos_caso  ON documentos(caso_id);
CREATE INDEX IF NOT EXISTS idx_users_role       ON users(role_id);

COMMIT;

