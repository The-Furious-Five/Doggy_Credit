package com.doggycredit.shared.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * RF-SEG-05.1 / ADR-009: clave maestra que envuelve la DEK por tenant.
 * La app se niega a arrancar en producción con la clave de demo.
 */
@ConfigurationProperties(prefix = "doggycredit.encryption")
public class MasterKeyProperties {

    static final String DEMO_KEY = "dev-only-insecure-master-key-do-not-use-in-prod";

    private String masterKey;
    private final Environment env;

    @Autowired
    public MasterKeyProperties(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void validate() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "DC_MASTER_KEY / doggycredit.encryption.master-key no está configurada.");
        }
        if (DEMO_KEY.equals(masterKey) && isProduction()) {
            throw new IllegalStateException(
                    "La clave maestra de demo no puede usarse en producción. "
                    + "Genere una clave segura y configúrela en DC_MASTER_KEY.");
        }
    }

    private boolean isProduction() {
        for (String profile : env.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }
}
