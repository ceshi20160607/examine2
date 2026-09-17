package com.unique.unexamine.runtimedata.manage;

/** 解除结果仅包含关系状态；来源、目标正文及原转化成功事实均不改变。 */
public record RuntimeRecordConversionUnlink(
        Long conversionId, Integer conversionVersion, String relationStatus) {
}
