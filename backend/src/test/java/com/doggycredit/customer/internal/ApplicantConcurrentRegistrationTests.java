package com.doggycredit.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.customer.ApplicantAlreadyExistsException;
import com.doggycredit.customer.ApplicantRegistration;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.shared.TenantContext;

/**
 * RF-PER-01.1 (LOS-58): dos altas simultáneas del mismo documento. Ambas pueden pasar la
 * comprobación "¿ya existe?" antes de que la otra guarde; la segunda choca con el UNIQUE
 * de la base de datos y debe responder 409 (APPLICANT_ALREADY_EXISTS), no un 500.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApplicantConcurrentRegistrationTests {

	private static final int THREADS = 8;

	@Autowired
	ApplicantRegistry registry;

	@Test
	void altasSimultaneasDelMismoDocumentoDanUnaAltaYElRestoConflicto() throws Exception {
		UUID tenant = UUID.randomUUID();
		String document = "CI-" + UUID.randomUUID().toString().substring(0, 8);
		ApplicantRegistration registration = new ApplicantRegistration(document, "Comercio", 36,
				new BigDecimal("4500.00"), Currency.getInstance("BOB"), "Capital de trabajo");

		AtomicInteger created = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch start = new CountDownLatch(1);

		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			for (int i = 0; i < THREADS; i++) {
				pool.submit(() -> {
					TenantContext.set(tenant);
					try {
						start.await();
						registry.register(tenant, registration);
						created.incrementAndGet();
					}
					catch (ApplicantAlreadyExistsException expected) {
						conflicts.incrementAndGet();
					}
					catch (Throwable other) {
						unexpected.add(other);
					}
					finally {
						TenantContext.clear();
					}
				});
			}
			start.countDown();
		}
		finally {
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(unexpected).as("errores distintos de 409").isEmpty();
		assertThat(created.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(THREADS - 1);
	}

}
