package com.unique.examine.module.datasource.external.jdbc;

import java.net.IDN;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact host and port policy evaluated before any secret resolution. */
final class JdbcTableTargetPolicy {
    private static final Pattern DNS_NAME = Pattern.compile(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final Set<Target> allowed;

    JdbcTableTargetPolicy(JdbcTableDataSourceProperties properties) {
        Objects.requireNonNull(properties, "properties");
        var parsed = new LinkedHashSet<Target>();
        properties.allowedTargets().forEach(value -> {
            var target = parse(value);
            if (target != null) {
                parsed.add(target);
            }
        });
        allowed = Set.copyOf(parsed);
    }

    Target requireAllowed(String host, int port) {
        var canonical = canonicalHost(host);
        var target = canonical == null || port < 1 || port > 65_535
                ? null : new Target(canonical, port);
        if (target == null || !allowed.contains(target)) {
            throw new UnsafeTargetException();
        }
        return target;
    }

    private static Target parse(String value) {
        var delimiter = value.lastIndexOf(':');
        if (delimiter < 1 || delimiter == value.length() - 1
                || value.indexOf(':') != delimiter) {
            return null;
        }
        var host = canonicalHost(value.substring(0, delimiter));
        try {
            var port = Integer.parseInt(value.substring(delimiter + 1));
            return host == null || port < 1 || port > 65_535
                    ? null : new Target(host, port);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String canonicalHost(String value) {
        if (value == null || value.isBlank()
                || !value.equals(value.strip())) {
            return null;
        }
        try {
            var ascii = IDN.toASCII(
                    value, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            return DNS_NAME.matcher(ascii).matches() ? ascii : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    record Target(String host, int port) {
    }

    static final class UnsafeTargetException extends IllegalArgumentException {
        private UnsafeTargetException() {
            super("JDBC table target is not permitted");
        }
    }
}
