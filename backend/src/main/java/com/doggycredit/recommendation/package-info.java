/**
 * Catálogo de productos del tenant (microcrédito, verde, PYME), elegibilidad y ranking (RF-REC-*).
 * <p>
 * Ver DoggyCredit — Visión, Requerimientos y Arquitectura v1, §10.2, y el
 * capítulo 9 de la guía de inducción del Sprint 1 para el dueño y las
 * tareas concretas de este módulo. Nadie importa una clase de otro módulo
 * fuera de su paquete raíz o de `internal/` sin pasar por su API pública
 * (verificado por {@link org.springframework.modulith.core.ApplicationModules#verify()}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Recommendation")
package com.doggycredit.recommendation;
