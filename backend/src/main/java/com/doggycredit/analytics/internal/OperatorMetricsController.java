package com.doggycredit.analytics.internal;

import java.time.LocalDate;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.OperadorApi;
import com.doggycredit.shared.contract.model.MetricasOperador;

/** D-14 (RF-ANA-04): {@code GET /operador/metricas}, solo PLATFORM_ADMIN. */
@RestController
class OperatorMetricsController implements OperadorApi {

    private final OperatorMetricsService metrics;

    OperatorMetricsController(OperatorMetricsService metrics) {
        this.metrics = metrics;
    }

    @Override
    public ResponseEntity<MetricasOperador> obtenerMetricasOperador(LocalDate desde, LocalDate hasta) {
        RoleGuard.require("PLATFORM_ADMIN");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metrics.summarize(desde, hasta));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail invalidDate() {
        return ErrorCode.VALIDATION_ERROR.toProblemDetail("Desde y hasta deben ser fechas válidas con formato AAAA-MM-DD.");
    }
}
