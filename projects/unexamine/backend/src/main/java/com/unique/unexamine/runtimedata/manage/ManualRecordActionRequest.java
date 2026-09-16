package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 手动动作对单条记录的输入。字段编码来自已发布动作配置，页面不会要求用户输入编码或 JSON。
 */
public record ManualRecordActionRequest(
        @NotNull @Min(0) Integer version,
        @NotNull @Size(max = 20) Map<String, JsonNode> values) {
}
