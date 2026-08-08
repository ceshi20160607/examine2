package com.unique.examine.openapi.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public final class IpAllowlist {
    private IpAllowlist() {
    }

    public static boolean allows(List<String> entries, String observedIp) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        final byte[] observed;
        try {
            observed = literal(observedIp).getAddress();
        } catch (IllegalArgumentException exception) {
            return false;
        }
        for (var entry : entries) {
            try {
                if (matches(entry, observed)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Stored invalid policy fails closed.
            }
        }
        return false;
    }

    public static List<String> validate(List<String> entries) {
        var values = List.copyOf(entries);
        for (var value : values) {
            if (value == null || value.isBlank() || value.length() > 80) {
                throw new IllegalArgumentException("OpenAPI IP allowlist entry is invalid");
            }
            var separator = value.indexOf('/');
            var address = separator < 0 ? value : value.substring(0, separator);
            var bytes = literal(address).getAddress();
            if (separator >= 0) {
                var prefix = prefix(value.substring(separator + 1));
                if (prefix < 0 || prefix > bytes.length * 8) {
                    throw new IllegalArgumentException("OpenAPI CIDR prefix is invalid");
                }
            }
        }
        return values.stream().distinct().sorted().toList();
    }

    private static boolean matches(String entry, byte[] observed) {
        var separator = entry.indexOf('/');
        var address = separator < 0 ? entry : entry.substring(0, separator);
        var network = literal(address).getAddress();
        if (network.length != observed.length) {
            return false;
        }
        var prefix = separator < 0 ? network.length * 8 : prefix(entry.substring(separator + 1));
        if (prefix < 0 || prefix > network.length * 8) {
            return false;
        }
        var wholeBytes = prefix / 8;
        var remaining = prefix % 8;
        for (var index = 0; index < wholeBytes; index++) {
            if (network[index] != observed[index]) {
                return false;
            }
        }
        if (remaining == 0) {
            return true;
        }
        var mask = 0xff << (8 - remaining);
        return (network[wholeBytes] & mask) == (observed[wholeBytes] & mask);
    }

    private static int prefix(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("OpenAPI CIDR prefix is invalid", exception);
        }
    }

    private static InetAddress literal(String value) {
        if (value == null || value.isBlank()
                || !(value.matches("^[0-9.]+$") || value.matches("^[0-9A-Fa-f:.]+$"))) {
            throw new IllegalArgumentException("OpenAPI IP address is invalid");
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("OpenAPI IP address is invalid", exception);
        }
    }
}
