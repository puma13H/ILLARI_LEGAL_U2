# Bases y migraciones S12

La guía vigente es GUIA_INSTALACION.md en la raíz. database/s12/00-init.sh solo se ejecuta automáticamente en un volumen PostgreSQL vacío.

Se conservan01-schema.sql y las migraciones04/05/06/07; no se distribuyen seeds clásicos con cuentas de ejemplo. Órdenes aplica base→04→05→06, Pagos base→06, Jurídico01→07. Después se valida el esquema; no hay migración destructiva automática.

Para bases existentes: inventario, respaldo restaurado, plan y autorización explícita antes de migrar. No ejecutar fixtures de verification/postgres contra información real.
