package com.unique.unexamine.runtimedata.manage;

public record RuntimeRecordConversionExecution(
        Long conversionId,
        String targetModuleCode,
        RuntimeRecordView targetRecord) {
}
