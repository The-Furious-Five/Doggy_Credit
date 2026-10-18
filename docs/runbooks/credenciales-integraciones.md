# Credenciales propias por proveedor · E-15 / RF-INT-07

Un `TENANT_ADMIN` puede administrar claves desde **Integraciones → Credenciales
propias**. Se ofrece en buró, core bancario y billetera; el diálogo del banco
permite elegir Banco o Segundo banco. El segundo banco es un proveedor alternativo
de `BANCO`, seleccionado por `DC_INTEGRATION_BANK_PROVIDER=dos`; registrar su clave
no cambia ese selector ni agrega una integración al plan.

La clave API admite entre 8 y 2048 caracteres ASCII visibles, sin espacios ni
saltos de línea. El formulario nunca precarga el secreto y lo limpia al enviarlo,
cerrar o cambiar de proveedor. La API devuelve solo `configuradas` y
`ultimosCuatro` (vacío si no hay clave propia). Para rotar, guardar la clave nueva;
para volver a DoggyCredit, eliminar las credenciales propias.

## Contrato y restricciones

El fragmento RF-INT-07 de A-15 queda implementado en
`contracts/openapi/doggycredit-api.v1.yaml`, con tag propio
`credenciales-integraciones` y clientes Java/Angular generados. Los demás puntos
de A-15 conservan su alcance y seguimiento independientes.

`GET`, `PUT` y `DELETE /integraciones/{fuente}/credenciales` admiten `BURO`,
`BANCO`, `BANCO_DOS`, `WALLET`. Solo `TENANT_ADMIN`, sin restricción de plan para
guardar credenciales. El tenant sale del JWT; un header o campo del cuerpo no
permite elegir otro. `DELETE` es idempotente. Guardar una clave no activa fuentes,
no amplía cuotas y no sustituye el consentimiento exigido por `ConnectorGateway`.

## Persistencia y resolución

La migración `V2026_10_18_1800` crea `integration.tenant_credentials`, con
`FORCE ROW LEVEL SECURITY`, política por `app.tenant_id`, unicidad tenant/proveedor
y permisos para `doggycredit_app`. `secret_ciphertext` usa `FieldEncryption`
(AES-256-GCM, IV aleatorio y DEK derivada por tenant desde `DC_MASTER_KEY`).
`last_four` es el único fragmento legible. No cambiar la master key sin migrar
antes los datos cifrados: una clave existente que no se puede descifrar falla;
no se usa la credencial global silenciosamente.

Cada consulta resuelve la clave del tenant y proveedor; si falta, usa la variable
`DC_INTEGRATION_{BUREAU,BANK,BANK_DOS,WALLET}_API_KEY`. Por defecto están vacías,
conservando el acceso sin autenticación de los simuladores actuales. Las claves
se envían solo en `X-API-Key`, nunca en URL. No se cachean: la siguiente consulta
resuelve el cambio confirmado más reciente.

En workers de evaluación sin contexto HTTP, la lectura instala el tenant ya
validado antes de abrir una transacción nueva de solo lectura y restaura el
contexto anterior al terminar, también ante errores. La lectura y el descifrado
terminan antes de llamar por HTTP; la conexión no queda ocupada durante la E/S.

Los cambios y su auditoría se confirman en la misma transacción:
`INTEGRATION_CREDENTIALS_SAVED` / `INTEGRATION_CREDENTIALS_DELETED`, con tipo
`INTEGRATION_CREDENTIALS_<proveedor>`, ID del recurso, actor y correlación.
Nunca incluyen claves. Compose ejecuta WireMock sin `--verbose`, con
`--disable-request-logging` y `--no-request-journal`: tampoco deja las claves en
logs de peticiones ni en el historial de su API de diagnóstico. No habilitar
trazas de headers al probar credenciales.

## Validación

`IntegrationCredentialsTests` comprueba API real, roles, cifrado crudo, DEK de otro
tenant, RLS con el rol de aplicación y sin tenant, rotación, eliminación y rollback
con auditoría. `ConnectorCredentialsTests` exige que WireMock reciba la clave
correcta para los cuatro proveedores, incluso desde workers sin contexto, y
verifica el fallback de otro tenant y tras eliminar. Ambas clases capturan logs
para comprobar que no incluyen las claves de prueba.

Las pruebas del diálogo cubren cliente generado, validación, errores, duplicados,
limpieza del secreto, cambio de banco, cancelación y eliminación. La página
comprueba que las acciones se ofrecen únicamente al administrador en las fuentes
con implementación.
