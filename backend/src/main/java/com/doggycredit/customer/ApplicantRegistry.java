package com.doggycredit.customer;

import java.util.UUID;

public interface ApplicantRegistry {

	ApplicantProfile register(UUID tenantId, ApplicantRegistration registration);

	ApplicantProfile findRequired(UUID tenantId, String document);

}
