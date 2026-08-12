package com.unique.examine.plat.vnext.manage.registration;

public record RegistrationReceipt(
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        long roleId,
        long dataScopeId,
        long contextSessionId,
        long refreshTokenId
) {
    public RegistrationReceipt withSession(long contextSessionId, long refreshTokenId) {
        return new RegistrationReceipt(
                accountId, systemId, tenantId, memberId, roleId, dataScopeId,
                contextSessionId, refreshTokenId
        );
    }
}
