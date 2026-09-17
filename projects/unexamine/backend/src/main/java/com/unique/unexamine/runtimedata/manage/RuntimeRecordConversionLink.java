package com.unique.unexamine.runtimedata.manage;

import java.time.LocalDateTime;

/** 转化历史保留来源、目标及当前状态；链接仍受对应记录的详情权限约束。 */
public record RuntimeRecordConversionLink(
        Long conversionId,
        String direction,
        String moduleCode,
        String moduleName,
        Long recordId,
        String recordTitle,
        String recordStatus,
        Integer conversionVersion,
        String relationStatus,
        boolean canUnlink,
        LocalDateTime convertedAt) {
}
