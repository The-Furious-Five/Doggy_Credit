package com.doggycredit.tenancy.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** El tenant lo aplican @TenantId y la RLS desde el JWT; aquí no se recibe. */
interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

	List<ApiKey> findByRevocadaEnIsNullOrderByCreadaEnDesc();

	Optional<ApiKey> findByIdAndRevocadaEnIsNull(UUID id);

}
