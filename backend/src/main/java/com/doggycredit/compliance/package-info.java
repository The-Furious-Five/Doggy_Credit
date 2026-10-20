/**
 * Cumplimiento: reporte de accesos a los datos de un titular (RF-CON-03, C-11).
 * <p>
 * Es un módulo propio porque junta tres: lee la bitácora ({@code audit}), los
 * consentimientos ({@code consent}) y los solicitantes ({@code customer}). Ponerlo en
 * cualquiera de ellos crearía un ciclo entre módulos (customer y consent ya dependen de
 * audit, y customer depende de consent). Nadie depende de este módulo. Solo usa la API
 * pública de los demás (verificado por
 * {@link org.springframework.modulith.core.ApplicationModules#verify()}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Compliance")
package com.doggycredit.compliance;
