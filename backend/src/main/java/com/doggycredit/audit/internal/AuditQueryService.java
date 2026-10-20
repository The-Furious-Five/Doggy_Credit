package com.doggycredit.audit.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.audit.AccessLog;

/** C-04 (LOS-84): lectura paginada de la bitácora del tenant actual; C-11: reporte de accesos. */
@Service
class AuditQueryService implements AccessLog {

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

	@Override
	@Transactional(readOnly = true)
	public AccessLog.Page find(UUID applicantId, String correlationId, Instant from, Instant to, Long before,
			int size) {
		if (applicantId == null && correlationId == null) {
			throw new IllegalArgumentException("El reporte de accesos necesita un titular o un correlation-id");
		}
		Instant start = from == null ? Instant.EPOCH : from;
		Instant end = to == null ? OPEN_END : to;
		long cursor = before == null ? Long.MAX_VALUE : before;
		Limit limit = Limit.of(size + 1);
		List<AuditEvent> rows;
		if (applicantId != null && correlationId != null) {
			rows = events.findByApplicantAndCorrelation(applicantId, correlationId, start, end, cursor, limit);
		}
		else if (applicantId != null) {
			rows = events.findByApplicant(applicantId, start, end, cursor, limit);
		}
		else {
			rows = events.findByCorrelation(correlationId, start, end, cursor, limit);
		}
		boolean hasMore = rows.size() > size;
		List<AuditEvent> page = hasMore ? rows.subList(0, size) : rows;
		return new AccessLog.Page(page.stream().map(AuditQueryService::toEntry).toList(), hasMore);
	}

	private static AccessLog.Entry toEntry(AuditEvent event) {
		return new AccessLog.Entry(event.id(), event.chainSeq(), event.actor(), event.action(), event.resourceType(),
				event.resourceId(), event.correlationId(), event.createdAt(), event.rowHash(), event.applicantId(),
				event.consentId());
	}

}
