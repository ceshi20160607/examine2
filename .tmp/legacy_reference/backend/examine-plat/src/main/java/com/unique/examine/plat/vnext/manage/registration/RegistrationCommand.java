package com.unique.examine.plat.vnext.manage.registration;

public record RegistrationCommand(
        String username,
        String usernameNormalized,
        String displayName,
        String password,
        String systemName,
        String systemCode,
        String idempotencyKey,
        String requestHash
) {
    @Override
    public String toString() {
        return "RegistrationCommand[username=" + username
                + ", usernameNormalized=" + usernameNormalized
                + ", displayName=" + displayName
                + ", password=<redacted>, systemName=" + systemName
                + ", systemCode=" + systemCode
                + ", idempotencyKey=" + idempotencyKey
                + ", requestHash=" + requestHash + "]";
    }
}
