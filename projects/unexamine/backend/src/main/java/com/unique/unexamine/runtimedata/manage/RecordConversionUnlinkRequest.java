package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 解除转化关系：同时校验来源数据与转化关系版本，原因进入不可删除的操作历史。 */
public record RecordConversionUnlinkRequest(
        @NotNull @Min(1) Long conversionId,
        @NotNull @Min(0) Integer version,
        @NotNull @Min(0) Integer conversionVersion,
        @NotBlank @Size(max = 1000) String reason) {
}
