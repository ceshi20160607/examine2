package com.unique.unexamine.manage.module;

import com.unique.unexamine.module.base.entity.UnModuleDict;
import com.unique.unexamine.module.base.mapper.UnModuleDictMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** 记录保存与列表查询共用的字段类型、选项和 JSON 值校验。 */
@Component
class ModuleRecordFieldValue {
    private final ModuleSupport support;
    private final UnModuleDictMapper dictionaries;
    private final ObjectMapper json;

    ModuleRecordFieldValue(ModuleSupport support, UnModuleDictMapper dictionaries, ObjectMapper json) {
        this.support = support;
        this.dictionaries = dictionaries;
        this.json = json;
    }

    JsonNode readJson(String value) {
        if (value == null) return null;
        try { return json.readTree(value); }
        catch (Exception e) { bad("场景配置 JSON 无效"); return null; }
    }

    /** 场景列顺序直接决定本次列表表头，隐藏或停用的字段不会重新暴露。 */
    Object comparableValue(ModuleSupport.FieldSpec spec, JsonNode value, long tenantId, long moduleId) {
        if (value == null || value.isNull()) bad("筛选值不能为空");
        return switch (spec.type()) {
            case "text", "textarea", "select" -> {
                if (!value.isTextual()) bad("字段必须是文本：" + spec.name());
                String text = value.asText();
                if (spec.maxLength() != null && text.length() > spec.maxLength()) bad("字段超过长度限制：" + spec.name());
                if ("select".equals(spec.type())) validateOption(spec, text, tenantId, moduleId);
                yield text;
            }
            case "number", "integer" -> {
                BigDecimal number;
                try { number = new BigDecimal(value.asText()); }
                catch (Exception e) { bad("字段必须是数字：" + spec.name()); yield null; }
                if ("integer".equals(spec.type()) && number.stripTrailingZeros().scale() > 0) bad("字段必须是整数：" + spec.name());
                if (spec.precisionScale() != null && number.scale() > spec.precisionScale()) bad("数字小数位超限：" + spec.name());
                if (spec.minNumber() != null && number.compareTo(spec.minNumber()) < 0) bad("数字低于最小值：" + spec.name());
                if (spec.maxNumber() != null && number.compareTo(spec.maxNumber()) > 0) bad("数字高于最大值：" + spec.name());
                yield number;
            }
            case "date", "datetime" -> {
                if (!value.isTextual()) bad("字段必须是日期时间：" + spec.name());
                try { yield "date".equals(spec.type()) ? LocalDate.parse(value.asText()).atStartOfDay() : LocalDateTime.parse(value.asText()); }
                catch (DateTimeParseException e) { bad("日期时间格式无效：" + spec.name()); yield null; }
            }
            case "boolean" -> {
                if (!value.isBoolean()) bad("字段必须是布尔值：" + spec.name());
                yield value.asBoolean() ? "true" : "false";
            }
            case "multiselect" -> {
                if (!value.isArray()) bad("多选字段必须是数组：" + spec.name());
                for (JsonNode item : value) {
                    if (!item.isTextual()) bad("多选选项必须是文本");
                    validateOption(spec, item.asText(), tenantId, moduleId);
                }
                yield writeJson(value);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的字段类型");
        };
    }

    private void validateOption(ModuleSupport.FieldSpec spec, String option, long tenantId, long moduleId) {
        Set<String> allowed = new HashSet<>();
        JsonNode configured = readJson(spec.optionData());
        if (configured != null && configured.isArray()) {
            for (JsonNode item : configured) allowed.add(item.isObject() ? item.path("value").asText() : item.asText());
        }
        if (spec.dictCode() != null) {
            long systemId = support.module(tenantId, moduleId).getSystemId();
            for (UnModuleDict item : dictionaries.selectList(new LambdaQueryWrapper<UnModuleDict>()
                    .eq(UnModuleDict::getSystemId, systemId).in(UnModuleDict::getModuleId, List.of(0L, moduleId))
                    .eq(UnModuleDict::getDictCode, spec.dictCode()).eq(UnModuleDict::getStatus, 1)))
                allowed.add(item.getItemCode());
        }
        if (!allowed.contains(option)) bad("选项不在配置范围内：" + spec.name());
    }

    String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段值 JSON 无效"); }
    }

    String valueColumn(String type) {
        return switch (type) {
            case "number", "integer" -> "value_number";
            case "date", "datetime" -> "value_time";
            case "multiselect" -> "value_json";
            default -> "value_text";
        };
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
