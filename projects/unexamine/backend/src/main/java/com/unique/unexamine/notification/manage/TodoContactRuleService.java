package com.unique.unexamine.notification.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogItem;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.notification.base.entity.TodoContactRule;
import com.unique.unexamine.notification.base.service.TodoContactRuleBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 管理业务模块日期字段到“今日需联系”待办的发布规则。 */
@Service
public class TodoContactRuleService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> FIELD_TYPES = Set.of("DATE", "DATETIME");

    private final TodoContactRuleBaseService ruleService;
    private final RuntimeModuleCatalogService catalogService;
    private final ModulePublicationService publicationService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;

    public TodoContactRuleService(
            TodoContactRuleBaseService ruleService,
            RuntimeModuleCatalogService catalogService,
            ModulePublicationService publicationService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder) {
        this.ruleService = ruleService;
        this.catalogService = catalogService;
        this.publicationService = publicationService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<TodoContactRuleModels.RuleView> list(AuthenticatedContext context) {
        requireSystem(context);
        requireWorkPermission(context, "VIEW_CONFIG");
        return ownRules(context).stream()
                .sorted(Comparator.comparing(TodoContactRule::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::view).toList();
    }

    @Transactional
    public TodoContactRuleModels.RuleView create(
            AuthenticatedContext context, TodoContactRuleModels.SaveRuleRequest input, String traceId) {
        requireSystem(context);
        requireWorkPermission(context, "CONFIGURE");
        if (input.expectedVersion() != null) {
            throw conflict("TODO_CONTACT_RULE_VERSION_CONFLICT", "新建规则不能携带旧版本号");
        }
        ValidatedSource source = validateSource(context, input);
        requireUnique(context, null, source.moduleCode(), source.fieldCode(), input.leadDays());
        TodoContactRule row = new TodoContactRule();
        applyContext(row, context);
        applyInput(row, input, source);
        row.setStatus("DRAFT");
        row.setCreatedByAccountId(context.accountId());
        row.setVersion(0);
        ruleService.insert(row);
        audit(context, traceId, "TODO_CONTACT_RULE_DRAFT_CREATED", row);
        return view(ruleService.selectById(row.getId()));
    }

    @Transactional
    public TodoContactRuleModels.RuleView update(
            AuthenticatedContext context, Long ruleId,
            TodoContactRuleModels.SaveRuleRequest input, String traceId) {
        requireSystem(context);
        requireWorkPermission(context, "CONFIGURE");
        TodoContactRule row = requireOwn(context, ruleId);
        requireVersion(row, input.expectedVersion());
        ValidatedSource source = validateSource(context, input);
        requireUnique(context, row.getId(), source.moduleCode(), source.fieldCode(), input.leadDays());
        applyInput(row, input, source);
        // 修改发布规则后必须重新发布，避免配置者误以为新字段已经生效。
        row.setStatus("DRAFT");
        row.setUpdateTime(LocalDateTime.now());
        if (ruleService.updateById(row) != 1) {
            throw conflict("TODO_CONTACT_RULE_VERSION_CONFLICT", "规则已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "TODO_CONTACT_RULE_DRAFT_UPDATED", row);
        return view(ruleService.selectById(row.getId()));
    }

    @Transactional
    public TodoContactRuleModels.RuleView publish(
            AuthenticatedContext context, Long ruleId,
            TodoContactRuleModels.ChangeStatusRequest input, String traceId) {
        requireSystem(context);
        requireWorkPermission(context, "PUBLISH_CONFIG");
        TodoContactRule row = requireOwn(context, ruleId);
        requireVersion(row, input.expectedVersion());
        validateSource(context, new TodoContactRuleModels.SaveRuleRequest(
                row.getVersion(), row.getName(), row.getModuleCode(), row.getContactFieldCode(),
                row.getLeadDays(), row.getPriority()));
        row.setStatus("PUBLISHED");
        row.setUpdateTime(LocalDateTime.now());
        if (ruleService.updateById(row) != 1) {
            throw conflict("TODO_CONTACT_RULE_VERSION_CONFLICT", "规则已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "TODO_CONTACT_RULE_PUBLISHED", row);
        return view(ruleService.selectById(row.getId()));
    }

    @Transactional
    public TodoContactRuleModels.RuleView disable(
            AuthenticatedContext context, Long ruleId,
            TodoContactRuleModels.ChangeStatusRequest input, String traceId) {
        requireSystem(context);
        requireWorkPermission(context, "PUBLISH_CONFIG");
        TodoContactRule row = requireOwn(context, ruleId);
        requireVersion(row, input.expectedVersion());
        row.setStatus("DISABLED");
        row.setUpdateTime(LocalDateTime.now());
        if (ruleService.updateById(row) != 1) {
            throw conflict("TODO_CONTACT_RULE_VERSION_CONFLICT", "规则已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "TODO_CONTACT_RULE_DISABLED", row);
        return view(ruleService.selectById(row.getId()));
    }

    private ValidatedSource validateSource(
            AuthenticatedContext context, TodoContactRuleModels.SaveRuleRequest input) {
        String moduleCode = input.moduleCode().strip();
        String fieldCode = input.contactFieldCode().strip();
        String priority = input.priority().strip().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(priority)) {
            throw invalid("TODO_CONTACT_RULE_PRIORITY_INVALID", "联系待办优先级无效");
        }
        RuntimeModuleCatalogItem module = catalogService.list(context).stream()
                .filter(item -> "CURRENT".equals(item.configurationMode()))
                .filter(item -> item.moduleCode().equals(moduleCode)).findFirst()
                .orElseThrow(() -> invalid("TODO_CONTACT_RULE_MODULE_INVALID", "请选择当前系统中已发布且可查看的业务模块"));
        RuntimeModuleConfiguration runtime = publicationService.runtime(context, moduleCode, "todo-contact-rule");
        JsonNode field = null;
        for (JsonNode candidate : runtime.configuration().path("fields")) {
            if (fieldCode.equals(candidate.path("code").asText())) {
                field = candidate;
                break;
            }
        }
        if (field == null || !FIELD_TYPES.contains(field.path("fieldType").asText())
                || !"ACTIVE".equals(field.path("status").asText())
                || !field.path("access").path("readable").asBoolean(false)) {
            throw invalid("TODO_CONTACT_RULE_FIELD_INVALID", "请选择当前可读取的日期或日期时间字段");
        }
        return new ValidatedSource(moduleCode, module.moduleName(), fieldCode,
                field.path("name").asText(fieldCode), field.path("fieldType").asText(), priority);
    }

    private void applyInput(TodoContactRule row, TodoContactRuleModels.SaveRuleRequest input, ValidatedSource source) {
        row.setName(input.name().strip());
        row.setModuleCode(source.moduleCode());
        row.setModuleNameSnapshot(source.moduleName());
        row.setContactFieldCode(source.fieldCode());
        row.setContactFieldNameSnapshot(source.fieldName());
        row.setContactFieldType(source.fieldType());
        row.setLeadDays(input.leadDays());
        row.setPriority(source.priority());
    }

    private void applyContext(TodoContactRule row, AuthenticatedContext context) {
        row.setContextType("SYSTEM");
        row.setPlatformId(context.platformId());
        row.setSystemId(context.systemId());
        row.setTenantId(context.tenantId());
        row.setScopeKey(scopeKey(context));
    }

    private List<TodoContactRule> ownRules(AuthenticatedContext context) {
        return ruleService.selectList(Wrappers.<TodoContactRule>lambdaQuery()
                .eq(TodoContactRule::getScopeKey, scopeKey(context)));
    }

    private TodoContactRule requireOwn(AuthenticatedContext context, Long ruleId) {
        TodoContactRule row = ruleService.selectById(ruleId);
        if (row == null || !scopeKey(context).equals(row.getScopeKey())) {
            throw new DomainException("TODO_CONTACT_RULE_NOT_FOUND", "联系规则不存在", HttpStatus.NOT_FOUND);
        }
        return row;
    }

    private void requireUnique(
            AuthenticatedContext context, Long excludedId, String moduleCode, String fieldCode, Integer leadDays) {
        boolean duplicate = ownRules(context).stream().anyMatch(row -> !Objects.equals(row.getId(), excludedId)
                && row.getModuleCode().equals(moduleCode) && row.getContactFieldCode().equals(fieldCode)
                && Objects.equals(row.getLeadDays(), leadDays));
        if (duplicate) throw conflict("TODO_CONTACT_RULE_DUPLICATE", "该模块字段和提前天数组合已经存在");
    }

    private void requireVersion(TodoContactRule row, Integer expectedVersion) {
        if (expectedVersion == null || !Objects.equals(row.getVersion(), expectedVersion)) {
            throw conflict("TODO_CONTACT_RULE_VERSION_CONFLICT", "规则版本已变化，请刷新后重试");
        }
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context == null || context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统工作空间", HttpStatus.CONFLICT);
        }
    }

    private void requireWorkPermission(AuthenticatedContext context, String action) {
        if (!permissionChecker.allows(context, "WORK", "SYSTEM", action)
                && !permissionChecker.allows(context, "WORK", "*", action)) {
            throw new DomainException("TODO_CONTACT_RULE_PERMISSION_DENIED", "没有联系规则配置权限", HttpStatus.FORBIDDEN);
        }
    }

    private String scopeKey(AuthenticatedContext context) {
        return "P:" + context.platformId() + ":S:" + context.systemId() + ":T:" + context.tenantId();
    }

    private TodoContactRuleModels.RuleView view(TodoContactRule row) {
        return new TodoContactRuleModels.RuleView(row.getId(), row.getName(), row.getModuleCode(),
                row.getModuleNameSnapshot(), row.getContactFieldCode(), row.getContactFieldNameSnapshot(),
                row.getContactFieldType(), row.getLeadDays(), row.getPriority(), row.getStatus(),
                row.getCreatedByAccountId(), row.getCreateTime(), row.getUpdateTime(), row.getVersion());
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode, TodoContactRule row) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, "TODO_CONTACT_RULE", String.valueOf(row.getId()), "SUCCESS",
                Map.of("moduleCode", row.getModuleCode(), "fieldCode", row.getContactFieldCode(),
                        "leadDays", row.getLeadDays(), "status", row.getStatus()));
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private record ValidatedSource(
            String moduleCode, String moduleName, String fieldCode, String fieldName, String fieldType, String priority) { }
}
