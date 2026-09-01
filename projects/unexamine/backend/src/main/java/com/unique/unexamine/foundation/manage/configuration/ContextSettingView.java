package com.unique.unexamine.foundation.manage.configuration;

import java.time.LocalDateTime;

public record ContextSettingView(
        Long id,
        String contextType,
        Long platformId,
        Long systemId,
        String category,
        String settingKey,
        String valueType,
        Object value,
        boolean sensitive,
        String status,
        Integer version,
        LocalDateTime updatedAt) {
}
