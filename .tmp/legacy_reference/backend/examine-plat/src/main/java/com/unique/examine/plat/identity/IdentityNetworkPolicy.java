package com.unique.examine.plat.identity;

import java.net.InetAddress;
import java.net.URI;

final class IdentityNetworkPolicy {
    private IdentityNetworkPolicy() { }

    static URI https(String raw, String name, boolean allowLoopback) {
        if (raw == null || raw.isBlank()) {
            throw failure("IDENTITY_" + name.toUpperCase(java.util.Locale.ROOT) + "_MISSING");
        }
        try {
            var uri = URI.create(raw.strip());
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getFragment() != null) {
                throw failure("IDENTITY_ENDPOINT_DENIED");
            }
            var loopbackHttp = allowLoopback && "http".equalsIgnoreCase(uri.getScheme())
                    && isLoopback(uri.getHost());
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopbackHttp) {
                throw failure("IDENTITY_ENDPOINT_TLS_REQUIRED");
            }
            if (!allowLoopback) {
                for (var address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                            || address.isMulticastAddress()) {
                        throw failure("IDENTITY_ENDPOINT_PRIVATE_ADDRESS");
                    }
                }
            }
            return uri;
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcEnterpriseIdentityClient.IdentityTransportException(
                    "IDENTITY_ENDPOINT_INVALID", e);
        }
    }

    static URI ldaps(String raw, boolean allowLoopback) {
        if (raw == null || raw.isBlank()) throw failure("IDENTITY_DIRECTORY_MISSING");
        try {
            var uri = URI.create(raw.strip());
            if (uri.getUserInfo() != null || uri.getHost() == null
                    || (!"ldaps".equalsIgnoreCase(uri.getScheme())
                    && !(allowLoopback && "ldap".equalsIgnoreCase(uri.getScheme())
                    && isLoopback(uri.getHost())))) {
                throw failure("IDENTITY_DIRECTORY_TLS_REQUIRED");
            }
            return uri;
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcEnterpriseIdentityClient.IdentityTransportException(
                    "IDENTITY_DIRECTORY_INVALID", e);
        }
    }

    static boolean isLoopback(String host) {
        try { return InetAddress.getByName(host).isLoopbackAddress(); }
        catch (Exception e) { return false; }
    }

    private static OidcEnterpriseIdentityClient.IdentityTransportException failure(String code) {
        return new OidcEnterpriseIdentityClient.IdentityTransportException(code);
    }
}

