package com.unique.examine.web.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Fails production startup before serving traffic when infrastructure security
 * has silently fallen back to a local-development posture.
 */
@Component
public final class ProductionSecurityGate implements InitializingBean {
    static final long MAX_KEY_RING_BYTES = 64L * 1024L;

    private final Environment environment;

    public ProductionSecurityGate(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (isProduction(environment)) validate(environment);
    }

    static boolean isProduction(Environment environment) {
        if ("PRODUCTION".equalsIgnoreCase(
                environment.getProperty("examine.security.deployment-mode", "LOCAL").strip())) {
            return true;
        }
        for (var profile : environment.getActiveProfiles()) {
            if ("production".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) return true;
        }
        return false;
    }

    static void validate(Environment environment) {
        validateDatabase(environment);
        requireTrue(environment, "spring.data.redis.ssl.enabled", "Production Redis TLS is required");
        requireText(environment, "spring.data.redis.password", "Production Redis authentication is required");
        requireTrue(environment, "examine.security.secure-cookies", "Production secure cookies are required");
        validateFileStorage(environment);
        validateMail(environment);
        validateKeyRing(environment);
    }

    private static void validateDatabase(Environment environment) {
        var url = requireText(environment, "spring.datasource.url", "Production database URL is required")
                .toLowerCase(Locale.ROOT);
        if (!url.contains("sslmode=verify_identity")
                || url.contains("allowpublickeyretrieval=true")
                || url.contains("usessl=false")) {
            throw new IllegalStateException(
                    "Production database must use sslMode=VERIFY_IDENTITY and disable public-key retrieval");
        }
        requireText(environment, "spring.datasource.username", "Production database username is required");
        requireText(environment, "spring.datasource.password", "Production database password is required");
    }

    private static void validateFileStorage(Environment environment) {
        var mode = environment.getProperty("examine.file.storage.mode", "LOCAL").strip();
        if ("S3".equalsIgnoreCase(mode)) {
            var endpoint = requireText(environment, "examine.file.storage.s3.endpoint",
                    "Production S3 endpoint is required");
            final URI uri;
            try {
                uri = URI.create(endpoint);
            } catch (IllegalArgumentException failure) {
                throw new IllegalStateException("Production S3 endpoint is invalid", failure);
            }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException("Production S3 endpoint must use HTTPS");
            }
            requireText(environment, "examine.file.storage.s3.access-key",
                    "Production S3 access key is required");
            requireText(environment, "examine.file.storage.s3.secret-key",
                    "Production S3 secret key is required");
            return;
        }
        if (!"LOCAL".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Production file storage mode is invalid");
        }
        var root = absolutePath(environment, "examine.file.storage.local.root",
                "Production local file root must be absolute");
        if (Files.isSymbolicLink(root)) {
            throw new IllegalStateException("Production local file root must not be a symbolic link");
        }
    }

    private static void validateMail(Environment environment) {
        if (environment.getProperty("examine.event.account-recovery-mail.enabled", Boolean.class, false)) {
            requireText(environment, "examine.event.account-recovery-mail.host",
                    "Production account-recovery SMTP host is required");
            requireText(environment, "examine.event.account-recovery-mail.from",
                    "Production account-recovery SMTP sender is required");
            requireTrue(environment, "examine.event.account-recovery-mail.start-tls",
                    "Production account-recovery SMTP STARTTLS is required");
            requireTrue(environment, "examine.event.account-recovery-mail.smtp-auth",
                    "Production account-recovery SMTP authentication is required");
            requireText(environment, "examine.event.account-recovery-mail.username",
                    "Production account-recovery SMTP username is required");
            requireText(environment, "examine.event.account-recovery-mail.password",
                    "Production account-recovery SMTP password is required");
            requireHttps(environment, "examine.event.account-recovery-mail.public-base-url",
                    "Production account-recovery public base URL must use HTTPS");
        }
        if (environment.getProperty("examine.event.delivery.smtp.enabled", Boolean.class, false)) {
            requireText(environment, "examine.event.delivery.smtp.host",
                    "Production business SMTP host is required");
            requireText(environment, "examine.event.delivery.smtp.from",
                    "Production business SMTP sender is required");
            requireTrue(environment, "examine.event.delivery.smtp.start-tls",
                    "Production business SMTP STARTTLS is required");
            requireTrue(environment, "examine.event.delivery.smtp.start-tls-required",
                    "Production business SMTP must require STARTTLS");
            if (environment.getProperty("examine.event.delivery.smtp.authentication", Boolean.class, true)) {
                requireText(environment, "examine.event.delivery.smtp.username-secret-ref",
                        "Production business SMTP username SecretRef is required");
                requireText(environment, "examine.event.delivery.smtp.password-secret-ref",
                        "Production business SMTP password SecretRef is required");
            }
        }
    }

    private static void validateKeyRing(Environment environment) {
        var approvedRoot = absolutePath(environment, "examine.runtime.sensitive.key-ring-root",
                "Production sensitive key-ring root must be absolute");
        var keyRing = absolutePath(environment, "examine.runtime.sensitive.key-ring-file",
                "Production sensitive key-ring file must be absolute");
        try {
            var realRoot = approvedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(keyRing)
                    || !Files.isRegularFile(keyRing, LinkOption.NOFOLLOW_LINKS)
                    || !keyRing.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(realRoot)
                    || Files.size(keyRing) == 0
                    || Files.size(keyRing) > MAX_KEY_RING_BYTES) {
                throw new IllegalStateException("Production sensitive key-ring file is unsafe");
            }
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Production sensitive key-ring file is unavailable", failure);
        }
    }

    private static Path absolutePath(Environment environment, String key, String message) {
        var value = requireText(environment, key, message);
        final Path path;
        try {
            path = Path.of(value).normalize();
        } catch (RuntimeException failure) {
            throw new IllegalStateException(message, failure);
        }
        if (!path.isAbsolute()) throw new IllegalStateException(message);
        return path;
    }

    private static void requireHttps(Environment environment, String key, String message) {
        var value = requireText(environment, key, message);
        try {
            var uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException(message);
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException(message, failure);
        }
    }

    private static void requireTrue(Environment environment, String key, String message) {
        if (!environment.getProperty(key, Boolean.class, false)) {
            throw new IllegalStateException(message);
        }
    }

    private static String requireText(Environment environment, String key, String message) {
        var value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalStateException(message);
        }
        return value.strip();
    }
}
