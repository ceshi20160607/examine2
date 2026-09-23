package com.unique.unexamine.runtimedata.manage;

/** 可引用记录的最小选择信息，不携带其业务字段或来源租户目录。 */
public record RuntimeReferenceOption(Long value, String label, String description) {
}
