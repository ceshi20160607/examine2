package com.unique.examine.plat.vnext.manage.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.plat.vnext.manage.auth.VNextAuthTokenService;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
final class RegistrationCommandFactory {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{2,31}$");
    private static final Pattern SYSTEM_CODE = Pattern.compile("^[a-z][a-z0-9_]{2,31}$");

    private final VNextAuthTokenService tokenService;
    private final ObjectMapper objectMapper;

    RegistrationCommandFactory(VNextAuthTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    RegistrationCommand create(
            String username, String displayName, String password,
            String systemName, String systemCode, String idempotencyKey
    ) {
        var canonicalKey = canonicalUuid(idempotencyKey);
        var canonicalUsername = normalize(username);
        if (canonicalUsername == null || !USERNAME.matcher(canonicalUsername).matches()) {
            throw RegistrationErrors.invalid("username", "Username format is invalid");
        }
        var usernameNormalized = canonicalUsername.toLowerCase(Locale.ROOT);
        var canonicalDisplayName = trimmed(displayName);
        if (canonicalDisplayName == null || canonicalDisplayName.length() > 120) {
            throw RegistrationErrors.invalid("displayName", "Display name must contain 1 to 120 characters");
        }
        if (password == null || password.length() < 10 || password.length() > 200) {
            throw RegistrationErrors.invalid("password", "Password must contain 10 to 200 characters");
        }
        var canonicalSystemName = trimmed(systemName);
        if (canonicalSystemName == null || canonicalSystemName.length() > 160) {
            throw RegistrationErrors.invalid("systemName", "System name must contain 1 to 160 characters");
        }
        var canonicalSystemCode = trimmed(systemCode);
        if (canonicalSystemCode == null || !SYSTEM_CODE.matcher(canonicalSystemCode).matches()) {
            throw RegistrationErrors.invalid("systemCode", "System code format is invalid");
        }
        var hash = tokenService.hash(canonicalJson(
                usernameNormalized, canonicalDisplayName, canonicalSystemName, canonicalSystemCode
        ));
        return new RegistrationCommand(
                canonicalUsername, usernameNormalized, canonicalDisplayName, password,
                canonicalSystemName, canonicalSystemCode, canonicalKey, hash
        );
    }

    private String canonicalJson(
            String usernameNormalized, String displayName, String systemName, String systemCode
    ) {
        var canonical = new LinkedHashMap<String, String>();
        canonical.put("version", "p1-register-v1");
        canonical.put("usernameNormalized", usernameNormalized);
        canonical.put("displayName", displayName);
        canonical.put("systemName", systemName);
        canonical.put("systemCode", systemCode);
        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (JsonProcessingException exception) {
            throw RegistrationErrors.failed();
        }
    }

    private static String canonicalUuid(String value) {
        if (value == null || value.isBlank()) {
            throw RegistrationErrors.invalid("Idempotency-Key", "Idempotency-Key is required");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw RegistrationErrors.invalid("Idempotency-Key", "Idempotency-Key must be a canonical UUID");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw RegistrationErrors.invalid("Idempotency-Key", "Idempotency-Key must be a canonical UUID");
        }
    }

    private static String normalize(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        var result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
