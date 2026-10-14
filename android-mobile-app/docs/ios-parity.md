# Paridad funcional iOS → Android

| iOS | Android nativo | API / comportamiento |
| --- | --- | --- |
| Acceso institucional | AppAuth + Custom Tabs + callback nativo | Mismo realm y cliente público, PKCE S256 |
| Sesión y renovación | Android Keystore + Mutex | Tokens cifrados, una renovación y un reintento tras 401 |
| Inicio | Tarjetas Material, FAB y actualización por gesto | Tenant, uso, evaluaciones y revisión manual |
| Alertas del plan | Aviso al 80 % y al agotar cuota | Límites y consumo de `/tenant/actual/uso` |
| Buscar por CI/NIT | Campo con acción Buscar del teclado | Documento en `X-Documento`, nunca en la URL |
| Cartera de clientes | Listado, chips de segmento y carga de páginas | `/solicitantes` con `pagina`, `tamano`, `segmento` |
| Perfil 360 | Pantalla de detalle nativa | `/solicitantes/{id}`, campos ausentes como desconocidos |
| Fuentes del perfil | Tarjetas con estado, fecha y hash | Datos reales de la respuesta `perfil.fuentes` |
| Historial | Tarjetas y paginación | `/credit-assessments?solicitanteId=…` |
| Consentimientos | Estados, vencimiento y diálogo de revocación | GET y POST de `/consentimientos` |
| Nueva evaluación | Formulario en pantalla completa con cuatro pasos | Alta/reutilización, consentimiento, fuentes y evaluación |
| Consentimiento explícito | Casillas independientes sin selección inicial | BURO obligatorio, BANCO/WALLET opcionales |
| Borrador retomable | ViewModel y barra Retomar | Conserva datos durante la sesión y rotación |
| Reintentos | Idempotency-Key estable por petición | No duplica al reintentar el mismo cuerpo |
| Resultado | Score accesible, banda, razones y capacidad | Resultado del servidor, sin cálculo local de score |
| Trazabilidad | Versión, correlación y detalle de fuentes | Conserva los identificadores de la API |
| Datos simulados | Indicador visible según respuesta | Respeta `simulado`, incluido su default conservador |
| Bandeja de revisión | Lista con chips, deslizar y menú contextual | `/revision-manual`, páginas y productos disponibles |
| Aprobar/rechazar | Pantalla de resolución | Producto al aprobar y justificación de 10–1000 caracteres |
| Cambio simultáneo | Aviso y actualización de bandeja | CONCURRENT_UPDATE / ASSESSMENT_NOT_FOUND |
| Producto desactivado | Recarga y nueva selección | PRODUCT_NOT_FOUND |
| Analítica por período | Chips y barras Material accesibles | Fechas inclusivas en America/La_Paz |
| Analítica avanzada | Métricas, productos y monitoreo | Bloque Pro solo cuando lo devuelve la API |
| Cuenta y privacidad | Pantalla de ajustes y enmascarado | Tenant, plan, consumo y preferencias del servidor |
| Face ID / Touch ID / código | BiometricPrompt / bloqueo del dispositivo | Opción local de bloqueo al volver a la app |
| Portada de privacidad | Portada al pausar + FLAG_SECURE | Protege información de clientes en segundo plano |
| Consola web | Intent de navegador | Administración en `/app`, planes en `/app/configuracion` |
| Cierre de sesión | Diálogo nativo y limpieza de sesión | Borra tokens/borrador, revoca refresh token en Keycloak |

No se incluyó un modo de demostración: iOS ya lo había eliminado. Las fixtures
quedan exclusivamente en los targets de pruebas. No se modificó el backend ni
se sustituyeron sus simuladores financieros por proveedores externos.
