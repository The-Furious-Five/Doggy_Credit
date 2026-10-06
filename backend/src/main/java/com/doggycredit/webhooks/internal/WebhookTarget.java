package com.doggycredit.webhooks.internal;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Revalidado antes de cada envío; sin credenciales en URL ni red privada en producción. */
@Component
class WebhookTarget {
    private final boolean allowPrivate;

    WebhookTarget(@Value("${doggycredit.webhooks.allow-private-addresses:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    void validate(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.toString().length() > 2048 || uri.getPort() == 0 || uri.getPort() > 65535
                || !("https".equalsIgnoreCase(uri.getScheme())
                    || allowPrivate && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Usa una URL HTTPS absoluta, sin credenciales ni fragmentos.");
        }
        if (allowPrivate) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                byte[] bytes = address.getAddress();
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
                    throw new IllegalArgumentException("La URL debe apuntar a un receptor público.");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("No se pudo resolver el dominio del receptor.");
        }
    }
}
