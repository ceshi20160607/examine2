package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordParticipant;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordValue;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordParticipantMapper;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordMapper;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordValueMapper;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeDataService {
    private final ModulePublicationService publicationService;
    private final BusinessRecordBaseService recordService;
    private final BusinessRecordMapper recordMapper;
    private final BusinessRecordValueMapper valueMapper;
    private final BusinessRecordParticipantMapper participantMapper;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemDepartmentBaseService departmentService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public RuntimeDataService(
            ModulePublicationService publicationService,
            BusinessRecordBaseService recordService,
            BusinessRecordMapper recordMapper,
            BusinessRecordValueMapper valueMapper,
            BusinessRecordParticipantMapper participantMapper,
            SystemTenantMemberBaseService tenantMemberService,
            SystemDepartmentBaseService departmentService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.publicationService = publicationService;
        this.recordService = recordService;
        this.recordMapper = recordMapper;
        this.valueMapper = valueMapper;
        this.participantMapper = participantMapper;
        this.tenantMemberService = tenantMemberService;
        this.departmentService = departmentService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RuntimeRecordList list(
            AuthenticatedContext context,
            String moduleCode,
            int pageNumber,
            int pageSize,
            String traceId) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new DomainException("PAGINATION_INVALID", "页码必须大于 0，每页数量不能超过 200", HttpStatus.BAD_REQUEST);
        }
        requireAction(context, moduleCode, "LIST", traceId);
        PublishedModule published = published(context, moduleCode);
        LambdaQueryWrapper<BusinessRecord> query = boundary(context, published.configuration().moduleId());
        applyDataScope(query, context, moduleCode, "LIST");
        query.orderByDesc(BusinessRecord::getUpdatedAt, BusinessRecord::getId);
        Page<BusinessRecord> page = recordMapper.selectPage(new Page<>(pageNumber, pageSize), query);
        List<RuntimeRecordView> views = page.getRecords().stream().map(record -> view(record, published)).toList();
        return new RuntimeRecordList(views, page.getTotal(), pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public RuntimeRecordView detail(AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        requireAction(context, moduleCode, "DETAIL", traceId);
        PublishedModule published = published(context, moduleCode);
        BusinessRecord record = requireBoundaryRecord(context, published.configuration().moduleId(), recordId);
        if (!withinScope(context, moduleCode, "DETAIL", recordId, published.configuration().moduleId())) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        }
        return view(record, published);
    }

    @Transactional
    public RuntimeRecordView create(
            AuthenticatedContext context,
            String moduleCode,
            CreateRuntimeRecordRequest request,
            String traceId) {
        requireAction(context, moduleCode, "CREATE", traceId);
        PublishedModule published = published(context, moduleCode);
        Map<String, JsonNode> normalizedValues = validateFields(context, published.fields(), request.fields(), traceId, null);
        Ownership ownership = validateOwnership(
                context, request.ownerMemberId(), request.departmentId(), request.participantMemberIds(), traceId, null);

        BusinessRecord record = new BusinessRecord();
        record.setSystemId(context.systemId());
        record.setTenantId(context.tenantId());
        record.setModuleId(published.configuration().moduleId());
        record.setCreatedConfigVersionId(published.configuration().versionId());
        record.setUpdatedConfigVersionId(published.configuration().versionId());
        record.setRecordNumber(blankToNull(request.recordNumber()));
        record.setTitle(request.title().strip());
        record.setStatus(blankToDefault(request.status(), "ACTIVE"));
        record.setOwnerMemberId(ownership.ownerMemberId());
        record.setDepartmentId(ownership.departmentId());
        record.setCreatedByMemberId(context.memberId());
        record.setUpdatedByMemberId(context.memberId());
        record.setArchived(false);
        record.setDeleted(false);
        record.setVersion(0);
        recordService.insert(record);
        replaceValues(record.getId(), published.fields(), normalizedValues);
        replaceParticipants(context, record.getId(), ownership.participantMemberIds());

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_CREATED", "BUSINESS_RECORD", record.getId().toString(), "SUCCESS",
                Map.of("moduleCode", moduleCode, "configVersionId", published.configuration().versionId(),
                        "fields", normalizedValues, "participantMemberIds", ownership.participantMemberIds()));
        return view(recordService.selectById(record.getId()), published);
    }

    @Transactional
    public RuntimeRecordView update(
            AuthenticatedContext context,
            String moduleCode,
            Long recordId,
            UpdateRuntimeRecordRequest request,
            String traceId) {
        requireAction(context, moduleCode, "UPDATE", traceId);
        PublishedModule published = published(context, moduleCode);
        BusinessRecord record = requireBoundaryRecord(context, published.configuration().moduleId(), recordId);
        if (!withinScope(context, moduleCode, "UPDATE", recordId, published.configuration().moduleId())) {
            deny(context, moduleCode, "UPDATE", traceId, Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
        }
        if (!request.version().equals(record.getVersion())) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> before = readValues(recordId, published.fields());
        Map<String, JsonNode> normalizedValues = validateFields(
                context, published.fields(), request.fields(), traceId, recordId);
        Ownership ownership = validateOwnership(
                context, request.ownerMemberId(), request.departmentId(), request.participantMemberIds(), traceId, recordId);

        record.setUpdatedConfigVersionId(published.configuration().versionId());
        record.setRecordNumber(blankToNull(request.recordNumber()));
        record.setTitle(request.title().strip());
        record.setStatus(blankToDefault(request.status(), "ACTIVE"));
        record.setOwnerMemberId(ownership.ownerMemberId());
        record.setDepartmentId(ownership.departmentId());
        record.setUpdatedByMemberId(context.memberId());
        if (recordService.updateById(record) == 0) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        replaceValues(recordId, published.fields(), normalizedValues);
        replaceParticipants(context, recordId, ownership.participantMemberIds());

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_UPDATED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                Map.of("moduleCode", moduleCode, "configVersionId", published.configuration().versionId(),
                        "beforeFields", before, "afterFields", normalizedValues,
                        "participantMemberIds", ownership.participantMemberIds()));
        return view(recordService.selectById(recordId), published);
    }

    private PublishedModule published(AuthenticatedContext context, String moduleCode) {
        RuntimeModuleConfiguration configuration = publicationService.published(context, moduleCode);
        List<PublishedField> fields = new ArrayList<>();
        for (JsonNode field : configuration.configuration().path("fields")) {
            fields.add(new PublishedField(
                    field.path("id").longValue(),
                    field.path("code").asText(),
                    field.path("name").asText(),
                    field.path("fieldType").asText(),
                    field.path("required").asBoolean(false),
                    parseConfig(field.path("configJson").asText("{}"))));
        }
        fields.sort(Comparator.comparing(PublishedField::code));
        return new PublishedModule(configuration, fields);
    }

    private JsonNode parseConfig(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Published field configuration is invalid", exception);
        }
    }

    private void requireAction(AuthenticatedContext context, String moduleCode, String actionCode, String traceId) {
        if (!permissionChecker.allows(context, "MODULE", moduleCode, actionCode)) {
            deny(context, moduleCode, actionCode, traceId, Map.of("reason", "ACTION_NOT_GRANTED"));
        }
    }

    private void deny(
            AuthenticatedContext context,
            String moduleCode,
            String actionCode,
            String traceId,
            Map<String, ?> detail) {
        String permissionCode = "MODULE:" + moduleCode + ":" + actionCode;
        auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), permissionCode,
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions(),
                        "dataScopes", context.dataScopes(), "detail", detail));
        throw new DomainException("PERMISSION_DENIED", "没有执行该操作的权限", HttpStatus.FORBIDDEN);
    }

    private LambdaQueryWrapper<BusinessRecord> boundary(AuthenticatedContext context, Long moduleId) {
        return Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, context.systemId())
                .eq(BusinessRecord::getTenantId, context.tenantId())
                .eq(BusinessRecord::getModuleId, moduleId)
                .eq(BusinessRecord::getDeleted, false);
    }

    private BusinessRecord requireBoundaryRecord(AuthenticatedContext context, Long moduleId, Long recordId) {
        return recordService.selectList(boundary(context, moduleId).eq(BusinessRecord::getId, recordId))
                .stream().findFirst().orElseThrow(() ->
                        new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND));
    }

    private boolean withinScope(
            AuthenticatedContext context,
            String moduleCode,
            String actionCode,
            Long recordId,
            Long moduleId) {
        LambdaQueryWrapper<BusinessRecord> query = boundary(context, moduleId).eq(BusinessRecord::getId, recordId);
        applyDataScope(query, context, moduleCode, actionCode);
        return !recordService.selectList(query).isEmpty();
    }

    private void applyDataScope(
            LambdaQueryWrapper<BusinessRecord> query,
            AuthenticatedContext context,
            String moduleCode,
            String actionCode) {
        List<DataScopeTerm> terms = matchingTerms(context, moduleCode, actionCode);
        if (terms.stream().anyMatch(term -> "ALL".equals(term.type()))) {
            return;
        }
        Long currentDepartmentId = currentDepartmentId(context);
        List<Long> descendantDepartmentIds = descendantDepartmentIds(context, currentDepartmentId);
        List<ScopeClause> clauses = new ArrayList<>();
        for (DataScopeTerm term : terms) {
            switch (term.type()) {
                case "SELF" -> clauses.add(wrapper -> wrapper.eq(BusinessRecord::getOwnerMemberId, context.memberId()));
                case "CREATED_BY_SELF" -> clauses.add(wrapper ->
                        wrapper.eq(BusinessRecord::getCreatedByMemberId, context.memberId()));
                case "PARTICIPATED" -> clauses.add(wrapper -> wrapper.apply(
                        "exists (select 1 from biz_record_participant p where p.record_id = biz_record.id "
                                + "and p.system_id = {0} and p.tenant_id = {1} and p.system_member_id = {2})",
                        context.systemId(), context.tenantId(), context.memberId()));
                case "DEPARTMENT" -> {
                    if (currentDepartmentId != null) {
                        clauses.add(wrapper -> wrapper.eq(BusinessRecord::getDepartmentId, currentDepartmentId));
                    }
                }
                case "DEPARTMENT_AND_DESCENDANTS" -> {
                    if (!descendantDepartmentIds.isEmpty()) {
                        clauses.add(wrapper -> wrapper.in(BusinessRecord::getDepartmentId, descendantDepartmentIds));
                    }
                }
                case "SPECIFIED_DEPARTMENTS" -> {
                    List<Long> ids = longValues(term.condition(), "departmentIds");
                    if (!ids.isEmpty()) {
                        clauses.add(wrapper -> wrapper.in(BusinessRecord::getDepartmentId, ids));
                    }
                }
                case "SPECIFIED_MEMBERS" -> {
                    List<Long> ids = longValues(term.condition(), "memberIds");
                    if (!ids.isEmpty()) {
                        clauses.add(wrapper -> wrapper.in(BusinessRecord::getOwnerMemberId, ids));
                    }
                }
                default -> {
                    // Unknown and future scope terms never widen access.
                }
            }
        }
        if (clauses.isEmpty()) {
            query.apply("1 = 0");
            return;
        }
        query.and(nested -> {
            boolean first = true;
            for (ScopeClause clause : clauses) {
                if (!first) {
                    nested.or();
                }
                clause.apply(nested);
                first = false;
            }
        });
    }

    private List<DataScopeTerm> matchingTerms(AuthenticatedContext context, String moduleCode, String actionCode) {
        List<DataScopeTerm> result = new ArrayList<>();
        context.dataScopes().forEach((key, expression) -> {
            String[] values = key.split(":", -1);
            if (values.length == 3 && matches(values[0], "MODULE") && matches(values[1], moduleCode)
                    && matches(values[2], actionCode)) {
                result.addAll(expression.terms());
            }
        });
        return result;
    }

    private boolean matches(String granted, String required) {
        return "*".equals(granted) || granted.equals(required);
    }

    private Long currentDepartmentId(AuthenticatedContext context) {
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, context.memberId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().findFirst().map(SystemTenantMember::getDepartmentId).orElse(null);
    }

    private List<Long> descendantDepartmentIds(AuthenticatedContext context, Long rootId) {
        if (rootId == null) {
            return List.of();
        }
        List<SystemDepartment> departments = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                .eq(SystemDepartment::getSystemId, context.systemId())
                .eq(SystemDepartment::getTenantId, context.tenantId())
                .eq(SystemDepartment::getStatus, "ACTIVE"));
        Map<Long, List<Long>> children = new HashMap<>();
        departments.forEach(department -> children.computeIfAbsent(department.getParentId(), ignored -> new ArrayList<>())
                .add(department.getId()));
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            if (result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result.stream().toList();
    }

    private List<Long> longValues(JsonNode condition, String property) {
        JsonNode values = condition == null ? null : condition.path(property);
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        values.forEach(value -> {
            if (value.canConvertToLong()) {
                result.add(value.longValue());
            }
        });
        return result;
    }

    private Map<String, JsonNode> validateFields(
            AuthenticatedContext context,
            List<PublishedField> fields,
            Map<String, JsonNode> submitted,
            String traceId,
            Long recordId) {
        Map<String, PublishedField> byCode = new LinkedHashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        List<String> errors = new ArrayList<>();
        submitted.keySet().stream().filter(code -> !byCode.containsKey(code)).sorted()
                .forEach(code -> errors.add(code + ": 字段未发布或不存在"));
        Map<String, JsonNode> normalized = new LinkedHashMap<>();
        for (PublishedField field : fields) {
            JsonNode value = submitted.get(field.code());
            if (empty(value)) {
                if (field.required()) {
                    errors.add(field.code() + ": 必填字段不能为空");
                }
                continue;
            }
            try {
                JsonNode normalizedValue = normalize(field, value);
                validateReferenceValue(context, field, normalizedValue);
                normalized.put(field.code(), normalizedValue);
            } catch (IllegalArgumentException exception) {
                errors.add(field.code() + ": " + exception.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("errors", errors);
            if (recordId != null) {
                detail.put("recordId", recordId);
            }
            auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    recordId == null ? "BUSINESS_RECORD_CREATE" : "BUSINESS_RECORD_UPDATE", "BUSINESS_RECORD",
                    recordId == null ? null : recordId.toString(), "FIELD_VALIDATION_FAILED", detail);
            throw new DomainException("FIELD_VALIDATION_FAILED", String.join("；", errors), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return normalized;
    }

    private void validateReferenceValue(AuthenticatedContext context, PublishedField field, JsonNode value) {
        if ("MEMBER".equals(field.type())) {
            boolean valid = !tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                    .eq(SystemTenantMember::getSystemId, context.systemId())
                    .eq(SystemTenantMember::getTenantId, context.tenantId())
                    .eq(SystemTenantMember::getSystemMemberId, value.longValue())
                    .eq(SystemTenantMember::getStatus, "ACTIVE")).isEmpty();
            if (!valid) {
                throw new IllegalArgumentException("成员不属于当前租户或已停用");
            }
        }
        if ("DEPARTMENT".equals(field.type())) {
            boolean valid = !departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                    .eq(SystemDepartment::getSystemId, context.systemId())
                    .eq(SystemDepartment::getTenantId, context.tenantId())
                    .eq(SystemDepartment::getId, value.longValue())
                    .eq(SystemDepartment::getStatus, "ACTIVE")).isEmpty();
            if (!valid) {
                throw new IllegalArgumentException("部门不属于当前租户或已停用");
            }
        }
    }

    private JsonNode normalize(PublishedField field, JsonNode value) {
        return switch (field.type()) {
            case "TEXT", "MULTILINE_TEXT" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是文本");
                }
                yield TextNode.valueOf(value.textValue());
            }
            case "SINGLE_SELECT", "STATUS" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是选项值");
                }
                validateOption(field, value.textValue());
                yield TextNode.valueOf(value.textValue());
            }
            case "NUMBER", "MONEY" -> {
                if (!value.isNumber()) {
                    throw new IllegalArgumentException("必须是数字");
                }
                yield DecimalNode.valueOf(value.decimalValue());
            }
            case "DATE" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是日期");
                }
                try {
                    yield TextNode.valueOf(LocalDate.parse(value.textValue()).toString());
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd");
                }
            }
            case "DATETIME" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是日期时间");
                }
                try {
                    yield TextNode.valueOf(LocalDateTime.parse(value.textValue()).toString());
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException("日期时间格式不正确");
                }
            }
            case "BOOLEAN" -> {
                if (!value.isBoolean()) {
                    throw new IllegalArgumentException("必须是布尔值");
                }
                yield BooleanNode.valueOf(value.booleanValue());
            }
            case "MEMBER", "DEPARTMENT" -> {
                if (!value.canConvertToLong()) {
                    throw new IllegalArgumentException("必须是有效 ID");
                }
                yield LongNode.valueOf(value.longValue());
            }
            default -> throw new IllegalArgumentException("当前阶段不支持该字段类型");
        };
    }

    private void validateOption(PublishedField field, String submitted) {
        JsonNode options = field.config().path("options");
        if (!options.isArray() || options.isEmpty()) {
            throw new IllegalArgumentException("字段没有可用选项");
        }
        for (JsonNode option : options) {
            String value = option.path("value").asText(option.path("code").asText());
            if (submitted.equals(value)) {
                String status = option.path("status").asText("ACTIVE");
                if (!"ACTIVE".equals(status) || option.path("disabled").asBoolean(false)) {
                    throw new IllegalArgumentException("选项已停用");
                }
                return;
            }
        }
        throw new IllegalArgumentException("选项不存在");
    }

    private Ownership validateOwnership(
            AuthenticatedContext context,
            Long requestedOwner,
            Long requestedDepartment,
            List<Long> participants,
            String traceId,
            Long recordId) {
        Long owner = requestedOwner == null ? context.memberId() : requestedOwner;
        Long currentDepartment = currentDepartmentId(context);
        Long department = requestedDepartment == null ? currentDepartment : requestedDepartment;
        Set<Long> participantIds = new LinkedHashSet<>(participants);
        List<Long> memberIds = new ArrayList<>(participantIds);
        memberIds.add(owner);
        long activeMembers = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE")
                        .in(SystemTenantMember::getSystemMemberId, memberIds))
                .stream().map(SystemTenantMember::getSystemMemberId).distinct().count();
        boolean departmentValid = department == null || !departmentService.selectList(
                Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getSystemId, context.systemId())
                        .eq(SystemDepartment::getTenantId, context.tenantId())
                        .eq(SystemDepartment::getId, department)
                        .eq(SystemDepartment::getStatus, "ACTIVE")).isEmpty();
        if (activeMembers != memberIds.stream().distinct().count() || !departmentValid) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ownerMemberId", owner);
            detail.put("departmentId", department);
            detail.put("participantMemberIds", participantIds);
            auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    recordId == null ? "BUSINESS_RECORD_CREATE" : "BUSINESS_RECORD_UPDATE", "BUSINESS_RECORD",
                    recordId == null ? null : recordId.toString(), "OWNERSHIP_VALIDATION_FAILED", detail);
            throw new DomainException("OWNERSHIP_VALIDATION_FAILED", "负责人、所属部门或相关人员不属于当前租户", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return new Ownership(owner, department, participantIds.stream().sorted().toList());
    }

    private void replaceValues(Long recordId, List<PublishedField> fields, Map<String, JsonNode> values) {
        List<Long> currentFieldIds = fields.stream().map(PublishedField::id).toList();
        if (!currentFieldIds.isEmpty()) {
            valueMapper.delete(Wrappers.<BusinessRecordValue>lambdaQuery()
                    .eq(BusinessRecordValue::getRecordId, recordId)
                    .in(BusinessRecordValue::getFieldId, currentFieldIds));
        }
        Map<String, PublishedField> byCode = new HashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        values.forEach((code, value) -> valueMapper.insert(toStoredValue(recordId, byCode.get(code), value)));
    }

    private BusinessRecordValue toStoredValue(Long recordId, PublishedField field, JsonNode value) {
        BusinessRecordValue stored = new BusinessRecordValue();
        stored.setRecordId(recordId);
        stored.setFieldId(field.id());
        stored.setFieldCode(field.code());
        stored.setValueType(field.type());
        switch (field.type()) {
            case "TEXT", "MULTILINE_TEXT", "SINGLE_SELECT", "STATUS" -> stored.setValueText(value.textValue());
            case "NUMBER", "MONEY" -> stored.setValueNumber(value.decimalValue());
            case "DATE" -> stored.setValueDate(LocalDate.parse(value.textValue()));
            case "DATETIME" -> stored.setValueDatetime(LocalDateTime.parse(value.textValue()));
            case "BOOLEAN" -> stored.setValueBoolean(value.booleanValue());
            case "MEMBER", "DEPARTMENT" -> stored.setValueReferenceId(value.longValue());
            default -> throw new IllegalStateException("Unsupported published field type: " + field.type());
        }
        return stored;
    }

    private void replaceParticipants(AuthenticatedContext context, Long recordId, List<Long> memberIds) {
        participantMapper.delete(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                .eq(BusinessRecordParticipant::getRecordId, recordId));
        for (Long memberId : memberIds) {
            BusinessRecordParticipant participant = new BusinessRecordParticipant();
            participant.setSystemId(context.systemId());
            participant.setTenantId(context.tenantId());
            participant.setRecordId(recordId);
            participant.setSystemMemberId(memberId);
            participant.setCreatedByMemberId(context.memberId());
            participantMapper.insert(participant);
        }
    }

    private RuntimeRecordView view(BusinessRecord record, PublishedModule published) {
        List<Long> participantIds = participantMapper.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, record.getId())
                        .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                .stream().map(BusinessRecordParticipant::getSystemMemberId).toList();
        return new RuntimeRecordView(
                record.getId(), record.getRecordNumber(), record.getTitle(), record.getStatus(), record.getOwnerMemberId(),
                record.getDepartmentId(), record.getCreatedByMemberId(), record.getUpdatedByMemberId(),
                record.getCreatedConfigVersionId(), record.getUpdatedConfigVersionId(), participantIds,
                record.getVersion(), record.getCreatedAt(), record.getUpdatedAt(), readValues(record.getId(), published.fields()));
    }

    private Map<String, JsonNode> readValues(Long recordId, List<PublishedField> fields) {
        Set<String> visibleCodes = fields.stream().map(PublishedField::code).collect(java.util.stream.Collectors.toSet());
        Map<String, JsonNode> result = new LinkedHashMap<>();
        valueMapper.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                        .eq(BusinessRecordValue::getRecordId, recordId).orderByAsc(BusinessRecordValue::getId))
                .stream().filter(value -> visibleCodes.contains(value.getFieldCode()))
                .forEach(value -> result.put(value.getFieldCode(), fromStoredValue(value)));
        return result;
    }

    private JsonNode fromStoredValue(BusinessRecordValue value) {
        if (value.getValueText() != null) {
            return TextNode.valueOf(value.getValueText());
        }
        if (value.getValueNumber() != null) {
            return DecimalNode.valueOf(value.getValueNumber());
        }
        if (value.getValueDate() != null) {
            return TextNode.valueOf(value.getValueDate().toString());
        }
        if (value.getValueDatetime() != null) {
            return TextNode.valueOf(value.getValueDatetime().toString());
        }
        if (value.getValueBoolean() != null) {
            return BooleanNode.valueOf(value.getValueBoolean());
        }
        if (value.getValueReferenceId() != null) {
            return LongNode.valueOf(value.getValueReferenceId());
        }
        return objectMapper.nullNode();
    }

    private boolean empty(JsonNode value) {
        return value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip().toUpperCase(Locale.ROOT);
    }

    private record PublishedModule(RuntimeModuleConfiguration configuration, List<PublishedField> fields) {
    }

    private record PublishedField(Long id, String code, String name, String type, boolean required, JsonNode config) {
    }

    private record Ownership(Long ownerMemberId, Long departmentId, List<Long> participantMemberIds) {
    }

    @FunctionalInterface
    private interface ScopeClause {
        void apply(LambdaQueryWrapper<BusinessRecord> wrapper);
    }
}
