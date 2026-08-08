package com.unique.examine.module.datasource.external.http;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Feature allowlist applied before the shared hardened transport performs its
 * final DNS, address, peer and TLS checks.
 */
final class HttpDataSourceHostPolicy {
    private static final Pattern DNS_NAME = Pattern.compile(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final List<Rule> rules;

    HttpDataSourceHostPolicy(HttpDataSourceCheckProperties properties) {
        Objects.requireNonNull(properties, "properties");
        var parsed = new ArrayList<Rule>();
        properties.allowedHosts().forEach(value -> {
            var rule = rule(value);
            if (rule != null) {
                parsed.add(rule);
            }
        });
        rules = List.copyOf(parsed);
    }

    URI requireAllowed(String endpoint) {
        if (endpoint == null
                || endpoint.isBlank()
                || endpoint.length() > 1_024) {
            throw new UnsafeTargetException();
        }
        final URI uri;
        try {
            uri = URI.create(endpoint.strip());
        } catch (RuntimeException invalid) {
            throw new UnsafeTargetException();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getRawQuery() != null
                || uri.getPort() == 0
                || uri.getPort() < -1
                || uri.getPort() > 65_535) {
            throw new UnsafeTargetException();
        }
        var host = canonicalHost(uri.getHost());
        if (host == null || rules.stream().noneMatch(rule -> rule.matches(host))) {
            throw new UnsafeTargetException();
        }
        return uri;
    }

    private static Rule rule(String value) {
        var normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("*.")) {
            var suffix = canonicalHost(normalized.substring(2));
            if (suffix == null || suffix.indexOf('.') < 1) {
                return null;
            }
            return new WildcardRule(suffix);
        }
        if (normalized.indexOf('*') >= 0) {
            return null;
        }
        var exact = canonicalHost(normalized);
        return exact == null ? null : new ExactRule(exact);
    }

    private static String canonicalHost(String value) {
        try {
            var ascii = IDN.toASCII(
                    value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return DNS_NAME.matcher(ascii).matches() ? ascii : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private sealed interface Rule permits ExactRule, WildcardRule {
        boolean matches(String host);
    }

    private record ExactRule(String host) implements Rule {
        @Override
        public boolean matches(String candidate) {
            return host.equals(candidate);
        }
    }

    private record WildcardRule(String suffix) implements Rule {
        @Override
        public boolean matches(String host) {
            return host.length() > suffix.length() + 1
                    && host.endsWith("." + suffix);
        }
    }

    static final class UnsafeTargetException extends IllegalArgumentException {
        private UnsafeTargetException() {
            super("HTTP data-source target is not permitted");
        }
    }
}
