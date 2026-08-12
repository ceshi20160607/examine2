package com.unique.examine.openapi.security;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** OpenAPI-owned callback preflight; delivery transport rechecks/pins DNS. */
public final class OpenApiCallbackTargetPolicy {
    private final AddressResolver resolver;

    public OpenApiCallbackTargetPolicy() {
        this(InetAddress::getAllByName);
    }

    OpenApiCallbackTargetPolicy(AddressResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public URI requireSafe(String value) {
        if (value == null || value.isBlank() || value.length() > 1024) throw unsafe();
        final URI uri;
        try { uri = URI.create(value.strip()); } catch (RuntimeException malformed) { throw unsafe(); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() < -1 || uri.getPort() > 65535
                || uri.getRawQuery() != null) throw unsafe();
        try { IDN.toASCII(uri.getHost()); } catch (RuntimeException malformed) { throw unsafe(); }
        requirePublicResolution(uri);
        return uri;
    }

    public void requirePublicResolution(URI uri) {
        final InetAddress[] addresses;
        try { addresses = resolver.resolve(uri.getHost()); }
        catch (Exception unresolved) { throw unsafe(); }
        if (addresses == null || addresses.length == 0) throw unsafe();
        Arrays.stream(addresses).forEach(OpenApiCallbackTargetPolicy::requirePublic);
    }

    private static void requirePublic(InetAddress address) {
        var bytes = address.getAddress();
        var uniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        var carrierGradeNat = bytes.length == 4 && (bytes[0] & 0xff) == 100
                && ((bytes[1] & 0xff) >= 64 && (bytes[1] & 0xff) <= 127);
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress() || uniqueLocal || carrierGradeNat) throw unsafe();
    }

    public static String canonicalHost(URI uri) {
        return IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
    }

    private static UnsafeCallbackTargetException unsafe() {
        return new UnsafeCallbackTargetException("OpenAPI callback target is not permitted");
    }

    @FunctionalInterface
    interface AddressResolver { InetAddress[] resolve(String host) throws Exception; }

    public static final class UnsafeCallbackTargetException extends IllegalArgumentException {
        UnsafeCallbackTargetException(String message) { super(message); }
    }
}
