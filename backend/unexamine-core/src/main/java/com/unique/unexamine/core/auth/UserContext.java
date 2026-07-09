package com.unique.unexamine.core.auth;

import java.util.List;

public record UserContext(
        String scope,
        String systemId,
        String systemName,
        String accountMemberBindingId,
        List<String> effectiveRoles
) {
}