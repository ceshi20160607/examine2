package com.unique.examine.web.vnext.registration;

public record RegisterRequest(
        String username,
        String displayName,
        String password,
        String systemName,
        String systemCode
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username
                + ", displayName=" + displayName
                + ", password=<redacted>, systemName=" + systemName
                + ", systemCode=" + systemCode + "]";
    }
}
