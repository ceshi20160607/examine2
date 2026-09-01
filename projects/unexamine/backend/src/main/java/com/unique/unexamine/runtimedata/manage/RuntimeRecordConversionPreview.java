package com.unique.unexamine.runtimedata.manage;

import java.util.List;

public record RuntimeRecordConversionPreview(
        Long sourceRecordId,
        Integer sourceVersion,
        String targetModuleCode,
        String targetModuleName,
        String targetTitle,
        List<RuntimeRecordConversionMapping> mappings,
        List<String> issues,
        boolean alreadyConverted,
        boolean executable) {
}
