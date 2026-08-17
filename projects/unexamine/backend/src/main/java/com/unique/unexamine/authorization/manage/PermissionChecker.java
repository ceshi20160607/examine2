package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import org.springframework.stereotype.Component;

@Component
public class PermissionChecker {
    public boolean allows(AuthenticatedContext context, String resourceType, String resourceCode, String actionCode) {
        return context.permissions().stream().anyMatch(grant ->
                matches(grant.resourceType(), resourceType)
                        && matches(grant.resourceCode(), resourceCode)
                        && matches(grant.actionCode(), actionCode));
    }

    private boolean matches(String granted, String required) {
        return "*".equals(granted) || granted.equals(required);
    }
}
