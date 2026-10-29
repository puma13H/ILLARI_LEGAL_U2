CREATE TABLE clientes_distribuidos (
 id BIGSERIAL PRIMARY KEY,dni VARCHAR(255),nombres VARCHAR(255),apellidos VARCHAR(255),email VARCHAR(255),
 telefono VARCHAR(255),direccion VARCHAR(255),estado VARCHAR(255),keycloak_sub VARCHAR(255) UNIQUE
);

