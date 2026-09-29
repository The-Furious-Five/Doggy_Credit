package com.doggycredit.tenancy;

import java.util.Objects;
import java.util.UUID;

/**
 * HAB-03: identificador tipado de tenant. Evita confundir un UUID cualquiera
 * con un tenant_id. Los módulos que filtran por tenant reciben este tipo,
 * no un {@link UUID} crudo.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenant_id");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
