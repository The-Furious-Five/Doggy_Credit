package com.doggycredit.customer;

import java.time.Instant;
import java.util.UUID;

import com.doggycredit.shared.Money;

/** Vista pública del solicitante; el documento se descifra solo al salir del módulo. */
public record ApplicantProfile(UUID id, String document, String activity, int activitySeniorityMonths,
		Money monthlyIncome, String creditDestination, Instant createdAt, ApplicantSegment segment) {
}
