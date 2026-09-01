package com.unique.unexamine.moduleconfig.manage;

import java.time.LocalDateTime;

public record PublishedVersionSummary(
        Long versionId,
        Integer versionNumber,
        Integer draftRevision,
        String changeSummary,
        LocalDateTime publishedAt,
        boolean current,
        Integer publicationVersion) {
}
