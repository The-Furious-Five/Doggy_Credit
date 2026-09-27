package com.doggycredit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * FF-01 / FF-02: verifica que ningún módulo dependa de {@code internal/} de
 * otro, y que no haya ciclos entre módulos. Corre en cada build (mvn test o
 * mvn verify); un import indebido rompe este test, no una convención de
 * palabra. Ver el capítulo 2.2 de la guía de inducción del Sprint 1.
 */
class ApplicationModulesTest {

	private static final ApplicationModules modules = ApplicationModules.of(DoggycreditApiApplication.class);

	@Test
	void verificaLasFronterasDeModulos() {
		modules.verify();
	}

	@Test
	void generaLaDocumentacionC4EnDocsSlashC4() {
		// Spring Modulith genera diagramas C4/UML de los módulos a partir del
		// código real, no de un diagrama dibujado a mano que se desactualiza.
		new Documenter(modules)
				.writeModulesAsPlantUml()
				.writeIndividualModulesAsPlantUml();
	}
}
