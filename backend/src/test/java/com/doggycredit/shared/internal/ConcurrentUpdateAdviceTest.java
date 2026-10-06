package com.doggycredit.shared.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** Un choque de @Version responde 409 CONCURRENT_UPDATE (RFC 9457), no 500. */
class ConcurrentUpdateAdviceTest {

	@RestController
	static class Clash {
		@PutMapping("/registro")
		void update() {
			throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
		}
	}

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Clash())
		.setControllerAdvice(new ConcurrentUpdateAdvice())
		.build();

	@Test
	void unChoqueDeVersionResponde409ConSuCodigo() throws Exception {
		mvc.perform(put("/registro"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"))
			.andExpect(jsonPath("$.type").value("https://docs.doggycredit.example/errors/concurrent-update"));
	}

}
