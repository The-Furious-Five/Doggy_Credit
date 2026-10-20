package com.doggycredit.tenancy.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** D-14: solo inserción; el tenant lo aplican @TenantId y la RLS. */
interface PlanChangeRepository extends JpaRepository<PlanChange, UUID> {
}
