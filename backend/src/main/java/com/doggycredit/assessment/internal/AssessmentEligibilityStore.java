package com.doggycredit.assessment.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.doggycredit.recommendation.ProductEligibility;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Snapshot inmutable bajo RLS; no consulta el catálogo al leer una evaluación histórica. */
@Repository
class AssessmentEligibilityStore {

    private static final TypeReference<List<ProductEligibility>> TYPE = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    AssessmentEligibilityStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    void save(UUID tenantId, UUID assessmentId, List<ProductEligibility> products) {
        jdbc.update("""
                insert into assessment.eligibility_snapshots (id, tenant_id, assessment_id, products)
                values (?, ?, ?, ?::jsonb)
                """, assessmentId, tenantId, assessmentId, mapper.writeValueAsString(products));
    }

    /** null = evaluación anterior a E-16; [] = catálogo activo vacío al evaluar. */
    List<ProductEligibility> load(UUID tenantId, UUID assessmentId) {
        return jdbc.query("""
                select products from assessment.eligibility_snapshots
                 where tenant_id = ? and assessment_id = ?
                """, (rs, row) -> mapper.readValue(rs.getString("products"), TYPE), tenantId, assessmentId)
                .stream().findFirst().orElse(null);
    }
}
