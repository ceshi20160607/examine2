package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleRule;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndex;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndexField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleRuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.CreateIndexRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.CreateRuleRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.IndexFieldRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.QueryIndexDraft;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.RuleIndexDraft;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.RuleTestResult;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.TestRuleRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.UpdateIndexRequest;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.UpdateRuleRequest;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ModuleRuleConfigurationService {
    private static final Set<String> NON_INDEXABLE_TYPES = Set.of(
            "RICH_TEXT", "JSON", "SECRET", "ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP", "SUBTABLE", "SIGNATURE");

    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final CfgModuleRuleBaseService ruleService;
    private final CfgQueryIndexBaseService indexService;
    private final CfgQueryIndexFieldBaseService indexFieldService;
    private final StructuredRuleEvaluator evaluator;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ModuleRuleConfigurationService(
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            CfgModuleRuleBaseService ruleService,
            CfgQueryIndexBaseService indexService,
            CfgQueryIndexFieldBaseService indexFieldService,
            StructuredRuleEvaluator evaluator,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.ruleService = ruleService;
        this.indexService = indexService;
        this.indexFieldService = indexFieldService;
        this.evaluator = evaluator;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RuleIndexDraft draft(AuthenticatedContext context, Long moduleId) {
        requireModule(context, moduleId);
        List<CfgModuleRule> rules = ruleService.selectList(Wrappers.<CfgModuleRule>lambdaQuery()
                .eq(CfgModuleRule::getModuleId, moduleId).orderByAsc(CfgModuleRule::getSortOrder, CfgModuleRule::getId));
        List<QueryIndexDraft> indexes = indexService.selectList(Wrappers.<CfgQueryIndex>lambdaQuery()
                        .eq(CfgQueryIndex::getModuleId, moduleId).orderByAsc(CfgQueryIndex::getId)).stream()
                .map(this::indexDraft).toList();
        return new RuleIndexDraft(rules, indexes);
    }

    @Transactional
    public CfgModuleRule createRule(
            AuthenticatedContext context, Long moduleId, CreateRuleRequest request, String traceId) {
        ConfiguredModule module = requireModule(context, moduleId);
        validateDefinition(moduleId, request.definition());
        CfgModuleRule rule = new CfgModuleRule();
        rule.setSystemId(context.systemId());
        rule.setOwnerTenantId(context.tenantId());
        rule.setModuleId(moduleId);
        rule.setCode(resolveRuleCode(moduleId, request.code()));
        applyRule(rule, request.name(), request.ruleType(), request.triggerEvent(), request.definition(),
                request.message(), request.sortOrder(), "ACTIVE");
        resetTest(rule);
        rule.setVersion(0);
        ruleService.insert(rule);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_RULE_DRAFT_CREATED", "MODULE_RULE", rule.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "code", rule.getCode(), "ruleType", rule.getRuleType()));
        return rule;
    }

    @Transactional
    public CfgModuleRule updateRule(
            AuthenticatedContext context, Long moduleId, Long ruleId, UpdateRuleRequest request, String traceId) {
        ConfiguredModule module = requireModule(context, moduleId);
        CfgModuleRule rule = requireRule(context, moduleId, ruleId);
        if (!request.version().equals(rule.getVersion())) conflict("规则草稿已被其他操作修改");
        validateDefinition(moduleId, request.definition());
        applyRule(rule, request.name(), request.ruleType(), request.triggerEvent(), request.definition(),
                request.message(), request.sortOrder(), request.status());
        resetTest(rule);
        if (ruleService.updateById(rule) == 0) conflict("规则草稿已被其他操作修改");
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_RULE_DRAFT_UPDATED", "MODULE_RULE", ruleId.toString(), "SUCCESS", Map.of("moduleId", moduleId));
        return rule;
    }

    @Transactional
    public RuleTestResult testRule(
            AuthenticatedContext context, Long moduleId, Long ruleId, TestRuleRequest request, String traceId) {
        requireModule(context, moduleId);
        CfgModuleRule rule = requireRule(context, moduleId, ruleId);
        try {
            RuleTestResult result = evaluator.evaluate(objectMapper.readTree(rule.getExpressionText()), request.sampleFields(),
                    rule.getMessageTemplate());
            rule.setTestStatus("EXECUTED");
            rule.setLastTestInputJson(objectMapper.writeValueAsString(request.sampleFields()));
            rule.setLastTestResultJson(objectMapper.writeValueAsString(result));
            rule.setLastTestedByMemberId(context.memberId());
            rule.setLastTestedAt(LocalDateTime.now());
            if (ruleService.updateById(rule) == 0) conflict("规则草稿已被其他操作修改");
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "MODULE_RULE_DRAFT_TESTED", "MODULE_RULE", ruleId.toString(), "SUCCESS",
                    Map.of("moduleId", moduleId, "matched", result.matched(), "effectType", result.effectType()));
            return result;
        } catch (JsonProcessingException exception) {
            throw new DomainException("RULE_DEFINITION_INVALID", "规则定义无法解析", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public QueryIndexDraft createIndex(
            AuthenticatedContext context, Long moduleId, CreateIndexRequest request, String traceId) {
        ConfiguredModule module = requireModule(context, moduleId);
        validateIndexFields(moduleId, request.fields());
        CfgQueryIndex index = new CfgQueryIndex();
        index.setSystemId(context.systemId());
        index.setOwnerTenantId(context.tenantId());
        index.setModuleId(moduleId);
        index.setCode(resolveIndexCode(moduleId, request.code()));
        index.setName(request.name().strip());
        index.setUniqueIndex(request.uniqueIndex());
        index.setStatus("ACTIVE");
        index.setVersion(0);
        indexService.insert(index);
        replaceIndexFields(index.getId(), request.fields());
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "QUERY_INDEX_DRAFT_CREATED", "QUERY_INDEX", index.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "code", index.getCode(), "unique", index.getUniqueIndex()));
        return indexDraft(index);
    }

    @Transactional
    public QueryIndexDraft updateIndex(
            AuthenticatedContext context, Long moduleId, Long indexId, UpdateIndexRequest request, String traceId) {
        ConfiguredModule module = requireModule(context, moduleId);
        CfgQueryIndex index = requireIndex(context, moduleId, indexId);
        if (!request.version().equals(index.getVersion())) conflict("索引草稿已被其他操作修改");
        validateIndexFields(moduleId, request.fields());
        index.setName(request.name().strip());
        index.setUniqueIndex(request.uniqueIndex());
        index.setStatus(request.status());
        if (indexService.updateById(index) == 0) conflict("索引草稿已被其他操作修改");
        replaceIndexFields(indexId, request.fields());
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "QUERY_INDEX_DRAFT_UPDATED", "QUERY_INDEX", indexId.toString(), "SUCCESS", Map.of("moduleId", moduleId));
        return indexDraft(indexService.selectById(indexId));
    }

    private void applyRule(CfgModuleRule rule, String name, String ruleType, String triggerEvent,
                           com.fasterxml.jackson.databind.JsonNode definition, String message, Integer sortOrder, String status) {
        rule.setName(name.strip());
        rule.setRuleType(ruleType);
        rule.setTriggerEvent(triggerEvent);
        try { rule.setExpressionText(objectMapper.writeValueAsString(definition)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize structured rule", exception); }
        rule.setMessageTemplate(message == null || message.isBlank() ? null : message.strip());
        rule.setSortOrder(sortOrder == null ? 0 : sortOrder);
        rule.setStatus(status);
    }

    private void resetTest(CfgModuleRule rule) {
        rule.setTestStatus("NOT_TESTED");
        rule.setLastTestInputJson(null);
        rule.setLastTestResultJson(null);
        rule.setLastTestedByMemberId(null);
        rule.setLastTestedAt(null);
    }

    private void validateDefinition(Long moduleId, com.fasterxml.jackson.databind.JsonNode definition) {
        Set<String> fields = activeFields(moduleId).stream().map(ConfiguredModuleField::getCode).collect(Collectors.toSet());
        List<String> issues = evaluator.inspect(definition, fields);
        if (!issues.isEmpty()) throw new DomainException("RULE_DEFINITION_INVALID", String.join("；", issues), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void validateIndexFields(Long moduleId, List<IndexFieldRequest> requests) {
        Map<Long, ConfiguredModuleField> active = activeFields(moduleId).stream()
                .collect(Collectors.toMap(ConfiguredModuleField::getId, field -> field));
        Set<Long> fieldIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (IndexFieldRequest request : requests) {
            ConfiguredModuleField field = active.get(request.fieldId());
            if (field == null) invalidIndex("索引字段不存在或已停用");
            if (!fieldIds.add(request.fieldId()) || !orders.add(request.sortOrder())) invalidIndex("索引字段和顺序不能重复");
            if (NON_INDEXABLE_TYPES.contains(field.getFieldType())) invalidIndex("字段类型 " + field.getFieldType() + " 不支持查询索引");
        }
    }

    private void replaceIndexFields(Long indexId, List<IndexFieldRequest> requests) {
        indexFieldService.selectList(Wrappers.<CfgQueryIndexField>lambdaQuery()
                        .eq(CfgQueryIndexField::getQueryIndexId, indexId))
                .forEach(field -> indexFieldService.deleteById(field.getId()));
        requests.stream().sorted(java.util.Comparator.comparing(IndexFieldRequest::sortOrder)).forEach(request -> {
            CfgQueryIndexField field = new CfgQueryIndexField();
            field.setQueryIndexId(indexId);
            field.setFieldId(request.fieldId());
            field.setSortOrder(request.sortOrder());
            field.setSortDirection(request.sortDirection());
            indexFieldService.insert(field);
        });
    }

    private QueryIndexDraft indexDraft(CfgQueryIndex index) {
        List<CfgQueryIndexField> fields = indexFieldService.selectList(Wrappers.<CfgQueryIndexField>lambdaQuery()
                .eq(CfgQueryIndexField::getQueryIndexId, index.getId())
                .orderByAsc(CfgQueryIndexField::getSortOrder, CfgQueryIndexField::getId));
        Map<Long, String> codes = new LinkedHashMap<>();
        fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery().eq(ConfiguredModuleField::getModuleId, index.getModuleId()))
                .forEach(field -> codes.put(field.getId(), field.getCode()));
        List<String> fieldCodes = fields.stream().map(field -> codes.getOrDefault(field.getFieldId(), "<missing>" )).toList();
        String scope = Boolean.TRUE.equals(index.getUniqueIndex())
                ? "SYSTEM + TENANT + MODULE + NON_DELETED" : "TENANT + MODULE";
        String plan = "biz_record_index(" + String.join(", ", fieldCodes) + ") -> SHA-256 -> "
                + (Boolean.TRUE.equals(index.getUniqueIndex()) ? "唯一哈希约束" : "普通查询投影");
        return new QueryIndexDraft(index, fields, fieldCodes, scope, plan);
    }

    private List<ConfiguredModuleField> activeFields(Long moduleId) {
        return fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId).eq(ConfiguredModuleField::getStatus, "ACTIVE"));
    }

    private ConfiguredModule requireModule(AuthenticatedContext context, Long moduleId) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || !context.systemId().equals(module.getSystemId())
                || !context.tenantId().equals(module.getOwnerTenantId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "模块不存在", HttpStatus.NOT_FOUND);
        }
        return module;
    }

    private CfgModuleRule requireRule(AuthenticatedContext context, Long moduleId, Long ruleId) {
        CfgModuleRule rule = ruleService.selectById(ruleId);
        if (rule == null || !moduleId.equals(rule.getModuleId()) || !context.systemId().equals(rule.getSystemId())
                || !context.tenantId().equals(rule.getOwnerTenantId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "规则不存在", HttpStatus.NOT_FOUND);
        }
        return rule;
    }

    private CfgQueryIndex requireIndex(AuthenticatedContext context, Long moduleId, Long indexId) {
        CfgQueryIndex index = indexService.selectById(indexId);
        if (index == null || !moduleId.equals(index.getModuleId()) || !context.systemId().equals(index.getSystemId())
                || !context.tenantId().equals(index.getOwnerTenantId())) {
            throw new DomainException("CONFIGURATION_NOT_FOUND", "索引不存在", HttpStatus.NOT_FOUND);
        }
        return index;
    }

    private void touch(ConfiguredModule module) {
        module.setDraftRevision(module.getDraftRevision() + 1);
        if (moduleService.updateById(module) == 0) conflict("模块草稿已被其他操作修改");
    }

    private String resolveRuleCode(Long moduleId, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) return normalizeCode(requestedCode);
        for (int attempt = 0; attempt < 10; attempt++) {
            String generated = generatedCode("rule");
            if (ruleService.selectList(Wrappers.<CfgModuleRule>lambdaQuery()
                    .eq(CfgModuleRule::getModuleId, moduleId).eq(CfgModuleRule::getCode, generated)).isEmpty()) {
                return generated;
            }
        }
        throw new IllegalStateException("Cannot allocate module rule code");
    }

    private String resolveIndexCode(Long moduleId, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) return normalizeCode(requestedCode);
        for (int attempt = 0; attempt < 10; attempt++) {
            String generated = generatedCode("index");
            if (indexService.selectList(Wrappers.<CfgQueryIndex>lambdaQuery()
                    .eq(CfgQueryIndex::getModuleId, moduleId).eq(CfgQueryIndex::getCode, generated)).isEmpty()) {
                return generated;
            }
        }
        throw new IllegalStateException("Cannot allocate query index code");
    }

    private String generatedCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeCode(String code) { return code.strip().toLowerCase(Locale.ROOT); }
    private void conflict(String message) { throw new DomainException("DRAFT_VERSION_CONFLICT", message, HttpStatus.CONFLICT); }
    private void invalidIndex(String message) { throw new DomainException("QUERY_INDEX_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY); }
}
