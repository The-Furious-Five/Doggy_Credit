package com.doggycredit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de arquitectura (HAB-02) sobre el código de producción. Las fronteras
 * entre módulos y los ciclos ya los verifica {@link ApplicationModulesTest}
 * (Spring Modulith); aquí van solo las reglas que Modulith no cubre.
 * {@code allowEmptyShould}: el esqueleto aún no tiene campos ni controladores;
 * las reglas empiezan a morder en cuanto aparecen.
 */
@AnalyzeClasses(packages = "com.doggycredit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

	/** Inyección por constructor: dependencias explícitas y clases testeables sin Spring. */
	@ArchTest
	static final ArchRule sinInyeccionPorCampo = NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true);

	/** Los logs van por SLF4J, para que lleguen a la observabilidad (Grafana LGTM). */
	@ArchTest
	static final ArchRule sinJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.allowEmptyShould(true);

	/** Nada de System.out/err ni printStackTrace: se pierden fuera de los logs estructurados. */
	@ArchTest
	static final ArchRule sinSalidaEstandar = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.allowEmptyShould(true);

	/**
	 * Los controladores no tocan repositorios JPA directo: pasan por la API del
	 * módulo, que es donde se aplican tenant y reglas de negocio.
	 */
	@ArchTest
	static final ArchRule controladoresSinRepositorios = noClasses()
			.that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
			.should().dependOnClassesThat().areAssignableTo("org.springframework.data.repository.Repository")
			.allowEmptyShould(true);
}
