package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.moduleconfig.manage.StructuredRuleEvaluator;
import com.unique.unexamine.runtimedata.base.entity.BizRecordIndex;
import com.unique.unexamine.runtimedata.base.service.BizRecordIndexBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class RuntimeRuleIndexService {
    private final StructuredRuleEvaluator evaluator;
    private final BizRecordIndexBaseService recordIndexService;
    private final ObjectMapper objectMapper;

    public RuntimeRuleIndexService(
            StructuredRuleEvaluator evaluator,
            BizRecordIndexBaseService recordIndexService,
            ObjectMapper objectMapper) {
        this.evaluator = evaluator;
        this.recordIndexService = recordIndexService;
        this.objectMapper = objectMapper;
    }

    public void validate(RuntimeModuleConfiguration configuration, String event, Map<String, JsonNode> fields) {
        JsonNode sample = objectMapper.valueToTree(fields);
        for (JsonNode rule : configuration.configuration().path("rules")) {
            String trigger = rule.path("triggerEvent").asText();
            if (!("ALWAYS".equals(trigger) || event.equals(trigger))) continue;
            try {
                JsonNode definition = objectMapper.readTree(rule.path("expressionText").asText("{}"));
                var result = evaluator.evaluate(definition, sample, rule.path("messageTemplate").asText(null));
                if (!result.matched()) continue;
                String effect = result.effectType();
                String target = result.targetField();
                if ("BLOCK".equals(effect)) reject(rule, result.message());
                if (("REQUIRE_FIELD".equals(effect) || "REQUIRE_APPROVAL".equals(effect))
                        && (target == null || missing(fields.get(target)))) {
                    reject(rule, result.message() == null ? "命中规则后字段 " + target + " 必填" : result.message());
                }
            } catch (DomainException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new DomainException("PUBLISHED_RULE_INVALID", "已发布规则无法执行", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public void syncIndexes(
            AuthenticatedContext context,
            RuntimeModuleConfiguration configuration,
            Long recordId,
            Map<String, JsonNode> values) {
        recordIndexService.selectList(Wrappers.<BizRecordIndex>lambdaQuery()
                        .eq(BizRecordIndex::getRecordId, recordId))
                .forEach(index -> recordIndexService.deleteById(index.getId()));
        JsonNode fields = configuration.configuration().path("queryIndexFields");
        for (JsonNode index : configuration.configuration().path("queryIndexes")) {
            long indexId = index.path("id").asLong();
            List<JsonNode> definitions = new ArrayList<>();
            fields.forEach(field -> {
                if (field.path("queryIndexId").asLong() == indexId) definitions.add(field);
            });
            definitions.sort(Comparator.comparingInt(field -> field.path("sortOrder").asInt()));
            if (definitions.isEmpty()) continue;
            List<String> parts = new ArrayList<>();
            boolean missing = false;
            for (JsonNode definition : definitions) {
                long fieldId = definition.path("fieldId").asLong();
                String fieldCode = fieldCode(configuration.configuration().path("fields"), fieldId);
                JsonNode value = values.get(fieldCode);
                if (missing(value)) missing = true;
                parts.add(fieldCode + "=" + canonical(value));
            }
            String keyText = String.join("\u001F", parts);
            String hash = sha256(keyText);
            BizRecordIndex projection = new BizRecordIndex();
            projection.setSystemId(context.systemId());
            projection.setTenantId(context.tenantId());
            projection.setModuleId(configuration.moduleId());
            projection.setRecordId(recordId);
            projection.setQueryIndexId(indexId);
            projection.setIndexKeyHash(hash);
            projection.setUniqueKeyHash(index.path("uniqueIndex").asBoolean(false) && !missing ? hash : null);
            projection.setIndexKeyText(keyText);
            try {
                recordIndexService.insert(projection);
            } catch (DataIntegrityViolationException exception) {
                throw new DomainException("UNIQUE_RULE_VIOLATION",
                        "唯一规则 “" + index.path("name").asText(index.path("code").asText()) + "” 已存在相同值",
                        HttpStatus.CONFLICT);
            }
        }
    }

    public void validateCreatePreview(
            AuthenticatedContext context,
            RuntimeModuleConfiguration configuration,
            Map<String, JsonNode> values) {
        validate(configuration, "CREATE", values);
        JsonNode fields = configuration.configuration().path("queryIndexFields");
        for (JsonNode index : configuration.configuration().path("queryIndexes")) {
            if (!index.path("uniqueIndex").asBoolean(false)) continue;
            long indexId = index.path("id").asLong();
            List<JsonNode> definitions = new ArrayList<>();
            fields.forEach(field -> {
                if (field.path("queryIndexId").asLong() == indexId) definitions.add(field);
            });
            definitions.sort(Comparator.comparingInt(field -> field.path("sortOrder").asInt()));
            if (definitions.isEmpty()) continue;
            List<String> parts = new ArrayList<>();
            boolean missing = false;
            for (JsonNode definition : definitions) {
                String fieldCode = fieldCode(configuration.configuration().path("fields"),
                        definition.path("fieldId").asLong());
                JsonNode value = values.get(fieldCode);
                if (missing(value)) missing = true;
                parts.add(fieldCode + "=" + canonical(value));
            }
            if (missing) continue;
            String hash = sha256(String.join("\u001F", parts));
            boolean occupied = !recordIndexService.selectList(Wrappers.<BizRecordIndex>lambdaQuery()
                    .eq(BizRecordIndex::getTenantId, context.tenantId())
                    .eq(BizRecordIndex::getModuleId, configuration.moduleId())
                    .eq(BizRecordIndex::getQueryIndexId, indexId)
                    .eq(BizRecordIndex::getUniqueKeyHash, hash)).isEmpty();
            if (occupied) {
                throw new DomainException("UNIQUE_RULE_VIOLATION",
                        "唯一规则 “" + index.path("name").asText(index.path("code").asText()) + "” 已存在相同值",
                        HttpStatus.CONFLICT);
            }
        }
    }

    public void clearIndexes(Long recordId) {
        recordIndexService.selectList(Wrappers.<BizRecordIndex>lambdaQuery()
                        .eq(BizRecordIndex::getRecordId, recordId))
                .forEach(index -> recordIndexService.deleteById(index.getId()));
    }

    private void reject(JsonNode rule, String message) {
        throw new DomainException("BUSINESS_RULE_REJECTED",
                message == null || message.isBlank() ? "业务规则 “" + rule.path("name").asText() + "” 未通过" : message,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private String fieldCode(JsonNode fields, long fieldId) {
        for (JsonNode field : fields) if (field.path("id").asLong() == fieldId) return field.path("code").asText();
        throw new DomainException("PUBLISHED_INDEX_INVALID", "已发布索引引用失效字段", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean missing(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() || (value.isTextual() && value.asText().isBlank());
    }

    private String canonical(JsonNode value) {
        if (missing(value)) return "<NULL>";
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot canonicalize index value", exception); }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
