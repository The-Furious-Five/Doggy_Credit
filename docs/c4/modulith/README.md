# Diagramas de Spring Modulith

Pendiente de exportar. `backend/target/spring-modulith-docs/` no existe en este
árbol y no se pudo generar en la máquina donde se preparó LOS-142: el JDK instalado
falla al iniciar la JVM (`NoClassDefFoundError: java/lang/Object`) y no hay `mvn`
en el PATH, así que `./mvnw test` no arranca.

Para generarlos y congelarlos (JDK 21 funcional, desde la raíz del repo):

```bash
cd backend
./mvnw -Dtest=ApplicationModulesTest test
cp target/spring-modulith-docs/*.puml ../docs/c4/modulith/
```

`ApplicationModulesTest` llama a `Documenter.writeModulesAsPlantUml()` y
`writeIndividualModulesAsPlantUml()`, y no necesita base de datos. Los `.puml`
resultantes son el resumen C4 de componentes y un diagrama por módulo.
