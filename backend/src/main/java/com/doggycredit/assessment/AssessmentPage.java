package com.doggycredit.assessment;

import java.util.List;

/** C-09: una página de evaluaciones y el total que cumple el filtro. */
public record AssessmentPage(List<AssessmentSummary> items, long total) {
}
