package com.doggycredit.assessment.internal;

import java.util.List;

import org.springframework.boot.jackson.JacksonMixin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.doggycredit.shared.contract.model.ElegibilidadProducto;
import com.doggycredit.shared.contract.model.Evaluacion;
import com.doggycredit.shared.contract.model.Recomendacion;

/** Respeta «campo ausente» del contrato sin modificar los modelos generados. [] se conserva. */
@JacksonMixin(Evaluacion.class)
abstract class AssessmentProductsJsonMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract List<ElegibilidadProducto> getElegibilidad();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract List<Recomendacion> getRecomendaciones();
}

/** Los snapshots de C-06 no inventan detalles añadidos por E-16. */
@JacksonMixin(Recomendacion.class)
abstract class RecommendationDetailsJsonMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract Integer getPlazoSugeridoMeses();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract List<String> getRazones();
}
