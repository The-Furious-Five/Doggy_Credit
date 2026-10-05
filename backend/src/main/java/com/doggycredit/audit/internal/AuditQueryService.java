package com.doggycredit.audit.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** C-04 (LOS-84): lectura paginada de la bitácora del tenant actual. */
@Service
class AuditQueryService {

	/** Límite superior abierto para "hasta" (timestamptz admite fechas mucho mayores). */
	private static final Instant OPEN_END = Instant.parse("9999-12-31T23:59:59Z");

	/** Una página y si hay más después de ella. */
	record Page(List<AuditEvent> items, boolean hasMore) {
	}

	private final AuditEventRepository events;

	AuditQueryService(AuditEventRepository events) {
		this.events = events;
	}

	/**
	 * De la más reciente a la más antigua. {@code from}/{@code to}/{@code before} nulos
	 * significan "sin límite". Se pide un registro de más para saber si hay página
	 * siguiente sin contar toda la tabla.
	 */
	@Transactional(readOnly = true)
	Page page(Instant from, Instant to, Long before, int size) {
		List<AuditEvent> rows = events.findPage(from == null ? Instant.EPOCH : from, to == null ? OPEN_END : to,
				before == null ? Long.MAX_VALUE : before, Limit.of(size + 1));
		boolean hasMore = rows.size() > size;
		return new Page(hasMore ? List.copyOf(rows.subList(0, size)) : rows, hasMore);
	}

}
