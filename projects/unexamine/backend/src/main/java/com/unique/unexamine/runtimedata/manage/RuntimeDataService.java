package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordParticipant;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordValue;
import com.unique.unexamine.runtimedata.base.entity.BizRecordRelation;
import com.unique.unexamine.runtimedata.base.entity.BizRecordStateHistory;
import com.unique.unexamine.runtimedata.base.entity.BizRecordOwnerHistory;
import com.unique.unexamine.runtimedata.base.entity.BizRecordConversion;
import com.unique.unexamine.runtimedata.base.entity.BizRecordConversionResult;
import com.unique.unexamine.runtimedata.base.entity.BizTenantShare;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.runtimedata.base.mapper.BizRecordRelationMapper;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordParticipantMapper;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordMapper;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordValueMapper;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordStateHistoryBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordOwnerHistoryBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordConversionBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordConversionResultBaseService;
import com.unique.unexamine.runtimedata.base.service.BizTenantShareBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.entity.AuditFieldChange;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.audit.base.service.AuditFieldChangeBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.DictionaryConfigurationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final DictionaryConfigurationService dictionaryConfigurationService;
    private final BusinessRecordBaseService recordService;
    private final BusinessRecordMapper recordMapper;
    private final BusinessRecordValueMapper valueMapper;
    private final BusinessRecordParticipantMapper participantMapper;
    private final BizRecordRelationMapper relationMapper;
    private final BizRecordStateHistoryBaseService stateHistoryService;
    private final BizRecordOwnerHistoryBaseService ownerHistoryService;
    private final BizRecordConversionBaseService conversionService;
    private final BizRecordConversionResultBaseService conversionResultService;
    private final BizTenantShareBaseService tenantShareService;
    private final TenantShareUsageRecorder tenantShareUsageRecorder;
    private final ConfiguredModuleBaseService moduleService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemDepartmentBaseService departmentService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantBaseService tenantService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final AuditRecorder auditRecorder;
    private final AuditEventBaseService auditEventService;
    private final AuditFieldChangeBaseService fieldChangeService;
    private final RuntimeRuleIndexService ruleIndexService;
    private final ObjectMapper objectMapper;

    public RuntimeDataService(
            ModulePublicationService publicationService,
            DictionaryConfigurationService dictionaryConfigurationService,
            BusinessRecordBaseService recordService,
            BusinessRecordMapper recordMapper,
            BusinessRecordValueMapper valueMapper,
            BusinessRecordParticipantMapper participantMapper,
            BizRecordRelationMapper relationMapper,
            BizRecordStateHistoryBaseService stateHistoryService,
            BizRecordOwnerHistoryBaseService ownerHistoryService,
            BizRecordConversionBaseService conversionService,
            BizRecordConversionResultBaseService conversionResultService,
            BizTenantShareBaseService tenantShareService,
            TenantShareUsageRecorder tenantShareUsageRecorder,
            ConfiguredModuleBaseService moduleService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemDepartmentBaseService departmentService,
            SystemMemberBaseService memberService,
            SystemTenantBaseService tenantService,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            AuditRecorder auditRecorder,
            AuditEventBaseService auditEventService,
            AuditFieldChangeBaseService fieldChangeService,
            RuntimeRuleIndexService ruleIndexService,
            ObjectMapper objectMapper) {
        this.publicationService = publicationService;
        this.dictionaryConfigurationService = dictionaryConfigurationService;
        this.recordService = recordService;
        this.recordMapper = recordMapper;
        this.valueMapper = valueMapper;
        this.participantMapper = participantMapper;
        this.relationMapper = relationMapper;
        this.stateHistoryService = stateHistoryService;
        this.ownerHistoryService = ownerHistoryService;
        this.conversionService = conversionService;
        this.conversionResultService = conversionResultService;
        this.tenantShareService = tenantShareService;
        this.tenantShareUsageRecorder = tenantShareUsageRecorder;
        this.moduleService = moduleService;
        this.tenantMemberService = tenantMemberService;
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.tenantService = tenantService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.auditRecorder = auditRecorder;
        this.auditEventService = auditEventService;
        this.fieldChangeService = fieldChangeService;
        this.ruleIndexService = ruleIndexService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RuntimeRecordList list(
            AuthenticatedContext context,
            String moduleCode,
            String lifecycleState,
            String tenantScope,
            String search,
            String filtersJson,
            String sortField,
            String sortDirection,
            int pageNumber,
            int pageSize,
            String traceId) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new DomainException("PAGINATION_INVALID", "页码必须大于 0，每页数量不能超过 200", HttpStatus.BAD_REQUEST);
        }
        requireAction(context, moduleCode, "LIST", traceId);
        String normalizedTenantScope = tenantScope == null ? "ALL" : tenantScope.toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "OWN", "SHARED").contains(normalizedTenantScope)) {
            throw new DomainException("TENANT_SCOPE_INVALID", "租户范围只支持 ALL、OWN 或 SHARED", HttpStatus.BAD_REQUEST);
        }
        PublishedModule currentPublished = published(context, moduleCode);
        List<RuntimeListFilter> filters = parseListFilters(filtersJson);
        QueryAccessPolicy queryPolicy = queryAccessPolicy(context, moduleCode, currentPublished, filters, sortField);
        List<RecordAccess> accessible = new ArrayList<>();
        if (!"SHARED".equals(normalizedTenantScope)) {
            LambdaQueryWrapper<BusinessRecord> ownQuery = baseBoundary(context, currentPublished.configuration().moduleId());
            applyLifecycleState(ownQuery, lifecycleState);
            applyDataScope(ownQuery, context, moduleCode, "LIST");
            recordService.selectList(ownQuery).forEach(record -> accessible.add(
                    new RecordAccess(record, currentPublished, context, null)));
        }
        if (!"OWN".equals(normalizedTenantScope)) {
            for (BizTenantShare share : activeTargetShares(context)) {
                if (!shareAllows(share, "LIST")) continue;
                BusinessRecord record = recordService.selectById(share.getRecordId());
                if (record == null || !context.systemId().equals(record.getSystemId())
                        || !share.getSourceTenantId().equals(record.getTenantId())
                        || !lifecycleMatches(record, lifecycleState)) continue;
                ConfiguredModule sourceModule = moduleService.selectById(record.getModuleId());
                if (sourceModule == null || !context.systemId().equals(sourceModule.getSystemId())
                        || !moduleCode.equalsIgnoreCase(sourceModule.getCode())
                        || !withinSharedScope(context, moduleCode, "LIST", record)) continue;
                AuthenticatedContext sourceContext = tenantContext(context, record.getTenantId());
                accessible.add(new RecordAccess(record, published(sourceContext, moduleCode), sourceContext, share));
            }
        }
        String normalizedSearch = search == null ? "" : search.strip();
        if (normalizedSearch.length() > 200) {
            throw new DomainException("LIST_SEARCH_INVALID", "搜索内容不能超过 200 个字符", HttpStatus.BAD_REQUEST);
        }
        Map<Long, Map<String, JsonNode>> queryValues = new HashMap<>();
        accessible.removeIf(access -> !matchesListQuery(access, normalizedSearch, filters, queryPolicy, queryValues));
        accessible.sort(listComparator(sortField, sortDirection, queryPolicy, queryValues));
        int from = Math.min((pageNumber - 1) * pageSize, accessible.size());
        int to = Math.min(from + pageSize, accessible.size());
        List<RuntimeRecordView> views = accessible.subList(from, to).stream()
                .map(access -> view(context, moduleCode, "LIST", List.of("PAGE"), access)).toList();
        return new RuntimeRecordList(views, accessible.size(), pageNumber, pageSize);
    }

    private List<RuntimeListFilter> parseListFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) return List.of();
        try {
            List<RuntimeListFilter> filters = objectMapper.readValue(filtersJson,
                    new TypeReference<List<RuntimeListFilter>>() { });
            if (filters.size() > 10) {
                throw new DomainException("LIST_FILTER_INVALID", "组合筛选最多支持 10 个条件", HttpStatus.BAD_REQUEST);
            }
            return filters;
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("LIST_FILTER_INVALID", "组合筛选格式无效", HttpStatus.BAD_REQUEST);
        }
    }

    private QueryAccessPolicy queryAccessPolicy(
            AuthenticatedContext context, String moduleCode, PublishedModule published,
            List<RuntimeListFilter> filters, String sortField) {
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, "LIST", List.of("PAGE"),
                published.fields().stream().map(PublishedField::code).toList());
        Set<String> queryableFields = published.fields().stream()
                .filter(field -> {
                    FieldAccessDecision decision = decisions.get(field.code());
                    return decision != null && decision.readable()
                            && (decision.maskStrategy() == null || decision.maskStrategy().isBlank());
                })
                .map(PublishedField::code).collect(java.util.stream.Collectors.toSet());
        Set<String> searchableFields = published.fields().stream()
                .filter(PublishedField::searchable).map(PublishedField::code)
                .filter(queryableFields::contains).collect(java.util.stream.Collectors.toSet());
        Set<String> builtIns = Set.of("title", "recordNumber", "status", "ownerMemberId", "departmentId",
                "createdAt", "updatedAt", "dataTenantName");
        for (RuntimeListFilter filter : filters) {
            if (filter == null || filter.fieldCode() == null || filter.fieldCode().isBlank()
                    || (!builtIns.contains(filter.fieldCode()) && !queryableFields.contains(filter.fieldCode()))) {
                throw new DomainException("LIST_FILTER_FIELD_FORBIDDEN", "筛选字段不存在、不可读或已脱敏", HttpStatus.FORBIDDEN);
            }
            String operator = filter.operator() == null ? "" : filter.operator().strip().toUpperCase(Locale.ROOT);
            if (!Set.of("EQ", "NE", "CONTAINS", "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY").contains(operator)) {
                throw new DomainException("LIST_FILTER_OPERATOR_INVALID", "筛选运算符不受支持", HttpStatus.BAD_REQUEST);
            }
        }
        String normalizedSort = sortField == null || sortField.isBlank() ? "updatedAt" : sortField.strip();
        if (!builtIns.contains(normalizedSort) && !queryableFields.contains(normalizedSort)) {
            throw new DomainException("LIST_SORT_FIELD_FORBIDDEN", "排序字段不存在、不可读或已脱敏", HttpStatus.FORBIDDEN);
        }
        return new QueryAccessPolicy(queryableFields, searchableFields, builtIns);
    }

    private boolean matchesListQuery(
            RecordAccess access, String search, List<RuntimeListFilter> filters,
            QueryAccessPolicy policy, Map<Long, Map<String, JsonNode>> values) {
        if (!search.isBlank()) {
            String needle = search.toLowerCase(Locale.ROOT);
            boolean matched = containsIgnoreCase(access.record().getTitle(), needle)
                    || containsIgnoreCase(access.record().getRecordNumber(), needle);
            if (!matched) {
                Map<String, JsonNode> raw = queryValues(access, values);
                matched = policy.searchableFields().stream()
                        .map(raw::get).filter(java.util.Objects::nonNull)
                        .anyMatch(value -> containsIgnoreCase(value.asText(), needle));
            }
            if (!matched) return false;
        }
        for (RuntimeListFilter filter : filters) {
            JsonNode actual = queryValue(access, filter.fieldCode(), policy, values);
            if (!matchesFilter(actual, filter)) return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private boolean matchesFilter(JsonNode actual, RuntimeListFilter filter) {
        String operator = filter.operator().strip().toUpperCase(Locale.ROOT);
        boolean empty = actual == null || actual.isNull() || actual.asText().isBlank();
        if ("EMPTY".equals(operator)) return empty;
        if ("NOT_EMPTY".equals(operator)) return !empty;
        if (empty) return "NE".equals(operator);
        JsonNode expected = TextNode.valueOf(filter.value() == null ? "" : filter.value());
        int compared = compareQueryValues(actual, expected);
        return switch (operator) {
            case "EQ" -> compared == 0;
            case "NE" -> compared != 0;
            case "CONTAINS" -> actual.asText().toLowerCase(Locale.ROOT)
                    .contains(expected.asText().toLowerCase(Locale.ROOT));
            case "GT" -> compared > 0;
            case "GTE" -> compared >= 0;
            case "LT" -> compared < 0;
            case "LTE" -> compared <= 0;
            default -> false;
        };
    }

    private Comparator<RecordAccess> listComparator(
            String sortField, String sortDirection, QueryAccessPolicy policy,
            Map<Long, Map<String, JsonNode>> values) {
        String field = sortField == null || sortField.isBlank() ? "updatedAt" : sortField.strip();
        String direction = sortDirection == null ? "DESC" : sortDirection.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(direction)) {
            throw new DomainException("LIST_SORT_DIRECTION_INVALID", "排序方向只支持 ASC 或 DESC", HttpStatus.BAD_REQUEST);
        }
        Comparator<RecordAccess> comparator = (left, right) -> compareQueryValues(
                queryValue(left, field, policy, values), queryValue(right, field, policy, values));
        if ("DESC".equals(direction)) comparator = comparator.reversed();
        return comparator.thenComparing(item -> item.record().getId(), Comparator.reverseOrder());
    }

    private JsonNode queryValue(
            RecordAccess access, String field, QueryAccessPolicy policy,
            Map<Long, Map<String, JsonNode>> values) {
        BusinessRecord record = access.record();
        return switch (field) {
            case "title" -> textNode(record.getTitle());
            case "recordNumber" -> textNode(record.getRecordNumber());
            case "status" -> textNode(record.getStatus());
            case "ownerMemberId" -> record.getOwnerMemberId() == null ? null : LongNode.valueOf(record.getOwnerMemberId());
            case "departmentId" -> record.getDepartmentId() == null ? null : LongNode.valueOf(record.getDepartmentId());
            case "createdAt" -> textNode(record.getCreatedAt() == null ? null : record.getCreatedAt().toString());
            case "updatedAt" -> textNode(record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString());
            case "dataTenantName" -> textNode(tenantName(record.getTenantId()));
            default -> policy.queryableFields().contains(field) ? queryValues(access, values).get(field) : null;
        };
    }

    private Map<String, JsonNode> queryValues(RecordAccess access, Map<Long, Map<String, JsonNode>> values) {
        return values.computeIfAbsent(access.record().getId(), ignored ->
                readRawValues(access.record().getId(), access.published().fields()));
    }

    private TextNode textNode(String value) {
        return value == null ? null : TextNode.valueOf(value);
    }

    private int compareQueryValues(JsonNode left, JsonNode right) {
        if (left == null || left.isNull()) return right == null || right.isNull() ? 0 : -1;
        if (right == null || right.isNull()) return 1;
        try {
            return new BigDecimal(left.asText()).compareTo(new BigDecimal(right.asText()));
        } catch (NumberFormatException ignored) {
            return left.asText().compareToIgnoreCase(right.asText());
        }
    }

    @Transactional(readOnly = true)
    public List<TenantShareTarget> shareTargets(
            AuthenticatedContext context, String moduleCode, String traceId) {
        requireAction(context, moduleCode, "SHARE", traceId);
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId())
                        .eq(SystemTenant::getStatus, "ACTIVE")
                        .ne(SystemTenant::getId, context.tenantId())
                        .orderByAsc(SystemTenant::getName, SystemTenant::getId))
                .stream().map(tenant -> new TenantShareTarget(tenant.getId(), tenant.getCode(), tenant.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RuntimeTenantShareView> shares(
            AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        requireAction(context, moduleCode, "SHARE", traceId);
        PublishedModule published = published(context, moduleCode);
        requireAnyBoundaryRecord(context, published.configuration().moduleId(), recordId);
        if (!withinScope(context, moduleCode, "SHARE", recordId, published.configuration().moduleId())) {
            deny(context, moduleCode, "SHARE", traceId,
                    Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
        }
        return tenantShareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                        .eq(BizTenantShare::getSystemId, context.systemId())
                        .eq(BizTenantShare::getSourceTenantId, context.tenantId())
                        .eq(BizTenantShare::getRecordId, recordId)
                        .orderByDesc(BizTenantShare::getGrantedAt, BizTenantShare::getId))
                .stream().map(share -> shareView(moduleCode, share)).toList();
    }

    @Transactional
    public RuntimeTenantShareView grantShare(
            AuthenticatedContext context, String moduleCode, Long recordId,
            CreateTenantShareRequest request, String traceId) {
        requireAction(context, moduleCode, "SHARE", traceId);
        PublishedModule published = published(context, moduleCode);
        BusinessRecord record = requireBoundaryRecord(context, published.configuration().moduleId(), recordId);
        if (!withinScope(context, moduleCode, "SHARE", recordId, published.configuration().moduleId())) {
            deny(context, moduleCode, "SHARE", traceId,
                    Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
        }
        SystemTenant target = tenantService.selectById(request.targetTenantId());
        if (target == null || !context.systemId().equals(target.getSystemId())
                || !"ACTIVE".equals(target.getStatus()) || context.tenantId().equals(target.getId())) {
            throw new DomainException("SHARE_TARGET_INVALID", "目标租户必须是同一系统内的其他有效租户",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new DomainException("SHARE_EXPIRY_INVALID", "共享失效时间必须晚于当前时间",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<String> allowedActions = normalizeShareActions(published, request.allowedActions());
        List<BizTenantShare> existingRows = tenantShareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                .eq(BizTenantShare::getSystemId, context.systemId())
                .eq(BizTenantShare::getSourceTenantId, context.tenantId())
                .eq(BizTenantShare::getTargetTenantId, target.getId())
                .eq(BizTenantShare::getRecordId, recordId));
        BizTenantShare share = existingRows.stream().findFirst().orElseGet(BizTenantShare::new);
        if (share.getId() != null && "ACTIVE".equals(effectiveShareStatus(share))
                && (request.version() == null || !request.version().equals(share.getVersion()))) {
            throw new DomainException("SHARE_VERSION_REQUIRED", "修改现有共享授权前请刷新授权版本", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        share.setSystemId(context.systemId());
        share.setSourceTenantId(context.tenantId());
        share.setTargetTenantId(target.getId());
        share.setRecordId(recordId);
        share.setPermissionJson(writeJson(Map.of(
                "moduleCode", moduleCode,
                "allowedActions", allowedActions,
                "sourceTenantId", context.tenantId(),
                "targetTenantId", target.getId(),
                "grantorPermission", permissionSnapshot(context, moduleCode, "SHARE", "PAGE"))));
        share.setStatus("ACTIVE");
        share.setExpiresAt(request.expiresAt());
        share.setGrantedByMemberId(context.memberId());
        share.setGrantedAt(now);
        share.setRevokedAt(null);
        if (share.getId() == null) {
            share.setVersion(0);
            tenantShareService.insert(share);
        } else if (tenantShareService.updateById(share) == 0) {
            throw new DomainException("SHARE_VERSION_CONFLICT", "共享授权已被其他操作修改", HttpStatus.CONFLICT);
        }
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_SHARED", "TENANT_SHARE", share.getId().toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "SHARE", "PAGE"), Map.of(
                        "recordId", recordId, "moduleCode", moduleCode, "sourceTenantId", context.tenantId(),
                        "targetTenantId", target.getId(), "allowedActions", allowedActions,
                        "effectiveAt", now, "expiresAt", request.expiresAt() == null ? "" : request.expiresAt()));
        return shareView(moduleCode, tenantShareService.selectById(share.getId()));
    }

    @Transactional
    public RuntimeTenantShareView revokeShare(
            AuthenticatedContext context, String moduleCode, Long recordId, Long shareId,
            RevokeTenantShareRequest request, String traceId) {
        requireAction(context, moduleCode, "SHARE", traceId);
        PublishedModule published = published(context, moduleCode);
        requireAnyBoundaryRecord(context, published.configuration().moduleId(), recordId);
        if (!withinScope(context, moduleCode, "SHARE", recordId, published.configuration().moduleId())) {
            deny(context, moduleCode, "SHARE", traceId,
                    Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
        }
        BizTenantShare share = tenantShareService.selectById(shareId);
        if (share == null || !context.systemId().equals(share.getSystemId())
                || !context.tenantId().equals(share.getSourceTenantId())
                || !recordId.equals(share.getRecordId())) {
            throw new DomainException("SHARE_NOT_FOUND", "共享授权不存在", HttpStatus.NOT_FOUND);
        }
        if (!request.version().equals(share.getVersion())) {
            throw new DomainException("SHARE_VERSION_CONFLICT", "共享授权已被其他操作修改", HttpStatus.CONFLICT);
        }
        if (!"ACTIVE".equals(effectiveShareStatus(share))) {
            throw new DomainException("SHARE_NOT_ACTIVE", "共享授权已经失效或撤销", HttpStatus.CONFLICT);
        }
        share.setStatus("REVOKED");
        share.setRevokedAt(LocalDateTime.now());
        if (tenantShareService.updateById(share) == 0) {
            throw new DomainException("SHARE_VERSION_CONFLICT", "共享授权已被其他操作修改", HttpStatus.CONFLICT);
        }
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_SHARE_REVOKED", "TENANT_SHARE", shareId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "SHARE", "PAGE"), Map.of(
                        "recordId", recordId, "moduleCode", moduleCode,
                        "sourceTenantId", share.getSourceTenantId(), "targetTenantId", share.getTargetTenantId(),
                        "reason", request.reason().strip()));
        return shareView(moduleCode, tenantShareService.selectById(shareId));
    }

    @Transactional
    public RuntimeRecordView detail(AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "DETAIL", recordId, false, traceId);
        if (access.share() != null) recordShareUsage(context, access.share(), "DETAIL", "SUCCESS");
        return view(context, moduleCode, "DETAIL", List.of("PAGE"), access);
    }

    /**
     * Resolves the record again for the PRINT action instead of reusing a page response. Printing therefore
     * requires both DETAIL and PRINT record scopes, and fields are protected by the PAGE ∩ FILE channel policy.
     */
    @Transactional
    public RuntimeRecordView printView(
            AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        requireRecordAccess(context, moduleCode, "DETAIL", recordId, false, traceId);
        RecordAccess access = requireRecordAccess(context, moduleCode, "PRINT", recordId, false, traceId);
        RuntimeRecordView protectedView = view(context, moduleCode, "PRINT", List.of("PAGE", "FILE"), access);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_PRINT_AUTHORIZED", "BUSINESS_RECORD", recordId.toString(),
                "SUCCESS", permissionSnapshot(context, moduleCode, "PRINT", "PAGE∩FILE"),
                Map.of("moduleCode", moduleCode, "visibleFieldCodes", protectedView.fields().keySet(),
                        "dataTenantId", protectedView.dataTenantId()));
        if (access.share() != null) recordShareUsage(context, access.share(), "PRINT", "SUCCESS");
        return protectedView;
    }

    @Transactional
    public RuntimeRecordTimeline timeline(
            AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "DETAIL", recordId, false, traceId);
        PublishedModule published = access.published();

        List<AuditEvent> events = auditEventService.selectList(Wrappers.<AuditEvent>lambdaQuery()
                .eq(AuditEvent::getSystemId, context.systemId())
                .eq(AuditEvent::getObjectType, "BUSINESS_RECORD")
                .eq(AuditEvent::getObjectId, recordId.toString())
                .eq(AuditEvent::getResultCode, "SUCCESS")
                .in(AuditEvent::getEventCode, "BUSINESS_RECORD_CREATED", "BUSINESS_RECORD_UPDATED",
                        "BUSINESS_RECORD_ARCHIVED", "BUSINESS_RECORD_DELETED", "BUSINESS_RECORD_RESTORED",
                        "BUSINESS_RECORD_TRANSFERRED", "BUSINESS_RECORD_CONVERTED")
                .orderByDesc(AuditEvent::getOccurredAt, AuditEvent::getId)
                .last("limit 100"));
        if (events.isEmpty()) {
            return new RuntimeRecordTimeline(List.of());
        }

        List<Long> eventIds = events.stream().map(AuditEvent::getId).toList();
        Map<Long, List<AuditFieldChange>> changesByEvent = fieldChangeService.selectList(
                        Wrappers.<AuditFieldChange>lambdaQuery()
                                .in(AuditFieldChange::getAuditEventId, eventIds)
                                .orderByAsc(AuditFieldChange::getId))
                .stream().collect(java.util.stream.Collectors.groupingBy(
                        AuditFieldChange::getAuditEventId, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Map<String, PublishedField> fieldsByCode = published.fields().stream()
                .collect(java.util.stream.Collectors.toMap(PublishedField::code, field -> field));
        Map<String, FieldAccessDecision> fieldAccess = fieldPolicyResolver.resolve(
                context, moduleCode, "DETAIL", "PAGE", published.fields().stream().map(PublishedField::code).toList());
        Set<Long> memberIds = events.stream().map(AuditEvent::getMemberId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, String> memberNames = memberIds.isEmpty() ? Map.of() : memberService.selectList(
                        Wrappers.<SystemMember>lambdaQuery()
                                .eq(SystemMember::getSystemId, context.systemId())
                                .in(SystemMember::getId, memberIds))
                .stream().collect(java.util.stream.Collectors.toMap(SystemMember::getId, SystemMember::getDisplayName));

        List<RuntimeRecordTimelineEntry> entries = events.stream().map(event -> {
            List<RuntimeRecordTimelineChange> changes = changesByEvent.getOrDefault(event.getId(), List.of()).stream()
                    .map(change -> timelineChange(change, fieldsByCode.get(change.getFieldCode()), fieldAccess.get(change.getFieldCode())))
                    .toList();
            return new RuntimeRecordTimelineEntry(
                    event.getId(), event.getEventCode(), timelineLabel(event.getEventCode()),
                    event.getActorAccountId(), event.getMemberId(),
                    memberNames.getOrDefault(event.getMemberId(), "未知成员"), event.getOccurredAt(), changes);
        }).toList();
        return new RuntimeRecordTimeline(entries);
    }

    private String timelineLabel(String eventCode) {
        return switch (eventCode) {
            case "BUSINESS_RECORD_CREATED" -> "新建记录";
            case "BUSINESS_RECORD_UPDATED" -> "编辑记录";
            case "BUSINESS_RECORD_ARCHIVED" -> "归档记录";
            case "BUSINESS_RECORD_DELETED" -> "删除记录";
            case "BUSINESS_RECORD_RESTORED" -> "恢复记录";
            case "BUSINESS_RECORD_TRANSFERRED" -> "转交负责人";
            case "BUSINESS_RECORD_CONVERTED" -> "转化记录";
            default -> eventCode;
        };
    }

    private RuntimeRecordTimelineChange timelineChange(
            AuditFieldChange change, PublishedField field, FieldAccessDecision decision) {
        boolean masked = field == null || decision == null || !decision.readable();
        JsonNode before = masked ? null : fieldPolicyResolver.protect(parseAuditValue(change.getBeforeValueJson()), decision);
        JsonNode after = masked ? null : fieldPolicyResolver.protect(parseAuditValue(change.getAfterValueJson()), decision);
        return new RuntimeRecordTimelineChange(change.getFieldCode(),
                field == null ? change.getFieldCode() : field.name(), change.getValueType(), before, after, masked);
    }

    private JsonNode parseAuditValue(String value) {
        try {
            return value == null ? objectMapper.nullNode() : objectMapper.readTree(value);
        } catch (Exception exception) {
            return objectMapper.nullNode();
        }
    }

    @Transactional
    public RuntimeRecordView channelView(AuthenticatedContext context, String moduleCode, Long recordId,
                                         String channel, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "DETAIL", recordId, true, traceId);
        List<String> channels = "PAGE".equalsIgnoreCase(channel)
                ? List.of("PAGE") : List.of("PAGE", channel);
        RuntimeRecordView protectedView = view(context, moduleCode, "DETAIL", channels, access);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "AUTHORIZATION_CHANNEL_VIEWED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "DETAIL", String.join("∩", channels)),
                Map.of("moduleCode", moduleCode, "channels", channels,
                        "visibleFieldCodes", protectedView.fields().keySet()));
        if (access.share() != null) recordShareUsage(context, access.share(), "DETAIL", "SUCCESS");
        return protectedView;
    }

    @Transactional
    public RuntimeRecordView create(
            AuthenticatedContext context,
            String moduleCode,
            CreateRuntimeRecordRequest request,
            String traceId) {
        requireAction(context, moduleCode, "CREATE", traceId);
        PublishedModule published = published(context, moduleCode);
        Map<String, JsonNode> normalizedValues = validateFields(
                context, context, moduleCode, "CREATE", published.fields(), request.fields(), traceId, null);
        ruleIndexService.validate(published.configuration(), "CREATE", normalizedValues);
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
        replaceValues(context, record.getId(), published.fields(), normalizedValues);
        replaceParticipants(context, record.getId(), ownership.participantMemberIds());
        ruleIndexService.syncIndexes(context, published.configuration(), record.getId(), normalizedValues);

        Long auditEventId = auditRecorder.recordWithPermissionSnapshot(
                traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_CREATED", "BUSINESS_RECORD", record.getId().toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "CREATE", "PAGE"),
                Map.of("moduleCode", moduleCode, "configVersionId", published.configuration().versionId(),
                        "fields", normalizedValues, "participantMemberIds", ownership.participantMemberIds()));
        recordFieldChanges(context, moduleCode, auditEventId, Map.of(), normalizedValues, published.fields());
        return view(context, moduleCode, "CREATE", List.of("PAGE"), recordService.selectById(record.getId()), published);
    }

    public RuntimeImportValidation validateImport(
            AuthenticatedContext context,
            String moduleCode,
            Long recordId,
            CreateRuntimeRecordRequest request,
            Integer expectedVersion,
            String traceId) {
        if (recordId == null) {
            requireAction(context, moduleCode, "CREATE", traceId);
            PublishedModule published = published(context, moduleCode);
            Map<String, JsonNode> normalized = validateFields(
                    context, context, moduleCode, "CREATE", published.fields(), request.fields(), traceId, null);
            ruleIndexService.validate(published.configuration(), "CREATE", normalized);
            validateOwnership(context, request.ownerMemberId(), request.departmentId(),
                    request.participantMemberIds(), traceId, null);
            return new RuntimeImportValidation(null, null, normalized, null);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, "UPDATE", recordId, true, traceId);
        BusinessRecord record = access.record();
        if (expectedVersion == null || !expectedVersion.equals(record.getVersion())) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> beforeFields = readRawValues(recordId, access.published().fields());
        Map<String, JsonNode> normalized = validateFields(context, access.sourceContext(), moduleCode, "UPDATE",
                access.published().fields(), request.fields(), traceId, recordId);
        ruleIndexService.validate(access.published().configuration(), "UPDATE", normalized);
        validateOwnership(access.sourceContext(), request.ownerMemberId(), request.departmentId(),
                request.participantMemberIds(), traceId, recordId);
        CreateRuntimeRecordRequest before = new CreateRuntimeRecordRequest(
                record.getTitle(), record.getRecordNumber(), record.getStatus(), record.getOwnerMemberId(),
                record.getDepartmentId(), participantIds(recordId), beforeFields);
        return new RuntimeImportValidation(recordId, record.getVersion(), normalized, before);
    }

    @Transactional
    public RuntimeRecordView update(
            AuthenticatedContext context,
            String moduleCode,
            Long recordId,
            UpdateRuntimeRecordRequest request,
            String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "UPDATE", recordId, true, traceId);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();
        if (!request.version().equals(record.getVersion())) {
            recordVersionConflict(context, moduleCode, recordId, request.version(), record.getVersion(), traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> before = readRawValues(recordId, published.fields());
        Map<String, JsonNode> normalizedValues = validateFields(
                context, access.sourceContext(), moduleCode, "UPDATE", published.fields(), request.fields(), traceId, recordId);
        ruleIndexService.validate(published.configuration(), "UPDATE", normalizedValues);
        Ownership ownership = validateOwnership(
                access.sourceContext(), request.ownerMemberId(), request.departmentId(), request.participantMemberIds(),
                traceId, recordId);

        record.setUpdatedConfigVersionId(published.configuration().versionId());
        record.setRecordNumber(blankToNull(request.recordNumber()));
        record.setTitle(request.title().strip());
        record.setStatus(blankToDefault(request.status(), "ACTIVE"));
        record.setOwnerMemberId(ownership.ownerMemberId());
        record.setDepartmentId(ownership.departmentId());
        record.setUpdatedByMemberId(context.memberId());
        record.setUpdatedAt(null);
        if (recordService.updateById(record) == 0) {
            recordVersionConflict(context, moduleCode, recordId, request.version(), null, traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        replaceValues(access.sourceContext(), recordId, published.fields(), normalizedValues);
        replaceParticipants(access.sourceContext(), recordId, ownership.participantMemberIds());
        ruleIndexService.syncIndexes(access.sourceContext(), published.configuration(), recordId, normalizedValues);

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("moduleCode", moduleCode);
        auditDetail.put("configVersionId", published.configuration().versionId());
        auditDetail.put("beforeFields", before);
        auditDetail.put("afterFields", normalizedValues);
        auditDetail.put("participantMemberIds", ownership.participantMemberIds());
        auditDetail.put("actorTenantId", context.tenantId());
        auditDetail.put("dataTenantId", record.getTenantId());
        if (access.share() != null) auditDetail.put("shareId", access.share().getId());
        Long auditEventId = auditRecorder.recordWithPermissionSnapshot(
                traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_UPDATED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "UPDATE", "PAGE"),
                auditDetail);
        recordFieldChanges(context, moduleCode, auditEventId, before, normalizedValues, published.fields());
        if (access.share() != null) recordShareUsage(context, access.share(), "UPDATE", "SUCCESS");
        return view(context, moduleCode, "UPDATE", List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    @Transactional
    public RuntimeRecordView applyFlowStatus(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode,
            String expectedCurrentStatus, String targetStatus, String traceId) {
        String action = actionCode == null ? "" : actionCode.strip().toUpperCase(Locale.ROOT);
        String target = targetStatus == null ? "" : targetStatus.strip().toUpperCase(Locale.ROOT);
        if (!target.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new DomainException("FLOW_STATUS_MAPPING_INVALID", "Flow 业务状态映射目标无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, action, recordId, true, traceId);
        BusinessRecord record = access.record();
        if (expectedCurrentStatus != null && !expectedCurrentStatus.isBlank()
                && !expectedCurrentStatus.equalsIgnoreCase(record.getStatus())) {
            throw new DomainException("FLOW_STATUS_MAPPING_INCOMPATIBLE",
                    "业务数据当前状态与 Flow 映射前置状态不兼容", HttpStatus.CONFLICT);
        }
        String before = record.getStatus();
        record.setStatus(target);
        record.setUpdatedByMemberId(context.memberId());
        record.setUpdatedAt(LocalDateTime.now());
        if (recordService.updateById(record) != 1) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "FLOW_BUSINESS_STATUS_MAPPED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, action, "FLOW"),
                Map.of("moduleCode", moduleCode, "actionCode", action, "beforeStatus", before,
                        "afterStatus", target));
        if (access.share() != null) recordShareUsage(context, access.share(), action, "SUCCESS");
        return view(context, moduleCode, action, List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), access.published(), access.sourceContext(), access.share()));
    }

    @Transactional(readOnly = true)
    public RuntimeRecordTransferPreview transferPreview(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordTransferRequest request, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "TRANSFER", recordId, true, traceId);
        BusinessRecord record = access.record();
        requireRecordVersion(context, moduleCode, "TRANSFER", record, request.version(), traceId);
        List<Long> currentParticipants = participantIds(recordId);
        List<Long> targetParticipants = request.participantMemberIds() == null
                ? currentParticipants : new LinkedHashSet<>(request.participantMemberIds()).stream().sorted().toList();
        validateTransferTarget(access.sourceContext(), moduleCode, request.ownerMemberId(), request.departmentId(),
                targetParticipants, traceId, recordId);
        if (java.util.Objects.equals(record.getOwnerMemberId(), request.ownerMemberId())
                && java.util.Objects.equals(record.getDepartmentId(), request.departmentId())
                && currentParticipants.equals(targetParticipants)) {
            throw new DomainException("TRANSFER_NO_CHANGE", "负责人、所属部门和相关人均未发生变化", HttpStatus.CONFLICT);
        }
        return new RuntimeRecordTransferPreview(recordId, record.getVersion(),
                record.getOwnerMemberId(), memberName(access.sourceContext(), record.getOwnerMemberId()),
                request.ownerMemberId(), memberName(access.sourceContext(), request.ownerMemberId()),
                record.getDepartmentId(), departmentName(access.sourceContext(), record.getDepartmentId()),
                request.departmentId(), departmentName(access.sourceContext(), request.departmentId()),
                currentParticipants, targetParticipants);
    }

    @Transactional
    public RuntimeRecordView transfer(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordTransferRequest request, String traceId) {
        RuntimeRecordTransferPreview preview = transferPreview(context, moduleCode, recordId, request, traceId);
        RecordAccess access = requireRecordAccess(context, moduleCode, "TRANSFER", recordId, true, traceId);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();

        BizRecordOwnerHistory history = new BizRecordOwnerHistory();
        history.setRecordId(recordId);
        history.setFromOwnerMemberId(record.getOwnerMemberId());
        history.setToOwnerMemberId(request.ownerMemberId());
        history.setFromDepartmentId(record.getDepartmentId());
        history.setToDepartmentId(request.departmentId());
        history.setReason(request.reason().strip());
        history.setChangedByMemberId(context.memberId());

        record.setOwnerMemberId(request.ownerMemberId());
        record.setDepartmentId(request.departmentId());
        record.setUpdatedByMemberId(context.memberId());
        record.setUpdatedAt(null);
        if (recordService.updateById(record) == 0) {
            recordOperationFailure(context, moduleCode, "TRANSFER", recordId, "RECORD_VERSION_CONFLICT",
                    "业务数据已被其他操作修改", traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        ownerHistoryService.insert(history);
        replaceParticipants(access.sourceContext(), recordId, preview.toParticipantMemberIds());
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("moduleCode", moduleCode);
        auditDetail.put("reason", request.reason().strip());
        auditDetail.put("preview", preview);
        auditDetail.put("actorTenantId", context.tenantId());
        auditDetail.put("dataTenantId", record.getTenantId());
        if (access.share() != null) auditDetail.put("shareId", access.share().getId());
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_TRANSFERRED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "TRANSFER", "PAGE"),
                auditDetail);
        if (access.share() != null) recordShareUsage(context, access.share(), "TRANSFER", "SUCCESS");
        return view(context, moduleCode, "TRANSFER", List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    @Transactional(readOnly = true)
    public RuntimeRecordConversionPreview conversionPreview(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionRequest request, String traceId) {
        return buildConversionPreview(context, moduleCode, recordId, request, traceId).preview();
    }

    @Transactional
    public RuntimeRecordConversionExecution convert(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionRequest request, String traceId) {
        ConversionPlan plan = buildConversionPreview(context, moduleCode, recordId, request, traceId);
        if (!plan.preview().executable()) {
            recordOperationFailure(context, moduleCode, "CONVERT", recordId, "CONVERSION_NOT_EXECUTABLE",
                    String.join("；", plan.preview().issues()), traceId);
            throw new DomainException(plan.preview().alreadyConverted()
                    ? "RECORD_ALREADY_CONVERTED" : "CONVERSION_MAPPING_INVALID",
                    String.join("；", plan.preview().issues()), HttpStatus.CONFLICT);
        }

        BizRecordConversion conversion = new BizRecordConversion();
        conversion.setSystemId(context.systemId());
        conversion.setTenantId(plan.source().getTenantId());
        conversion.setSourceRecordId(recordId);
        conversion.setActionId(plan.actionId());
        conversion.setStatus("PROCESSING");
        conversion.setMappingSnapshotJson(writeJson(Map.of(
                "sourceModuleCode", moduleCode,
                "targetModuleCode", request.targetModuleCode(),
                "sourceVersion", request.version(),
                "mappings", plan.preview().mappings())));
        conversion.setIdempotencyKey("convert:" + recordId + ":" + plan.target().configuration().moduleId());
        conversion.setCreatedByMemberId(context.memberId());
        conversion.setVersion(0);
        try {
            conversionService.insert(conversion);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            recordOperationFailure(context, moduleCode, "CONVERT", recordId, "RECORD_ALREADY_CONVERTED",
                    "该记录已转化到所选目标模块", traceId);
            throw new DomainException("RECORD_ALREADY_CONVERTED", "该记录已转化到所选目标模块", HttpStatus.CONFLICT);
        }

        BusinessRecord source = plan.source();
        List<Long> participantIds = plan.access().share() == null
                ? participantMapper.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                                .eq(BusinessRecordParticipant::getRecordId, recordId)
                                .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                        .stream().map(BusinessRecordParticipant::getSystemMemberId).toList()
                : List.of();
        RuntimeRecordView targetRecord = create(context, request.targetModuleCode(),
                new CreateRuntimeRecordRequest(plan.preview().targetTitle(), null, "ACTIVE",
                        plan.access().share() == null ? source.getOwnerMemberId() : context.memberId(),
                        plan.access().share() == null ? source.getDepartmentId() : null,
                        participantIds, plan.targetValues()), traceId);

        BizRecordConversionResult result = new BizRecordConversionResult();
        result.setConversionId(conversion.getId());
        result.setTargetModuleId(plan.target().configuration().moduleId());
        result.setTargetRecordId(targetRecord.id());
        result.setResultType("PRIMARY");
        conversionResultService.insert(result);
        conversion.setStatus("SUCCEEDED");
        conversion.setFinishedAt(LocalDateTime.now());
        conversionService.updateById(conversion);

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("moduleCode", moduleCode);
        auditDetail.put("conversionId", conversion.getId());
        auditDetail.put("targetModuleCode", request.targetModuleCode());
        auditDetail.put("targetRecordId", targetRecord.id());
        auditDetail.put("actorTenantId", context.tenantId());
        auditDetail.put("dataTenantId", source.getTenantId());
        if (plan.access().share() != null) auditDetail.put("shareId", plan.access().share().getId());
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_CONVERTED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "CONVERT", "PAGE"),
                auditDetail);
        if (plan.access().share() != null) recordShareUsage(context, plan.access().share(), "CONVERT", "SUCCESS");
        return new RuntimeRecordConversionExecution(conversion.getId(), request.targetModuleCode(), targetRecord);
    }

    @Transactional(readOnly = true)
    public RuntimeRecordConversions conversions(
            AuthenticatedContext context, String moduleCode, Long recordId, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "DETAIL", recordId, false, traceId);
        List<RuntimeRecordConversionLink> links = new ArrayList<>();
        List<BizRecordConversion> outgoing = conversionService.selectList(Wrappers.<BizRecordConversion>lambdaQuery()
                .eq(BizRecordConversion::getSystemId, context.systemId())
                .eq(BizRecordConversion::getTenantId, access.record().getTenantId())
                .eq(BizRecordConversion::getSourceRecordId, recordId)
                .eq(BizRecordConversion::getStatus, "SUCCEEDED"));
        appendOutgoingConversionLinks(context, outgoing, links);

        List<BizRecordConversionResult> incomingResults = conversionResultService.selectList(
                Wrappers.<BizRecordConversionResult>lambdaQuery()
                        .eq(BizRecordConversionResult::getTargetRecordId, recordId));
        if (!incomingResults.isEmpty()) {
            Map<Long, BizRecordConversion> incoming = conversionService.selectList(
                            Wrappers.<BizRecordConversion>lambdaQuery()
                                    .in(BizRecordConversion::getId,
                                            incomingResults.stream().map(BizRecordConversionResult::getConversionId).toList())
                                    .eq(BizRecordConversion::getSystemId, context.systemId())
                                    .eq(BizRecordConversion::getStatus, "SUCCEEDED"))
                    .stream().collect(java.util.stream.Collectors.toMap(BizRecordConversion::getId, item -> item));
            for (BizRecordConversion conversion : incoming.values()) {
                BusinessRecord source = recordService.selectById(conversion.getSourceRecordId());
                if (source == null) continue;
                ConfiguredModule module = moduleService.selectById(source.getModuleId());
                if (module != null && context.systemId().equals(module.getSystemId())) {
                    try {
                        requireRecordAccess(context, module.getCode(), "DETAIL", source.getId(), false, traceId);
                        links.add(new RuntimeRecordConversionLink(conversion.getId(), "SOURCE", module.getCode(),
                                module.getName(), source.getId(), source.getTitle(), conversion.getFinishedAt()));
                    } catch (DomainException ignored) {
                        // Conversion history never widens access to a source record.
                    }
                }
            }
        }
        links.sort(Comparator.comparing(RuntimeRecordConversionLink::convertedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new RuntimeRecordConversions(links);
    }

    private ConversionPlan buildConversionPreview(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionRequest request, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "CONVERT", recordId, true, traceId);
        PublishedModule sourcePublished = access.published();
        BusinessRecord source = access.record();
        requireRecordVersion(context, moduleCode, "CONVERT", source, request.version(), traceId);
        requireAction(context, request.targetModuleCode(), "CREATE", traceId);
        PublishedModule target = published(context, request.targetModuleCode());
        ConfiguredModule targetModule = moduleService.selectById(target.configuration().moduleId());

        JsonNode action = findPublishedAction(sourcePublished, "CONVERT");
        JsonNode targetConfig = findConversionTarget(action, request.targetModuleCode());
        List<String> issues = new ArrayList<>();
        if (targetConfig == null) {
            issues.add("所选目标模块未配置转化映射");
            targetConfig = objectMapper.createObjectNode();
        }
        JsonNode mappingsConfig = targetConfig.path("fieldMappings");
        if (!mappingsConfig.isObject()) {
            issues.add("转化动作缺少 fieldMappings 配置");
        }
        Map<String, PublishedField> sourceFields = sourcePublished.fields().stream()
                .collect(java.util.stream.Collectors.toMap(PublishedField::code, item -> item));
        Map<String, JsonNode> sourceValues = readRawValues(recordId, sourcePublished.fields());
        Map<String, FieldAccessDecision> sourceAccess = fieldPolicyResolver.resolve(
                context, moduleCode, "CONVERT", "PAGE", new ArrayList<>(sourceFields.keySet()));
        Map<String, FieldAccessDecision> targetAccess = fieldPolicyResolver.resolve(
                context, request.targetModuleCode(), "CREATE", "PAGE",
                target.fields().stream().map(PublishedField::code).toList());
        Map<String, JsonNode> targetValues = new LinkedHashMap<>();
        List<RuntimeRecordConversionMapping> mappings = new ArrayList<>();

        for (PublishedField targetField : target.fields()) {
            JsonNode sourceCodeNode = mappingsConfig.path(targetField.code());
            String sourceCode = sourceCodeNode.isTextual() ? sourceCodeNode.asText() : null;
            if (!targetField.required() && sourceCode == null) continue;
            String status = "READY";
            String message = "映射有效";
            JsonNode value = null;
            if (sourceCode == null || sourceCode.isBlank()) {
                status = "MISSING";
                message = "目标必填字段未配置来源";
                if (targetField.required()) issues.add(targetField.name() + "：未配置来源字段");
            } else if (!sourceFields.containsKey(sourceCode)) {
                status = "INVALID";
                message = "来源字段不存在";
                issues.add(targetField.name() + "：来源字段 " + sourceCode + " 不存在");
            } else if (sourceAccess.get(sourceCode) == null || !sourceAccess.get(sourceCode).readable()) {
                status = "DENIED";
                message = "来源字段无读取权限";
                issues.add(targetField.name() + "：来源字段无读取权限");
            } else if (targetAccess.get(targetField.code()) == null || !targetAccess.get(targetField.code()).writable()) {
                status = "DENIED";
                message = "目标字段无写入权限";
                issues.add(targetField.name() + "：目标字段无写入权限");
            } else {
                value = sourceValues.get(sourceCode);
                if (empty(value)) {
                    status = targetField.required() ? "MISSING" : "EMPTY";
                    message = targetField.required() ? "来源值为空" : "可选来源值为空";
                    if (targetField.required()) issues.add(targetField.name() + "：来源值为空");
                } else {
                    try {
                        value = normalize(context, targetField, value);
                        validateReferenceValue(context, targetField, value);
                        targetValues.put(targetField.code(), value);
                    } catch (IllegalArgumentException exception) {
                        status = "INVALID";
                        message = exception.getMessage();
                        issues.add(targetField.name() + "：" + exception.getMessage());
                    }
                }
            }
            mappings.add(new RuntimeRecordConversionMapping(targetField.code(), targetField.name(),
                    targetField.required(), sourceCode, value, status, message));
        }
        if (issues.isEmpty()) {
            try {
                ruleIndexService.validateCreatePreview(context, target.configuration(), targetValues);
            } catch (DomainException exception) {
                issues.add(exception.getMessage());
            }
        }
        boolean duplicate = hasCompletedConversion(access.sourceContext(), recordId, target.configuration().moduleId());
        if (duplicate) issues.add("该记录已经转化到所选目标模块，永久唯一关系禁止再次转化");
        long actionId = action.path("id").asLong(0);
        if (actionId == 0) issues.add("当前发布版本缺少有效的转化动作标识");
        String targetName = targetModule == null ? request.targetModuleCode() : targetModule.getName();
        RuntimeRecordConversionPreview preview = new RuntimeRecordConversionPreview(recordId, source.getVersion(),
                request.targetModuleCode(), targetName, source.getTitle(), mappings,
                List.copyOf(new LinkedHashSet<>(issues)), duplicate, issues.isEmpty());
        return new ConversionPlan(access, source, target, actionId, targetValues, preview);
    }

    private JsonNode findPublishedAction(PublishedModule published, String code) {
        for (JsonNode action : published.configuration().configuration().path("actions")) {
            if (code.equalsIgnoreCase(action.path("code").asText())) return action;
        }
        throw new DomainException("MODULE_ACTION_NOT_PUBLISHED", "当前发布版本未包含转化动作", HttpStatus.CONFLICT);
    }

    private JsonNode findConversionTarget(JsonNode action, String targetModuleCode) {
        JsonNode config = parseConfig(action.path("configJson").asText("{}"));
        if (targetModuleCode.equalsIgnoreCase(config.path("targetModuleCode").asText())) return config;
        for (JsonNode target : config.path("targets")) {
            if (targetModuleCode.equalsIgnoreCase(target.path("moduleCode").asText())) return target;
        }
        return null;
    }

    private boolean hasCompletedConversion(AuthenticatedContext context, Long sourceRecordId, Long targetModuleId) {
        List<BizRecordConversion> conversions = conversionService.selectList(Wrappers.<BizRecordConversion>lambdaQuery()
                .eq(BizRecordConversion::getSystemId, context.systemId())
                .eq(BizRecordConversion::getTenantId, context.tenantId())
                .eq(BizRecordConversion::getSourceRecordId, sourceRecordId)
                .eq(BizRecordConversion::getStatus, "SUCCEEDED"));
        if (conversions.isEmpty()) return false;
        return !conversionResultService.selectList(Wrappers.<BizRecordConversionResult>lambdaQuery()
                .in(BizRecordConversionResult::getConversionId,
                        conversions.stream().map(BizRecordConversion::getId).toList())
                .eq(BizRecordConversionResult::getTargetModuleId, targetModuleId)).isEmpty();
    }

    private void appendOutgoingConversionLinks(AuthenticatedContext context, List<BizRecordConversion> conversions,
                                               List<RuntimeRecordConversionLink> links) {
        if (conversions.isEmpty()) return;
        Map<Long, BizRecordConversion> byId = conversions.stream()
                .collect(java.util.stream.Collectors.toMap(BizRecordConversion::getId, item -> item));
        List<BizRecordConversionResult> results = conversionResultService.selectList(
                Wrappers.<BizRecordConversionResult>lambdaQuery()
                        .in(BizRecordConversionResult::getConversionId, byId.keySet()));
        for (BizRecordConversionResult result : results) {
            BusinessRecord target = recordService.selectById(result.getTargetRecordId());
            ConfiguredModule module = moduleService.selectById(result.getTargetModuleId());
            BizRecordConversion conversion = byId.get(result.getConversionId());
            if (target != null && module != null && context.systemId().equals(module.getSystemId())
                    && context.tenantId().equals(target.getTenantId())) {
                links.add(new RuntimeRecordConversionLink(conversion.getId(), "TARGET", module.getCode(),
                        module.getName(), target.getId(), target.getTitle(), conversion.getFinishedAt()));
            }
        }
    }

    private void validateTransferTarget(
            AuthenticatedContext context, String moduleCode, Long ownerMemberId, Long departmentId,
            List<Long> participantMemberIds, String traceId, Long recordId) {
        List<Long> memberIds = new ArrayList<>(participantMemberIds);
        memberIds.add(ownerMemberId);
        long activeMemberCount = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, context.systemId())
                .eq(SystemTenantMember::getTenantId, context.tenantId())
                .in(SystemTenantMember::getSystemMemberId, memberIds)
                .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().map(SystemTenantMember::getSystemMemberId).distinct().count();
        boolean departmentValid = departmentId == null || !departmentService.selectList(
                Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getSystemId, context.systemId())
                        .eq(SystemDepartment::getTenantId, context.tenantId())
                        .eq(SystemDepartment::getId, departmentId)
                        .eq(SystemDepartment::getStatus, "ACTIVE")).isEmpty();
        if (activeMemberCount != memberIds.stream().distinct().count() || !departmentValid) {
            recordOperationFailure(context, moduleCode, "TRANSFER", recordId, "TRANSFER_TARGET_INVALID",
                    "新负责人、所属部门或相关人不属于当前租户", traceId);
            throw new DomainException("TRANSFER_TARGET_INVALID", "新负责人、所属部门或相关人不属于当前租户",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private List<Long> participantIds(Long recordId) {
        return participantMapper.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, recordId)
                        .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                .stream().map(BusinessRecordParticipant::getSystemMemberId).toList();
    }

    private String memberName(AuthenticatedContext context, Long memberId) {
        if (memberId == null) return null;
        SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId()).eq(SystemMember::getId, memberId))
                .stream().findFirst().orElse(null);
        return member == null ? null : member.getDisplayName();
    }

    private String departmentName(AuthenticatedContext context, Long departmentId) {
        if (departmentId == null) return null;
        SystemDepartment department = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getSystemId, context.systemId())
                        .eq(SystemDepartment::getTenantId, context.tenantId())
                        .eq(SystemDepartment::getId, departmentId))
                .stream().findFirst().orElse(null);
        return department == null ? null : department.getName();
    }

    private void requireTransferOrConversionScope(
            AuthenticatedContext context, String moduleCode, String action, Long recordId,
            Long moduleId, String traceId) {
        if (!withinScope(context, moduleCode, action, recordId, moduleId)) {
            deny(context, moduleCode, action, traceId, Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
        }
    }

    private void requireRecordVersion(
            AuthenticatedContext context, String moduleCode, String action, BusinessRecord record,
            Integer requestedVersion, String traceId) {
        if (!requestedVersion.equals(record.getVersion())) {
            recordOperationFailure(context, moduleCode, action, record.getId(), "RECORD_VERSION_CONFLICT",
                    "业务数据已被其他操作修改", traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
    }

    private void recordOperationFailure(
            AuthenticatedContext context, String moduleCode, String action, Long recordId,
            String resultCode, String message, String traceId) {
        auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_" + action, "BUSINESS_RECORD", recordId.toString(), resultCode,
                Map.of("moduleCode", moduleCode, "message", message));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize record conversion snapshot", exception);
        }
    }

    private void recordVersionConflict(
            AuthenticatedContext context, String moduleCode, Long recordId,
            Integer requestedVersion, Integer currentVersion, String traceId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("moduleCode", moduleCode);
        detail.put("recordId", recordId);
        detail.put("requestedVersion", requestedVersion);
        detail.put("currentVersion", currentVersion);
        auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_UPDATE", "BUSINESS_RECORD", recordId.toString(), "RECORD_VERSION_CONFLICT", detail);
    }

    @Transactional(readOnly = true)
    public RuntimeRecordLifecycleImpact lifecycleImpact(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode, String traceId) {
        String action = actionCode.toUpperCase(Locale.ROOT);
        if (!Set.of("ARCHIVE", "DELETE", "RESTORE").contains(action)) {
            throw new DomainException("LIFECYCLE_ACTION_INVALID", "生命周期动作无效", HttpStatus.BAD_REQUEST);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, action, recordId, false, traceId);
        BusinessRecord record = access.record();
        long outgoing = relationMapper.selectCount(Wrappers.<BizRecordRelation>lambdaQuery()
                .eq(BizRecordRelation::getSystemId, context.systemId())
                .eq(BizRecordRelation::getTenantId, record.getTenantId())
                .eq(BizRecordRelation::getSourceRecordId, recordId));
        long incoming = relationMapper.selectCount(Wrappers.<BizRecordRelation>lambdaQuery()
                .eq(BizRecordRelation::getSystemId, context.systemId())
                .eq(BizRecordRelation::getTenantId, record.getTenantId())
                .eq(BizRecordRelation::getTargetRecordId, recordId));
        long participants = participantMapper.selectCount(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                .eq(BusinessRecordParticipant::getSystemId, context.systemId())
                .eq(BusinessRecordParticipant::getTenantId, record.getTenantId())
                .eq(BusinessRecordParticipant::getRecordId, recordId));
        String warning = switch (action) {
            case "ARCHIVE" -> "归档后记录从默认列表移除，可从归档列表恢复。";
            case "DELETE" -> "删除后记录进入回收站，正文和历史保留，可在校验通过后恢复。";
            default -> "恢复会按当前发布配置重新校验必填、唯一规则和引用关系。";
        };
        return new RuntimeRecordLifecycleImpact(recordId, action, lifecycleState(record),
                outgoing, incoming, participants, warning);
    }

    @Transactional
    public RuntimeRecordView archive(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordLifecycleRequest request, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "ARCHIVE", recordId, true, traceId);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();
        requireLifecycleVersion(context, moduleCode, "ARCHIVE", record, request.version(), traceId);
        String fromState = lifecycleState(record);
        record.setArchived(true);
        record.setArchivedAt(LocalDateTime.now());
        record.setUpdatedByMemberId(context.memberId());
        persistLifecycleRecord(context, moduleCode, "ARCHIVE", record, request.reason(), fromState,
                "ARCHIVED", "BUSINESS_RECORD_ARCHIVED", published, traceId);
        ruleIndexService.clearIndexes(recordId);
        if (access.share() != null) recordShareUsage(context, access.share(), "ARCHIVE", "SUCCESS");
        return view(context, moduleCode, "ARCHIVE", List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    @Transactional
    public RuntimeRecordView delete(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordLifecycleRequest request, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "DELETE", recordId, false, traceId);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();
        if (Boolean.TRUE.equals(record.getDeleted())) {
            throw new DomainException("RECORD_ALREADY_DELETED", "业务数据已在回收站", HttpStatus.CONFLICT);
        }
        requireLifecycleVersion(context, moduleCode, "DELETE", record, request.version(), traceId);
        String fromState = lifecycleState(record);
        record.setDeleted(true);
        record.setDeletedAt(LocalDateTime.now());
        record.setUpdatedByMemberId(context.memberId());
        persistLifecycleRecord(context, moduleCode, "DELETE", record, request.reason(), fromState,
                "DELETED", "BUSINESS_RECORD_DELETED", published, traceId);
        ruleIndexService.clearIndexes(recordId);
        if (access.share() != null) recordShareUsage(context, access.share(), "DELETE", "SUCCESS");
        return view(context, moduleCode, "DELETE", List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    @Transactional
    public RuntimeRecordView restore(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordLifecycleRequest request, String traceId) {
        requireAction(context, moduleCode, "RESTORE", traceId);
        PublishedModule published = published(context, moduleCode);
        BusinessRecord record = requireAnyBoundaryRecord(context, published.configuration().moduleId(), recordId);
        if (!Boolean.TRUE.equals(record.getArchived()) && !Boolean.TRUE.equals(record.getDeleted())) {
            throw new DomainException("RECORD_ALREADY_ACTIVE", "业务数据已经是正常状态", HttpStatus.CONFLICT);
        }
        requireLifecycleScope(context, moduleCode, "RESTORE", recordId, published.configuration().moduleId(), traceId);
        requireLifecycleVersion(context, moduleCode, "RESTORE", record, request.version(), traceId);
        Map<String, JsonNode> rawValues = readRawValues(recordId, published.fields());
        try {
            validateRestore(context, recordId, published, rawValues);
            ruleIndexService.validate(published.configuration(), "RESTORE", rawValues);
        } catch (DomainException exception) {
            recordLifecycleFailure(context, moduleCode, "RESTORE", recordId, exception.code(),
                    exception.getMessage(), traceId);
            throw exception;
        }
        String fromState = lifecycleState(record);
        record.setArchived(false);
        record.setArchivedAt(null);
        record.setDeleted(false);
        record.setDeletedAt(null);
        record.setUpdatedByMemberId(context.memberId());
        try {
            ruleIndexService.syncIndexes(context, published.configuration(), recordId, rawValues);
        } catch (DomainException exception) {
            recordLifecycleFailure(context, moduleCode, "RESTORE", recordId, exception.code(),
                    exception.getMessage(), traceId);
            throw exception;
        }
        persistLifecycleRecord(context, moduleCode, "RESTORE", record, request.reason(), fromState,
                "ACTIVE", "BUSINESS_RECORD_RESTORED", published, traceId);
        return view(context, moduleCode, "RESTORE", List.of("PAGE"), recordService.selectById(recordId), published);
    }

    private void validateRestore(
            AuthenticatedContext context, Long recordId, PublishedModule published, Map<String, JsonNode> values) {
        List<String> conflicts = new ArrayList<>();
        for (PublishedField field : published.fields()) {
            JsonNode value = values.get(field.code());
            if (field.required() && empty(value)) {
                conflicts.add(field.code() + ": 当前发布配置要求必填");
            }
            if ("REFERENCE".equals(field.type()) && !empty(value) && !recordId.equals(value.longValue())) {
                boolean targetAvailable = field.referenceModuleId() != null && !recordService.selectList(
                        boundary(context, field.referenceModuleId()).eq(BusinessRecord::getId, value.longValue())).isEmpty();
                if (!targetAvailable) {
                    conflicts.add(field.code() + ": 引用记录缺失、已归档或已删除");
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new DomainException("RECORD_RESTORE_CONFLICT", String.join("；", conflicts), HttpStatus.CONFLICT);
        }
    }

    private void requireLifecycleScope(
            AuthenticatedContext context, String moduleCode, String action, Long recordId,
            Long moduleId, String traceId) {
        if (!withinScope(context, moduleCode, action, recordId, moduleId)) {
            deny(context, moduleCode, action, traceId, Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
        }
    }

    private void requireLifecycleVersion(
            AuthenticatedContext context, String moduleCode, String action, BusinessRecord record,
            Integer requestedVersion, String traceId) {
        if (!requestedVersion.equals(record.getVersion())) {
            recordLifecycleFailure(context, moduleCode, action, record.getId(), "RECORD_VERSION_CONFLICT",
                    "业务数据已被其他操作修改", traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
    }

    private void persistLifecycleRecord(
            AuthenticatedContext context, String moduleCode, String action, BusinessRecord record,
            String reason, String fromState, String toState, String eventCode,
            PublishedModule published, String traceId) {
        Map<String, JsonNode> snapshotFields = readRawValues(record.getId(), published.fields());
        record.setUpdatedAt(null);
        if (recordService.updateById(record) == 0) {
            recordLifecycleFailure(context, moduleCode, action, record.getId(), "RECORD_VERSION_CONFLICT",
                    "业务数据已被其他操作修改", traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        BizRecordStateHistory history = new BizRecordStateHistory();
        history.setRecordId(record.getId());
        history.setFromStatus(fromState);
        history.setToStatus(toState);
        history.setReason(reason.strip());
        history.setSourceType("PAGE");
        history.setSourceId(traceId);
        history.setChangedByMemberId(context.memberId());
        stateHistoryService.insert(history);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("moduleCode", moduleCode);
        detail.put("action", action);
        detail.put("reason", reason.strip());
        detail.put("fromState", fromState);
        detail.put("toState", toState);
        detail.put("actorTenantId", context.tenantId());
        detail.put("dataTenantId", record.getTenantId());
        detail.put("snapshot", lifecycleSnapshot(record, snapshotFields));
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), eventCode, "BUSINESS_RECORD", record.getId().toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, action, "PAGE"), detail);
    }

    private Map<String, Object> lifecycleSnapshot(BusinessRecord record, Map<String, JsonNode> fields) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recordNumber", record.getRecordNumber());
        snapshot.put("title", record.getTitle());
        snapshot.put("status", record.getStatus());
        snapshot.put("ownerMemberId", record.getOwnerMemberId());
        snapshot.put("departmentId", record.getDepartmentId());
        snapshot.put("fields", fields);
        return snapshot;
    }

    private void recordLifecycleFailure(
            AuthenticatedContext context, String moduleCode, String action, Long recordId,
            String resultCode, String message, String traceId) {
        auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_" + action, "BUSINESS_RECORD", recordId.toString(), resultCode,
                Map.of("moduleCode", moduleCode, "message", message));
    }

    private String lifecycleState(BusinessRecord record) {
        if (Boolean.TRUE.equals(record.getDeleted())) return "DELETED";
        if (Boolean.TRUE.equals(record.getArchived())) return "ARCHIVED";
        return "ACTIVE";
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
                    field.path("searchable").asBoolean(false),
                    field.path("dictionaryId").isNumber() ? field.path("dictionaryId").longValue() : null,
                    field.path("referenceModuleId").isNumber() ? field.path("referenceModuleId").longValue() : null,
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
        return baseBoundary(context, moduleId)
                .eq(BusinessRecord::getArchived, false)
                .eq(BusinessRecord::getDeleted, false);
    }

    private void applyLifecycleState(LambdaQueryWrapper<BusinessRecord> query, String lifecycleState) {
        switch (lifecycleState.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> query.eq(BusinessRecord::getDeleted, false).eq(BusinessRecord::getArchived, false);
            case "ARCHIVED" -> query.eq(BusinessRecord::getDeleted, false).eq(BusinessRecord::getArchived, true);
            case "DELETED" -> query.eq(BusinessRecord::getDeleted, true);
            default -> throw new DomainException("LIFECYCLE_STATE_INVALID",
                    "记录状态筛选只支持 ACTIVE、ARCHIVED 或 DELETED", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean lifecycleMatches(BusinessRecord record, String lifecycleState) {
        return switch (lifecycleState.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> !Boolean.TRUE.equals(record.getDeleted()) && !Boolean.TRUE.equals(record.getArchived());
            case "ARCHIVED" -> !Boolean.TRUE.equals(record.getDeleted()) && Boolean.TRUE.equals(record.getArchived());
            case "DELETED" -> Boolean.TRUE.equals(record.getDeleted());
            default -> throw new DomainException("LIFECYCLE_STATE_INVALID",
                    "记录状态筛选只支持 ACTIVE、ARCHIVED 或 DELETED", HttpStatus.BAD_REQUEST);
        };
    }

    private LambdaQueryWrapper<BusinessRecord> baseBoundary(AuthenticatedContext context, Long moduleId) {
        return Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, context.systemId())
                .eq(BusinessRecord::getTenantId, context.tenantId())
                .eq(BusinessRecord::getModuleId, moduleId);
    }

    private BusinessRecord requireBoundaryRecord(AuthenticatedContext context, Long moduleId, Long recordId) {
        return recordService.selectList(boundary(context, moduleId).eq(BusinessRecord::getId, recordId))
                .stream().findFirst().orElseThrow(() ->
                        new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND));
    }

    private BusinessRecord requireAnyBoundaryRecord(AuthenticatedContext context, Long moduleId, Long recordId) {
        return recordService.selectList(baseBoundary(context, moduleId).eq(BusinessRecord::getId, recordId))
                .stream().findFirst().orElseThrow(() ->
                        new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND));
    }

    private RecordAccess requireRecordAccess(
            AuthenticatedContext context, String moduleCode, String actionCode,
            Long recordId, boolean activeOnly, String traceId) {
        requireAction(context, moduleCode, actionCode, traceId);
        PublishedModule currentPublished = published(context, moduleCode);
        LambdaQueryWrapper<BusinessRecord> ownBoundary = activeOnly
                ? boundary(context, currentPublished.configuration().moduleId())
                : baseBoundary(context, currentPublished.configuration().moduleId());
        BusinessRecord own = recordService.selectList(ownBoundary.eq(BusinessRecord::getId, recordId))
                .stream().findFirst().orElse(null);
        if (own != null) {
            if (!withinScope(context, moduleCode, actionCode, recordId, currentPublished.configuration().moduleId())) {
                deny(context, moduleCode, actionCode, traceId,
                        Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
            }
            return new RecordAccess(own, currentPublished, context, null);
        }

        BusinessRecord sharedRecord = recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                        .eq(BusinessRecord::getSystemId, context.systemId())
                        .eq(BusinessRecord::getId, recordId))
                .stream().findFirst().orElse(null);
        if (sharedRecord == null || (activeOnly && (Boolean.TRUE.equals(sharedRecord.getArchived())
                || Boolean.TRUE.equals(sharedRecord.getDeleted())))) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        }
        ConfiguredModule sourceModule = moduleService.selectById(sharedRecord.getModuleId());
        if (sourceModule == null || !context.systemId().equals(sourceModule.getSystemId())
                || !moduleCode.equalsIgnoreCase(sourceModule.getCode())) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        }
        BizTenantShare share = tenantShareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                        .eq(BizTenantShare::getSystemId, context.systemId())
                        .eq(BizTenantShare::getSourceTenantId, sharedRecord.getTenantId())
                        .eq(BizTenantShare::getTargetTenantId, context.tenantId())
                        .eq(BizTenantShare::getRecordId, recordId))
                .stream().findFirst().orElse(null);
        String shareStatus = effectiveShareStatus(share);
        if (share == null || !"ACTIVE".equals(shareStatus) || !shareAllows(share, actionCode)) {
            if (share != null) recordShareUsage(context, share, actionCode,
                    share == null ? "NOT_FOUND" : (!"ACTIVE".equals(shareStatus) ? shareStatus : "ACTION_DENIED"));
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在或共享授权已失效", HttpStatus.NOT_FOUND);
        }
        if (!withinSharedScope(context, moduleCode, actionCode, sharedRecord)) {
            recordShareUsage(context, share, actionCode, "DATA_SCOPE_DENIED");
            deny(context, moduleCode, actionCode, traceId,
                    Map.of("recordId", recordId, "shareId", share.getId(), "reason", "DATA_SCOPE_DENIED"));
        }
        AuthenticatedContext sourceContext = tenantContext(context, sharedRecord.getTenantId());
        return new RecordAccess(sharedRecord, published(sourceContext, moduleCode), sourceContext, share);
    }

    private boolean withinSharedScope(
            AuthenticatedContext context, String moduleCode, String actionCode, BusinessRecord record) {
        LambdaQueryWrapper<BusinessRecord> query = Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, context.systemId())
                .eq(BusinessRecord::getModuleId, record.getModuleId())
                .eq(BusinessRecord::getId, record.getId());
        applyDataScope(query, context, moduleCode, actionCode);
        return !recordService.selectList(query).isEmpty();
    }

    private List<BizTenantShare> activeTargetShares(AuthenticatedContext context) {
        return tenantShareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                        .eq(BizTenantShare::getSystemId, context.systemId())
                        .eq(BizTenantShare::getTargetTenantId, context.tenantId())
                        .eq(BizTenantShare::getStatus, "ACTIVE"))
                .stream().filter(share -> "ACTIVE".equals(effectiveShareStatus(share))).toList();
    }

    private AuthenticatedContext tenantContext(AuthenticatedContext context, Long tenantId) {
        return new AuthenticatedContext(context.sessionId(), context.accountId(), context.platformId(),
                context.systemId(), tenantId, context.memberId(), context.tenantMemberId(), context.username(),
                context.displayName(), context.mfaLevel(), context.roleIds(), context.permissions(), context.dataScopes());
    }

    private List<String> normalizeShareActions(PublishedModule published, List<String> requested) {
        Set<String> publishedActions = new LinkedHashSet<>();
        published.configuration().configuration().path("actions")
                .forEach(action -> publishedActions.add(action.path("code").asText().toUpperCase(Locale.ROOT)));
        LinkedHashSet<String> result = new LinkedHashSet<>(List.of("LIST", "DETAIL"));
        if (requested != null) requested.stream().filter(java.util.Objects::nonNull)
                .map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT)).forEach(result::add);
        Set<String> prohibited = Set.of("CREATE", "RESTORE", "SHARE");
        List<String> invalid = result.stream()
                .filter(action -> prohibited.contains(action) || !publishedActions.contains(action)).toList();
        if (!invalid.isEmpty()) {
            throw new DomainException("SHARE_ACTION_INVALID", "共享动作无效或不允许：" + String.join("、", invalid),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return List.copyOf(result);
    }

    private boolean shareAllows(BizTenantShare share, String actionCode) {
        if (share == null) return false;
        JsonNode actions = parseConfig(share.getPermissionJson()).path("allowedActions");
        if (!actions.isArray()) return false;
        for (JsonNode action : actions) {
            if (actionCode.equalsIgnoreCase(action.asText())) return true;
        }
        return false;
    }

    private List<String> shareActions(BizTenantShare share) {
        JsonNode actions = parseConfig(share.getPermissionJson()).path("allowedActions");
        List<String> result = new ArrayList<>();
        if (actions.isArray()) actions.forEach(action -> result.add(action.asText()));
        return List.copyOf(result);
    }

    private String effectiveShareStatus(BizTenantShare share) {
        if (share == null) return "MISSING";
        if (!"ACTIVE".equals(share.getStatus())) return share.getStatus();
        if (share.getExpiresAt() != null && !share.getExpiresAt().isAfter(LocalDateTime.now())) return "EXPIRED";
        return "ACTIVE";
    }

    private RuntimeTenantShareView shareView(String moduleCode, BizTenantShare share) {
        return new RuntimeTenantShareView(share.getId(), share.getSourceTenantId(), tenantName(share.getSourceTenantId()),
                share.getTargetTenantId(), tenantName(share.getTargetTenantId()), share.getRecordId(), moduleCode,
                shareActions(share), effectiveShareStatus(share), share.getGrantedAt(), share.getExpiresAt(),
                share.getRevokedAt(), share.getGrantedByMemberId(), share.getVersion());
    }

    private String tenantName(Long tenantId) {
        SystemTenant tenant = tenantId == null ? null : tenantService.selectById(tenantId);
        return tenant == null ? "未知租户" : tenant.getName();
    }

    private void recordShareUsage(
            AuthenticatedContext context, BizTenantShare share, String actionCode, String resultCode) {
        tenantShareUsageRecorder.record(share.getId(), context.tenantMemberId(), actionCode, resultCode);
    }

    private boolean withinScope(
            AuthenticatedContext context,
            String moduleCode,
            String actionCode,
            Long recordId,
            Long moduleId) {
        LambdaQueryWrapper<BusinessRecord> query = baseBoundary(context, moduleId).eq(BusinessRecord::getId, recordId);
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
            AuthenticatedContext validationContext,
            String moduleCode,
            String actionCode,
            List<PublishedField> fields,
            Map<String, JsonNode> submitted,
            String traceId,
            Long recordId) {
        Map<String, PublishedField> byCode = new LinkedHashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        List<String> errors = new ArrayList<>();
        submitted.keySet().stream().filter(code -> !byCode.containsKey(code)).sorted()
                .forEach(code -> errors.add(code + ": 字段未发布或不存在"));
        Map<String, FieldAccessDecision> access = fieldPolicyResolver.resolve(
                context, moduleCode, actionCode, "PAGE", fields.stream().map(PublishedField::code).toList());
        Map<String, JsonNode> normalized = new LinkedHashMap<>();
        for (PublishedField field : fields) {
            JsonNode value = submitted.get(field.code());
            if (empty(value)) {
                if (field.required()) {
                    errors.add(field.code() + ": 必填字段不能为空");
                }
                continue;
            }
            if (!access.get(field.code()).writable()) {
                errors.add(field.code() + ": 当前页面渠道没有字段写入权限");
                continue;
            }
            try {
                JsonNode normalizedValue = normalize(validationContext, field, value);
                validateReferenceValue(validationContext, field, normalizedValue);
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
        if ("REFERENCE".equals(field.type())) {
            if (field.referenceModuleId() == null) {
                throw new IllegalArgumentException("引用字段未配置目标模块");
            }
            boolean valid = !recordService.selectList(boundary(context, field.referenceModuleId())
                    .eq(BusinessRecord::getId, value.longValue())).isEmpty();
            if (!valid) {
                throw new IllegalArgumentException("引用记录不存在或不属于当前租户");
            }
        }
    }

    private JsonNode normalize(AuthenticatedContext context, PublishedField field, JsonNode value) {
        return switch (field.type()) {
            case "TEXT", "MULTILINE_TEXT" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是文本");
                }
                yield TextNode.valueOf(value.textValue());
            }
            case "PHONE" -> {
                if (!value.isTextual()) throw new IllegalArgumentException("必须是手机号文本");
                String phone = value.textValue().strip().replace(" ", "").replace("-", "");
                if (!phone.matches("^\\+?[0-9]{6,20}$")) throw new IllegalArgumentException("手机号格式不正确");
                yield TextNode.valueOf(phone);
            }
            case "SINGLE_SELECT", "STATUS" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是选项值");
                }
                validateOption(field, value.textValue());
                yield TextNode.valueOf(value.textValue());
            }
            case "NUMBER", "MONEY" -> {
                if (value.isNumber()) {
                    yield DecimalNode.valueOf(value.decimalValue());
                }
                if (value.isTextual()) {
                    try {
                        yield DecimalNode.valueOf(new BigDecimal(value.textValue().strip()));
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("必须是数字");
                    }
                }
                throw new IllegalArgumentException("必须是数字");
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
            case "MEMBER", "DEPARTMENT", "REFERENCE" -> {
                if (!value.canConvertToLong()) {
                    throw new IllegalArgumentException("必须是有效 ID");
                }
                yield LongNode.valueOf(value.longValue());
            }
            case "CASCADE" -> {
                if (field.dictionaryId() == null) throw new IllegalArgumentException("级联字段尚未绑定发布字典");
                List<Long> submittedPath = new ArrayList<>();
                if (value.isArray()) {
                    value.forEach(item -> {
                        if (item.canConvertToLong()) submittedPath.add(item.longValue());
                    });
                    if (submittedPath.size() != value.size()) throw new IllegalArgumentException("级联选择必须是有效选项 ID");
                } else if (value.canConvertToLong()) {
                    submittedPath.add(value.longValue());
                } else {
                    throw new IllegalArgumentException("级联选择必须是有效选项 ID");
                }
                List<Long> resolvedPath = dictionaryConfigurationService.validateRuntimeSelection(
                        context, field.dictionaryId(), submittedPath,
                        field.config().path("maxDepth").asInt(0),
                        field.config().path("allowIntermediate").asBoolean(false));
                if ("PATH".equalsIgnoreCase(field.config().path("saveMode").asText("FINAL"))) {
                    ArrayNode path = objectMapper.createArrayNode();
                    resolvedPath.forEach(path::add);
                    yield path;
                }
                yield LongNode.valueOf(resolvedPath.getLast());
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

    private void replaceValues(
            AuthenticatedContext context, Long recordId, List<PublishedField> fields, Map<String, JsonNode> values) {
        List<Long> currentFieldIds = fields.stream().map(PublishedField::id).toList();
        if (!currentFieldIds.isEmpty()) {
            valueMapper.delete(Wrappers.<BusinessRecordValue>lambdaQuery()
                    .eq(BusinessRecordValue::getRecordId, recordId)
                    .in(BusinessRecordValue::getFieldId, currentFieldIds));
            relationMapper.delete(Wrappers.<BizRecordRelation>lambdaQuery()
                    .eq(BizRecordRelation::getSystemId, context.systemId())
                    .eq(BizRecordRelation::getTenantId, context.tenantId())
                    .eq(BizRecordRelation::getSourceRecordId, recordId)
                    .in(BizRecordRelation::getFieldId, currentFieldIds));
        }
        Map<String, PublishedField> byCode = new HashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        values.forEach((code, value) -> {
            PublishedField field = byCode.get(code);
            valueMapper.insert(toStoredValue(recordId, field, value));
            if ("REFERENCE".equals(field.type())) {
                BizRecordRelation relation = new BizRecordRelation();
                relation.setSystemId(context.systemId());
                relation.setTenantId(context.tenantId());
                relation.setSourceRecordId(recordId);
                relation.setFieldId(field.id());
                relation.setTargetRecordId(value.longValue());
                relation.setRelationOrder(0);
                relationMapper.insert(relation);
            }
        });
    }

    private BusinessRecordValue toStoredValue(Long recordId, PublishedField field, JsonNode value) {
        BusinessRecordValue stored = new BusinessRecordValue();
        stored.setRecordId(recordId);
        stored.setFieldId(field.id());
        stored.setFieldCode(field.code());
        stored.setValueType(field.type());
        switch (field.type()) {
            case "TEXT", "MULTILINE_TEXT", "PHONE", "SINGLE_SELECT", "STATUS" -> stored.setValueText(value.textValue());
            case "NUMBER", "MONEY" -> stored.setValueNumber(value.decimalValue());
            case "DATE" -> stored.setValueDate(LocalDate.parse(value.textValue()));
            case "DATETIME" -> stored.setValueDatetime(LocalDateTime.parse(value.textValue()));
            case "BOOLEAN" -> stored.setValueBoolean(value.booleanValue());
            case "MEMBER", "DEPARTMENT", "REFERENCE" -> stored.setValueReferenceId(value.longValue());
            case "CASCADE" -> {
                if (value.isArray()) stored.setValueJson(value.toString());
                else stored.setValueReferenceId(value.longValue());
            }
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

    private RuntimeRecordView view(AuthenticatedContext context, String moduleCode, String actionCode,
                                   List<String> channels, BusinessRecord record, PublishedModule published) {
        return view(context, moduleCode, actionCode, channels,
                new RecordAccess(record, published, tenantContext(context, record.getTenantId()), null));
    }

    private RuntimeRecordView view(AuthenticatedContext context, String moduleCode, String actionCode,
                                   List<String> channels, RecordAccess access) {
        BusinessRecord record = access.record();
        PublishedModule published = access.published();
        List<Long> participantIds = participantMapper.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, record.getId())
                        .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                .stream().map(BusinessRecordParticipant::getSystemMemberId).toList();
        return new RuntimeRecordView(
                record.getId(), record.getRecordNumber(), record.getTitle(), record.getStatus(), record.getOwnerMemberId(),
                record.getDepartmentId(), record.getCreatedByMemberId(), record.getUpdatedByMemberId(),
                record.getCreatedConfigVersionId(), record.getUpdatedConfigVersionId(), participantIds,
                record.getArchived(), record.getArchivedAt(), record.getDeleted(), record.getDeletedAt(),
                record.getVersion(), record.getCreatedAt(), record.getUpdatedAt(),
                record.getTenantId(), tenantName(record.getTenantId()), context.tenantId().equals(record.getTenantId()),
                access.share() != null, access.share() == null ? null : access.share().getId(),
                access.share() == null ? null : effectiveShareStatus(access.share()),
                access.share() == null ? List.of() : shareActions(access.share()),
                access.share() == null ? null : access.share().getExpiresAt(),
                readProtectedValues(context, moduleCode, actionCode, channels, record.getId(), published.fields()));
    }

    private Map<String, JsonNode> readRawValues(Long recordId, List<PublishedField> fields) {
        Set<String> visibleCodes = fields.stream().map(PublishedField::code).collect(java.util.stream.Collectors.toSet());
        Map<String, JsonNode> result = new LinkedHashMap<>();
        valueMapper.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                        .eq(BusinessRecordValue::getRecordId, recordId).orderByAsc(BusinessRecordValue::getId))
                .stream().filter(value -> visibleCodes.contains(value.getFieldCode()))
                .forEach(value -> result.put(value.getFieldCode(), fromStoredValue(value)));
        return result;
    }

    private Map<String, JsonNode> readProtectedValues(AuthenticatedContext context, String moduleCode,
                                                       String actionCode, List<String> channels, Long recordId,
                                                       List<PublishedField> fields) {
        Map<String, JsonNode> raw = readRawValues(recordId, fields);
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, actionCode, channels, fields.stream().map(PublishedField::code).toList());
        Map<String, JsonNode> result = new LinkedHashMap<>();
        raw.forEach((code, value) -> {
            JsonNode protectedValue = fieldPolicyResolver.protect(value, decisions.get(code));
            if (protectedValue != null) {
                result.put(code, protectedValue);
            }
        });
        return result;
    }

    private Map<String, Object> permissionSnapshot(AuthenticatedContext context, String moduleCode,
                                                    String actionCode, String channel) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleIds", context.roleIds());
        snapshot.put("resource", "MODULE:" + moduleCode);
        snapshot.put("action", actionCode);
        snapshot.put("channel", channel);
        snapshot.put("dataScope", context.dataScopes().entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(":" + moduleCode + ":" + actionCode)
                        || entry.getKey().startsWith("*:*:"))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new)));
        return snapshot;
    }

    private void recordFieldChanges(AuthenticatedContext context, String moduleCode, Long auditEventId,
                                    Map<String, JsonNode> before, Map<String, JsonNode> after,
                                    List<PublishedField> fields) {
        Map<String, PublishedField> byCode = fields.stream()
                .collect(java.util.stream.Collectors.toMap(PublishedField::code, field -> field));
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(before.keySet());
        codes.addAll(after.keySet());
        for (String code : codes) {
            JsonNode beforeValue = before.get(code);
            JsonNode afterValue = after.get(code);
            if (java.util.Objects.equals(beforeValue, afterValue)) {
                continue;
            }
            PublishedField field = byCode.get(code);
            AuditFieldChange change = new AuditFieldChange();
            change.setAuditEventId(auditEventId);
            change.setFieldCode(code);
            change.setValueType(field == null ? "UNKNOWN" : field.type());
            change.setBeforeValueJson(toJsonValue(beforeValue));
            change.setAfterValueJson(toJsonValue(afterValue));
            boolean sensitive = (field != null && field.config().path("sensitive").asBoolean(false))
                    || fieldPolicyResolver.isSensitive(context, moduleCode, code);
            change.setSensitivity(sensitive ? "SENSITIVE" : "NORMAL");
            fieldChangeService.insert(change);
        }
    }

    private String toJsonValue(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value == null ? objectMapper.nullNode() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize audit field value", exception);
        }
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
        if (value.getValueJson() != null) {
            try {
                return objectMapper.readTree(value.getValueJson());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored JSON field value is invalid", exception);
            }
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

    private record PublishedField(
            Long id, String code, String name, String type, boolean required, boolean searchable,
            Long dictionaryId, Long referenceModuleId, JsonNode config) {
    }

    private record QueryAccessPolicy(
            Set<String> queryableFields, Set<String> searchableFields, Set<String> builtInFields) {
    }

    private record Ownership(Long ownerMemberId, Long departmentId, List<Long> participantMemberIds) {
    }

    private record RecordAccess(
            BusinessRecord record,
            PublishedModule published,
            AuthenticatedContext sourceContext,
            BizTenantShare share) {
    }

    private record ConversionPlan(
            RecordAccess access,
            BusinessRecord source,
            PublishedModule target,
            Long actionId,
            Map<String, JsonNode> targetValues,
            RuntimeRecordConversionPreview preview) {
    }

    @FunctionalInterface
    private interface ScopeClause {
        void apply(LambdaQueryWrapper<BusinessRecord> wrapper);
    }
}
