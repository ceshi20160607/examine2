package com.unique.unexamine.core.auth;

import java.util.List;

public record UserProfile(
        String accountId,
        String username,
        String displayName,
        List<String> platformRoles
) {
}