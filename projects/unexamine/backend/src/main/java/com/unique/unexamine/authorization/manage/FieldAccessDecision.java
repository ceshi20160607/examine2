package com.unique.unexamine.authorization.manage;

import java.util.List;

public record FieldAccessDecision(
        String fieldCode,
        String channel,
        boolean readable,
        boolean writable,
        String maskStrategy,
        List<Long> contributingRoleIds,
        String reason) {
}
