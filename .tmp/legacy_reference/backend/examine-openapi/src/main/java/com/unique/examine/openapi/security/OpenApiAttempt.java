package com.unique.examine.openapi.security;

public final class OpenApiAttempt {
    private Long applicationId;
    private Integer credentialVersion;

    public Long applicationId() {
        return applicationId;
    }

    public Integer credentialVersion() {
        return credentialVersion;
    }

    void identified(long applicationId, int credentialVersion) {
        this.applicationId = applicationId;
        this.credentialVersion = credentialVersion;
    }
}
