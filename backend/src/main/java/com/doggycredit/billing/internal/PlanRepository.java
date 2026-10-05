package com.doggycredit.billing.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlanRepository extends JpaRepository<Plan, String> {
}
