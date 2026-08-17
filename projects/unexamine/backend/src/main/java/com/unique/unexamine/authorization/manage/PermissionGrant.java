package com.unique.unexamine.authorization.manage;

import java.util.List;

public record PermissionGrant(
        String resourceType,
        String resourceCode,
        String actionCode,
        List<Long> roleIds) {
    public String key() {
        return resourceType + ":" + resourceCode + ":" + actionCode;
    }
}
