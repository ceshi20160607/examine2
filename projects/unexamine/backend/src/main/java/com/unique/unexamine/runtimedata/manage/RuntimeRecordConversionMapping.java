package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;

/** 发布字段的名称和映射校验结果，页面无需展示内部字段编号。 */
public record RuntimeRecordConversionMapping(
        String targetFieldCode,
        String targetFieldName,
        boolean required,
        String sourceFieldCode,
        String sourceFieldName,
        JsonNode value,
        String status,
        String message) {
}
