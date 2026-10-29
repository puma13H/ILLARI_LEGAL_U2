-- ADITIVA. Ejecutar únicamente en la base propietaria del backend jurídico.
-- Vinculación administrativa explícita; nunca se infiere una cuenta por su email.
BEGIN;
CREATE TABLE IF NOT EXISTS keycloak_identidades (
    id BIGSERIAL PRIMARY KEY,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    creada TIMESTAMPTZ NOT NULL,
    operador VARCHAR(255) NOT NULL,
    CONSTRAINT uk_identidad_issuer_sub UNIQUE(issuer,subject),
    CONSTRAINT uk_identidad_usuario_issuer UNIQUE(user_id,issuer)
);
COMMIT;

