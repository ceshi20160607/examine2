package com.unique.unexamine.runtimedata.manage;

import java.time.LocalDateTime;

public record RuntimeRecordConversionLink(
        Long conversionId,
        String direction,
        String moduleCode,
        String moduleName,
        Long recordId,
        String recordTitle,
        LocalDateTime convertedAt) {
}
