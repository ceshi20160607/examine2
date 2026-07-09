package com.unique.unexamine.core.auth;

import java.util.List;

public record LoginResponse(
        String token,
        UserProfile profile,
        UserContext currentContext,
        List<SystemOption> systems
) {
}