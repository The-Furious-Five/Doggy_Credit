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
import com.doggycredit.shared.contract.api.AnalyticsApi;
import com.doggycredit.shared.contract.model.AnalyticsResumen;

@RestController
class AnalyticsController implements AnalyticsApi {

    private final AnalyticsSummaryService summaries;

    AnalyticsController(AnalyticsSummaryService summaries) {
        this.summaries = summaries;
    }

    @Override
    public ResponseEntity<AnalyticsResumen> obtenerResumenAnalytics(LocalDate desde, LocalDate hasta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(summaries.summarize(desde, hasta));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail invalidDate() {
        return ErrorCode.VALIDATION_ERROR.toProblemDetail("Desde y hasta deben ser fechas válidas con formato AAAA-MM-DD.");
    }
}
