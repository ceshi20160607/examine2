package com.unique.unexamine.runtimedata.manage;

/** 已授权记录的来源字段描述；展示文本只从保护后的值解析，不用于写入或还原原始值。 */
public record RuntimeFieldPresentation(String name, String fieldType, String displayValue, Long fieldId, Integer sortOrder) {
}
