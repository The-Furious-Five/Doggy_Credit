package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.recommendation.CreditNeeds;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.recommendation.CreditNeeds.NeedInput;
import com.doggycredit.recommendation.NeedCategory;

/** D-06: la API pública del módulo, con las tasas y plazos de application.yml (todas las necesidades). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreditNeedsConfigurationTests {

	@Autowired
	CreditNeeds needs;

	@Test
	void cadaNecesidadTieneTasaYPlazoConfigurados() {
		for (String destination : List.of("capital de trabajo", "maquinaria", "paneles solares", "consolidar deudas")) {
			CreditNeeds.CreditNeed need = needs.analyze(new NeedInput(HolderType.PERSONA, destination, null,
					new BigDecimal("4000"), List.of(new BigDecimal("3500")), new BigDecimal("400"), null, 1, null, null));
			assertThat(need.suggestedAmount()).isPositive();
			assertThat(need.suggestedTermMonths()).isPositive();
		}
		assertThat(needs.analyze(new NeedInput(HolderType.PERSONA, "capital de trabajo", null, new BigDecimal("4000"),
				List.of(new BigDecimal("3500")), new BigDecimal("400"), null, 1, new BigDecimal("15000"), 12))
			.suggestedAmount()).isEqualByComparingTo("11284.76");
		assertThat(NeedCategory.values()).hasSize(4);
	}

}
