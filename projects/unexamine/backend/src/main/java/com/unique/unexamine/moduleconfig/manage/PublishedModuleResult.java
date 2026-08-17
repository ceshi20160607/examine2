package com.unique.unexamine.moduleconfig.manage;

import java.time.LocalDateTime;

public record PublishedModuleResult(
        Long moduleId,
        Long versionId,
        Integer versionNumber,
        Integer draftRevision,
        LocalDateTime publishedAt) {
}
