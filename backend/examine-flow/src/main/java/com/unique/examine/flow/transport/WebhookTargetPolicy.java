package com.unique.examine.flow.transport;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Canonical URL and DNS policy shared by definition preflight and the
 * production transport.
 */
public final class WebhookTargetPolicy {
    public static final int MAXIMUM_URL_LENGTH = 1024;

    private final AddressResolver resolver;

    public WebhookTargetPolicy() {
        this(InetAddress::getAllByName);
    }

    WebhookTargetPolicy(AddressResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public URI requireSafe(String value) {
        if (value == null
                || value.isBlank()
                || value.length() > MAXIMUM_URL_LENGTH) {
            throw unsafe("Webhook URL must contain 1 to 1024 characters");
        }
        final URI uri;
        try {
            uri = URI.create(value.strip());
        } catch (RuntimeException failure) {
            throw unsafe("Webhook URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0
                || uri.getPort() < -1
                || uri.getRawQuery() != null
                        && uri.getRawQuery().length() > 2048) {
            throw unsafe(
                    "Webhook target must be an HTTPS URL without credentials "
                            + "or fragments");
        }
        if (uri.getPort() > 65535) {
            throw unsafe("Webhook target port is invalid");
        }
        try {
            IDN.toASCII(uri.getHost());
        } catch (RuntimeException failure) {
            throw unsafe("Webhook target host is invalid");
        }
        resolveSafe(uri);
        return uri;
    }

    public List<InetAddress> resolveSafe(URI uri) {
        Objects.requireNonNull(uri, "uri");
        final InetAddress[] addresses;
        try {
            addresses = resolver.resolve(uri.getHost());
        } catch (Exception failure) {
            throw unsafe("Webhook target host cannot be resolved");
        }
        if (addresses == null || addresses.length == 0) {
            throw unsafe("Webhook target host cannot be resolved");
        }
        Arrays.stream(addresses).forEach(this::requirePublic);
        return List.copyOf(Arrays.asList(addresses));
    }

    public void requirePublic(InetAddress address) {
        Objects.requireNonNull(address, "address");
        var bytes = address.getAddress();
        var uniqueLocal = bytes.length == 16
                && (bytes[0] & 0xfe) == 0xfc;
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || uniqueLocal) {
            throw unsafe("Webhook target resolves to a non-public address");
        }
    }

    public static boolean maskedSecret(String value) {
        return value != null
                && "********".equals(value.strip());
    }

    public static String canonicalHost(URI uri) {
        return IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
    }

    private static UnsafeWebhookTargetException unsafe(String message) {
        return new UnsafeWebhookTargetException(message);
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws Exception;
    }

    public static final class UnsafeWebhookTargetException
            extends IllegalArgumentException {
        UnsafeWebhookTargetException(String message) {
            super(message);
        }
    }
}
