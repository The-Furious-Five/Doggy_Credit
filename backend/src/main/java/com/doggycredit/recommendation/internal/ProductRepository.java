package com.doggycredit.recommendation.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** El tenant lo filtran @TenantId y la RLS: nunca se pasa como parámetro. */
interface ProductRepository extends JpaRepository<Product, UUID> {

	List<Product> findAllByOrderByProductTypeAsc();

}
