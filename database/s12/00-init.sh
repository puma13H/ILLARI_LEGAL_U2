#!/bin/sh
# Invoked ONLY by the official PostgreSQL image when PGDATA is empty.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
\getenv clients_pwd CLIENTES_DB_PASSWORD
\getenv services_pwd SERVICIOS_DB_PASSWORD
\getenv orders_pwd ORDENES_DB_PASSWORD
\getenv payments_pwd PAGOS_DB_PASSWORD
\getenv legacy_pwd LEGACY_DB_PASSWORD
\getenv keycloak_pwd KEYCLOAK_DB_PASSWORD
CREATE ROLE illari_clientes LOGIN PASSWORD :'clients_pwd';
CREATE ROLE illari_servicios LOGIN PASSWORD :'services_pwd';
CREATE ROLE illari_ordenes LOGIN PASSWORD :'orders_pwd';
CREATE ROLE illari_pagos LOGIN PASSWORD :'payments_pwd';
CREATE ROLE illari_juridico LOGIN PASSWORD :'legacy_pwd';
CREATE ROLE illari_keycloak LOGIN PASSWORD :'keycloak_pwd';
CREATE DATABASE illari_clientes OWNER illari_clientes;
CREATE DATABASE illari_servicios OWNER illari_servicios;
CREATE DATABASE illari_ordenes OWNER illari_ordenes;
CREATE DATABASE illari_pagos OWNER illari_pagos;
CREATE DATABASE illari_juridico OWNER illari_juridico;
CREATE DATABASE illari_keycloak OWNER illari_keycloak;
REVOKE CONNECT ON DATABASE illari_clientes FROM PUBLIC;
REVOKE CONNECT ON DATABASE illari_servicios FROM PUBLIC;
REVOKE CONNECT ON DATABASE illari_ordenes FROM PUBLIC;
REVOKE CONNECT ON DATABASE illari_pagos FROM PUBLIC;
REVOKE CONNECT ON DATABASE illari_juridico FROM PUBLIC;
REVOKE CONNECT ON DATABASE illari_keycloak FROM PUBLIC;
SQL
# Run as each owner, so the runtime role owns only its schema, tables and sequences.
apply() {
    db="$1"; owner="$2"; file="$3"
    PGOPTIONS="-c role=$owner" psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" -f "$file"
}
apply illari_clientes illari_clientes /migrations/s12/clientes-base.sql
apply illari_servicios illari_servicios /migrations/s12/servicios-base.sql
apply illari_ordenes illari_ordenes /migrations/s12/ordenes-base.sql
apply illari_ordenes illari_ordenes /migrations/04-fase1-saga.sql
apply illari_ordenes illari_ordenes /migrations/05-fase2-compras-detalle.sql
apply illari_ordenes illari_ordenes /migrations/06-fase2-mensajeria.sql
apply illari_pagos illari_pagos /migrations/s12/pagos-base.sql
apply illari_pagos illari_pagos /migrations/06-fase2-mensajeria.sql
apply illari_juridico illari_juridico /migrations/01-schema.sql
apply illari_juridico illari_juridico /migrations/07-fase3-identidad-juridica.sql
PGOPTIONS="-c role=illari_juridico" psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname illari_juridico <<'SQL'
INSERT INTO roles(name,display_name) VALUES ('admin','Administrador'),('abogado','Abogado'),('cliente','Cliente');
SQL

