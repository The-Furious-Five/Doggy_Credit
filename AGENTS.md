# DoggyCredit — reglas para desarrollo de interfaces

Antes de crear o modificar una interfaz, leer `docs/design-system/README.md` y
consultar `web/src/app/design-system/index.ts`. Esta regla aplica a todas las
pantallas presentes y futuras del sistema.

- Usar el Design System y su API pública. No crear controles visuales paralelos.
- Consumir tokens semánticos. Los estilos de una pantalla solo componen layout
  y presentación a partir de esos tokens.
- Mantener componentes genéricos en design-system y negocio fuera de él.
- Si falta una capacidad, extender el sistema, documentar el componente en el
  catálogo y agregar pruebas de comportamiento pertinentes.
- Conservar navegación por teclado, nombres accesibles, estados, temas y
  preferencias de movimiento reducido.
- No deshabilitar el checker, modificar exclusiones o mover una pantalla al
  directorio del sistema para eludir las reglas.
- Ejecutar `npm run ds:check`, `npm run ds:test`, las pruebas afectadas y
  `npm run build` desde `web/` antes de entregar cambios de interfaz.
- No editar `web/src/app/api/`: se genera desde OpenAPI.

Estas reglas no autorizan cambios en APIs ni datos de negocio que no formen
parte de la tarea. La guía del sprint y sus responsables siguen vigentes.
