package com.doggycredit.audit.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * C-04 (LOS-84): verifica la cadena de hash de un tenant con audit.verify_chain(),
 * que recalcula cada eslabón con la misma función que usó el trigger al insertar.
 * Lista vacía = cadena íntegra. Lo usará el ancla periódica (cabezas al log cada 15 min,
 * informe de arquitectura §12).
 */
@Component
class AuditChainVerifier {

	/** Un eslabón roto: la fila alterada (HASH_MISMATCH) o el enlace cortado (BROKEN_LINK). */
	record Break(long chainSeq, String problem) {
	}

	private final JdbcTemplate jdbc;

	AuditChainVerifier(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<Break> verify(UUID tenantId) {
		return jdbc.query("SELECT chain_seq, problem FROM audit.verify_chain(?)",
				(rs, row) -> new Break(rs.getLong("chain_seq"), rs.getString("problem")), tenantId);
	}

}
