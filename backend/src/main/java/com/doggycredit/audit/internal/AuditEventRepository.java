package com.doggycredit.audit.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

	/**
	 * C-04: una página de la bitácora del tenant actual (lo filtran @TenantId y la RLS),
	 * de la más reciente a la más antigua, por cursor sobre chain_seq. Sin nulos en los
	 * parámetros: quien llama pone los límites abiertos (época, fecha lejana, Long.MAX).
	 */
	@Query("""
			select e from AuditEvent e
			where e.createdAt >= :from and e.createdAt < :to and e.chainSeq < :before
			order by e.chainSeq desc
			""")
	List<AuditEvent> findPage(Instant from, Instant to, long before, Limit limit);

}
