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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordParticipant;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordValue;
import com.unique.unexamine.runtimedata.base.entity.BizRecordRelation;
import com.unique.unexamine.runtimedata.base.entity.BizRecordChildRow;
import com.unique.unexamine.runtimedata.base.entity.BizRecordChildValue;
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
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordParticipantBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordValueBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordRelationBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordChildRowBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordChildValueBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordStateHistoryBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordOwnerHistoryBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordConversionBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordConversionResultBaseService;
import com.unique.unexamine.runtimedata.base.service.BizTenantShareBaseService;
import com.unique.unexamine.runtimedata.manage.repository.RuntimeRecordWriteRepository;
import com.unique.unexamine.file.base.entity.FileObject;
import com.unique.unexamine.file.base.entity.FileReference;
import com.unique.unexamine.file.base.service.FileObjectBaseService;
import com.unique.unexamine.file.base.service.FileReferenceBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.entity.AuditFieldChange;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.audit.base.service.AuditFieldChangeBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.SharedSourceMetadataService;
import com.unique.unexamine.moduleconfig.manage.ModuleIdentifierIntegrity;
import com.unique.unexamine.moduleconfig.manage.TextFieldProtocol;
import com.unique.unexamine.moduleconfig.manage.StructuredContentFieldProtocol;
import com.unique.unexamine.moduleconfig.manage.DictionaryConfigurationService;
import com.unique.unexamine.moduleconfig.manage.DictionaryModels;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.authorization.manage.SystemPeopleDirectoryService;
import com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels;
import com.unique.unexamine.authorization.manage.FieldAccessDecision;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.foundation.manage.control.BusinessAction;
import com.unique.unexamine.foundation.manage.control.BusinessActionApprovalCoordinator;
import com.unique.unexamine.foundation.manage.control.FoundationControlService;
import com.unique.unexamine.foundation.manage.control.FoundationPolicyService;
import com.unique.unexamine.foundation.manage.event.LocalBusinessEvent;
import com.unique.unexamine.foundation.manage.event.LocalBusinessEventPublisher;
import com.unique.unexamine.flow.manage.FlowAutomaticActionPolicy;
import com.unique.unexamine.flow.manage.FlowBindingService;
import com.unique.unexamine.flow.manage.FlowTriggerEventService;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 运行态记录与公共动作的业务编排；单表访问使用生成的 Base Service。 */
@Service
public class RuntimeDataService {
    private final FoundationControlService actionControl;
    private final FoundationPolicyService foundationPolicyService;
    private final ObjectProvider<BusinessActionApprovalCoordinator> approvalCoordinator;
    private final FlowBindingService actionBindings;
    private final Validator actionValidator;
    private final com.unique.unexamine.shared.manage.security.RuntimeSecretCipher secretCipher;
    private final ModulePublicationService publicationService;
    private final SharedSourceMetadataService sharedSourceMetadata;
    private final TenantShareScopeService tenantShareScope;
    private final ModuleIdentifierIntegrity moduleIdentifierIntegrity;
    private final DictionaryConfigurationService dictionaryConfigurationService;
    private final BusinessRecordBaseService recordService;
    private final BusinessRecordValueBaseService valueService;
    private final BusinessRecordParticipantBaseService participantService;
    private final BizRecordRelationBaseService relationService;
    private final BizRecordChildRowBaseService childRowService;
    private final BizRecordChildValueBaseService childValueService;
    private final BizRecordStateHistoryBaseService stateHistoryService;
    private final BizRecordOwnerHistoryBaseService ownerHistoryService;
    private final BizRecordConversionBaseService conversionService;
    private final BizRecordConversionResultBaseService conversionResultService;
    private final BizTenantShareBaseService tenantShareService;
    private final TenantShareUsageRecorder tenantShareUsageRecorder;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService moduleFieldService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemDefinitionBaseService systemService;
    private final com.unique.unexamine.authorization.manage.OrganizationRelationRepository organizationRelationRepository;
    private final SystemDepartmentBaseService departmentService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantBaseService tenantService;
    private final PermissionChecker permissionChecker;
    private final ChannelFieldPolicyResolver fieldPolicyResolver;
    private final SystemPeopleDirectoryService peopleDirectoryService;
    private final AuditRecorder auditRecorder;
    private final AuditEventBaseService auditEventService;
    private final AuditFieldChangeBaseService fieldChangeService;
    private final RuntimeRuleIndexService ruleIndexService;
    private final FlowTriggerEventService flowEvents;
    private final LocalBusinessEventPublisher localEvents;
    private final ObjectMapper objectMapper;
    private final RuntimeRecordWriteRepository recordWrites;
    private final FileObjectBaseService fileObjectService;
    private final FileReferenceBaseService fileReferenceService;

    public RuntimeDataService(
            ModulePublicationService publicationService,
            ModuleIdentifierIntegrity moduleIdentifierIntegrity,
            DictionaryConfigurationService dictionaryConfigurationService,
            BusinessRecordBaseService recordService,
            BusinessRecordValueBaseService valueService,
            BusinessRecordParticipantBaseService participantService,
            BizRecordRelationBaseService relationService,
            BizRecordChildRowBaseService childRowService,
            BizRecordChildValueBaseService childValueService,
            BizRecordStateHistoryBaseService stateHistoryService,
            BizRecordOwnerHistoryBaseService ownerHistoryService,
            BizRecordConversionBaseService conversionService,
            BizRecordConversionResultBaseService conversionResultService,
            BizTenantShareBaseService tenantShareService,
            TenantShareUsageRecorder tenantShareUsageRecorder,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService moduleFieldService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemDefinitionBaseService systemService,
            com.unique.unexamine.authorization.manage.OrganizationRelationRepository organizationRelationRepository,
            SystemDepartmentBaseService departmentService,
            SystemMemberBaseService memberService,
            SystemTenantBaseService tenantService,
            PermissionChecker permissionChecker,
            ChannelFieldPolicyResolver fieldPolicyResolver,
            SystemPeopleDirectoryService peopleDirectoryService,
            AuditRecorder auditRecorder,
            AuditEventBaseService auditEventService,
            AuditFieldChangeBaseService fieldChangeService,
            RuntimeRuleIndexService ruleIndexService,
            ObjectMapper objectMapper,
            FoundationControlService actionControl, FoundationPolicyService foundationPolicyService,
            FlowBindingService actionBindings, Validator actionValidator,
            RuntimeRecordWriteRepository recordWrites, SharedSourceMetadataService sharedSourceMetadata,
            com.unique.unexamine.shared.manage.security.RuntimeSecretCipher secretCipher, TenantShareScopeService tenantShareScope,
            FlowTriggerEventService flowEvents,
            LocalBusinessEventPublisher localEvents,
            FileObjectBaseService fileObjectService,
            FileReferenceBaseService fileReferenceService,
            ObjectProvider<BusinessActionApprovalCoordinator> approvalCoordinator) {
        this.recordWrites = recordWrites;
        this.sharedSourceMetadata = sharedSourceMetadata;
        this.tenantShareScope = tenantShareScope;
        this.actionControl = actionControl;
        this.foundationPolicyService = foundationPolicyService;
        this.approvalCoordinator = approvalCoordinator;
        this.actionBindings = actionBindings;
        this.actionValidator = actionValidator;
        this.flowEvents = flowEvents;
        this.localEvents = localEvents;
        this.secretCipher = secretCipher;
        this.publicationService = publicationService;
        this.moduleIdentifierIntegrity = moduleIdentifierIntegrity;
        this.dictionaryConfigurationService = dictionaryConfigurationService;
        this.recordService = recordService;
        this.valueService = valueService;
        this.participantService = participantService;
        this.relationService = relationService;
        this.childRowService = childRowService;
        this.childValueService = childValueService;
        this.stateHistoryService = stateHistoryService;
        this.ownerHistoryService = ownerHistoryService;
        this.conversionService = conversionService;
        this.conversionResultService = conversionResultService;
        this.tenantShareService = tenantShareService;
        this.tenantShareUsageRecorder = tenantShareUsageRecorder;
        this.moduleService = moduleService;
        this.moduleFieldService = moduleFieldService;
        this.tenantMemberService = tenantMemberService;
        this.systemService = systemService;
        this.organizationRelationRepository = organizationRelationRepository;
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.tenantService = tenantService;
        this.permissionChecker = permissionChecker;
        this.fieldPolicyResolver = fieldPolicyResolver;
        this.peopleDirectoryService = peopleDirectoryService;
        this.auditRecorder = auditRecorder;
        this.auditEventService = auditEventService;
        this.fieldChangeService = fieldChangeService;
        this.ruleIndexService = ruleIndexService;
        this.objectMapper = objectMapper;
        this.fileObjectService = fileObjectService;
        this.fileReferenceService = fileReferenceService;
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
        return listForAction(context, moduleCode, lifecycleState, tenantScope, search, filtersJson,
                sortField, sortDirection, pageNumber, pageSize, traceId, "LIST");
    }

    /** 导出沿用列表筛选和字段展示，但记录必须同时落在列表与导出各自的数据范围内。 */
    @Transactional(readOnly = true)
    public RuntimeRecordList listForExport(
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
        return listForAction(context, moduleCode, lifecycleState, tenantScope, search, filtersJson,
                sortField, sortDirection, pageNumber, pageSize, traceId, "EXPORT");
    }

    private RuntimeRecordList listForAction(
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
            String traceId,
            String dataAction) {
        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
            throw new DomainException("PAGINATION_INVALID", "页码必须大于 0，每页数量不能超过 200", HttpStatus.BAD_REQUEST);
        }
        ListQueryAccess queryAccess = listQueryAccess(context, moduleCode, lifecycleState, tenantScope,
                traceId, dataAction);
        List<RecordAccess> accessible = new ArrayList<>(queryAccess.records());
        List<RuntimeListFilter> filters = parseListFilters(filtersJson);
        QueryAccessPolicy queryPolicy = queryAccessPolicy(context, moduleCode, queryAccess);
        validateListQuery(queryPolicy.fields(), filters, sortField, sortDirection);
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
        return new RuntimeRecordList(views, accessible.size(), pageNumber, pageSize, queryPolicy.fields());
    }

    /** 查询目录与查询执行共用真实记录边界，不能用任意来源租户参数发现字段。 */
    @Transactional(readOnly = true)
    public List<RuntimeListQueryField> queryFields(
            AuthenticatedContext context, String moduleCode, String lifecycleState, String tenantScope, String traceId) {
        return queryAccessPolicy(context, moduleCode,
                listQueryAccess(context, moduleCode, lifecycleState, tenantScope, traceId, "LIST")).fields();
    }

    private ListQueryAccess listQueryAccess(
            AuthenticatedContext context, String moduleCode, String lifecycleState, String tenantScope,
            String traceId, String dataAction) {
        requireAction(context, moduleCode, "LIST", traceId);
        if (!"LIST".equals(dataAction)) requireAction(context, moduleCode, dataAction, traceId);
        String normalizedTenantScope = tenantScope == null ? "ALL" : tenantScope.toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "OWN", "SHARED").contains(normalizedTenantScope)) {
            throw new DomainException("TENANT_SCOPE_INVALID", "租户范围只支持 ALL、OWN 或 SHARED", HttpStatus.BAD_REQUEST);
        }
        SharedSourceMetadataService.RuntimeAccess moduleAccess = sharedSourceMetadata.requireRuntimeAccess(context, moduleCode);
        if (!multiTenantEnabled(context.systemId()) && !moduleAccess.currentConfiguration()) {
            throw new DomainException("SHARED_MODULE_NOT_AVAILABLE", "模块不存在或访问关系已失效", HttpStatus.NOT_FOUND);
        }
        // 来源数据只由当前有效共享和原操作者范围取得，不借用来源成员身份。
        PublishedModule currentPublished = moduleAccess.currentConfiguration() ? published(context, moduleCode) : null;
        List<RecordAccess> accessible = new ArrayList<>();
        if (currentPublished != null && !"SHARED".equals(normalizedTenantScope)) {
            LambdaQueryWrapper<BusinessRecord> ownQuery = baseBoundary(context, currentPublished.configuration().moduleId());
            applyLifecycleState(ownQuery, lifecycleState);
            applyDataScope(ownQuery, context, moduleCode, "LIST");
            if (!"LIST".equals(dataAction)) applyDataScope(ownQuery, context, moduleCode, dataAction);
            recordService.selectList(ownQuery).forEach(record -> accessible.add(
                    new RecordAccess(record, currentPublished, context, null)));
        }
        if (!"OWN".equals(normalizedTenantScope)) {
            Set<Long> included = new HashSet<>();
            for (BizTenantShare share : activeTargetShares(context)) {
                if (!shareAllows(share, "LIST") || (!"LIST".equals(dataAction) && !shareAllows(share, dataAction))
                        || !moduleAccess.module().getId().equals(share.getModuleId())
                        || !sharedSourceMetadata.activeTenantPair(context, share.getSourceTenantId())) continue;
                var candidates = Wrappers.<BusinessRecord>lambdaQuery()
                        .eq(BusinessRecord::getSystemId, context.systemId())
                        .eq(BusinessRecord::getTenantId, share.getSourceTenantId())
                        .eq(BusinessRecord::getModuleId, share.getModuleId());
                if ("RECORD".equals(share.getScopeType())) candidates.eq(BusinessRecord::getId, share.getRecordId());
                applyLifecycleState(candidates, lifecycleState);
                for (BusinessRecord record : recordService.selectList(candidates)) {
                    // 共享授权扩充该动作的数据范围；目标成员仍须有模块动作权限，但无需再命中本租户角色范围。
                    if (included.contains(record.getId()) || !tenantShareScope.matches(context, share, record, moduleCode)) continue;
                    AuthenticatedContext sourceContext = tenantContext(context, record.getTenantId());
                    PublishedModule sourcePublished = published(sourceContext, moduleCode);
                    if (!record.getModuleId().equals(sourcePublished.configuration().moduleId())) {
                        throw new DomainException("SHARE_CONFIGURATION_CONFLICT", "共享记录与来源发布模块不一致", HttpStatus.CONFLICT);
                    }
                    accessible.add(new RecordAccess(record, sourcePublished, sourceContext, share));
                    included.add(record.getId());
                }
            }
        }
        return new ListQueryAccess(currentPublished, accessible);
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
            AuthenticatedContext context, String moduleCode, ListQueryAccess access) {
        Map<String, PublishedField> candidates = new LinkedHashMap<>();
        Set<String> incompatible = new LinkedHashSet<>();
        List<PublishedModule> publications = new ArrayList<>();
        if (access.currentPublished() != null) publications.add(access.currentPublished());
        access.records().forEach(record -> publications.add(record.published()));
        for (PublishedModule publication : publications) {
            for (PublishedField field : publication.fields()) {
                // 子表列只属于其明细行，不作为主记录的筛选或排序字段。
                if (field.parentFieldId() != null) continue;
                PublishedField previous = candidates.putIfAbsent(field.code(), field);
                if (previous != null && !previous.type().equals(field.type())) incompatible.add(field.code());
            }
        }
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, "LIST", List.of("PAGE"), List.copyOf(candidates.keySet()));
        Set<String> queryable = new LinkedHashSet<>();
        candidates.forEach((code, field) -> {
            FieldAccessDecision decision = decisions.get(code);
            if (!incompatible.contains(code) && decision != null && decision.readable()
                    && ("SECRET".equals(field.type()) || decision.maskStrategy() == null || decision.maskStrategy().isBlank())) queryable.add(code);
        });
        Map<String, Set<String>> searchableByPublication = new HashMap<>();
        for (PublishedModule publication : publications) {
            searchableByPublication.put(publicationKey(publication), publication.fields().stream()
                    .filter(field -> queryable.contains(field.code()) && field.searchable()
                            && !Set.of("JSON", "ADDRESS", "LOCATION", "SECRET", "CASCADE").contains(field.type()))
                    .map(PublishedField::code).collect(java.util.stream.Collectors.toSet()));
        }
        List<RuntimeListQueryField> descriptors = new ArrayList<>();
        Map<String, String> builtinNames = new LinkedHashMap<>();
        builtinNames.put("title", "标题"); builtinNames.put("recordNumber", "业务编号");
        builtinNames.put("status", "状态"); builtinNames.put("ownerMemberId", "负责人");
        builtinNames.put("departmentId", "负责部门"); builtinNames.put("createdAt", "创建时间");
        builtinNames.put("updatedAt", "更新时间"); builtinNames.put("dataTenantName", "数据归属");
        builtinNames.forEach((code, name) -> {
            String type = Set.of("createdAt", "updatedAt").contains(code) ? "DATETIME"
                    : "ownerMemberId".equals(code) ? "MEMBER" : "departmentId".equals(code) ? "DEPARTMENT"
                    : "status".equals(code) ? "STATUS" : "TEXT";
            descriptors.add(new RuntimeListQueryField(code, name, type, false, true,
                    RuntimeListQueryField.operatorsFor(type), List.of()));
        });
        Map<String, Map<String, String>> choices = new LinkedHashMap<>();
        Set<String> choiceTypes = Set.of("SINGLE_SELECT", "STATUS", "REFERENCE", "CASCADE", "MEMBER", "DEPARTMENT");
        for (RecordAccess record : access.records()) {
            Map<String, JsonNode> choiceValues = new LinkedHashMap<>();
            List<PublishedField> choiceFields = record.published().fields().stream()
                    .filter(field -> queryable.contains(field.code()) && choiceTypes.contains(queryType(field.type()))).toList();
            Map<String, JsonNode> rawChoices = readRawValues(record.record().getId(), choiceFields);
            choiceFields.forEach(field -> {
                        JsonNode value = rawChoices.get(field.code());
                        if (value != null && !value.isNull()) choiceValues.put(field.code(), value);
                    });
            // 名称解析复用受保护的记录投影；不返回来源字典或通讯录的其他条目。
            Map<String, RuntimeFieldPresentation> names = fieldPresentation(context, record, choiceValues, decisions);
            choiceValues.forEach((code, value) -> {
                RuntimeFieldPresentation name = names.get(code);
                String label = name == null ? null : name.displayValue();
                if (label == null && Set.of("MEMBER", "DEPARTMENT").contains(queryType(candidates.get(code).type()))) {
                    SystemPeopleDirectoryModels.Labels labels = organizationLabels(record, null, null, List.of(), Map.of(code, value));
                    label = "MEMBER".equals(queryType(candidates.get(code).type())) ? labels.members().get(value.longValue())
                            : labels.departments().get(value.longValue());
                }
                if (label != null) choices.computeIfAbsent(code, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(value.isArray() ? value.toString() : value.asText(), label);
            });
            SystemPeopleDirectoryModels.Labels labels = organizationLabels(record, record.record().getOwnerMemberId(),
                    record.record().getDepartmentId(), List.of(), Map.of());
            if (record.record().getOwnerMemberId() != null && labels.members().containsKey(record.record().getOwnerMemberId()))
                choices.computeIfAbsent("ownerMemberId", ignored -> new LinkedHashMap<>()).putIfAbsent(
                        record.record().getOwnerMemberId().toString(), labels.members().get(record.record().getOwnerMemberId()));
            if (record.record().getDepartmentId() != null && labels.departments().containsKey(record.record().getDepartmentId()))
                choices.computeIfAbsent("departmentId", ignored -> new LinkedHashMap<>()).putIfAbsent(
                        record.record().getDepartmentId().toString(), labels.departments().get(record.record().getDepartmentId()));
            choices.computeIfAbsent("status", ignored -> new LinkedHashMap<>()).putIfAbsent(
                    record.record().getStatus(), "ACTIVE".equals(record.record().getStatus()) ? "有效" : record.record().getStatus());
        }
        for (String code : queryable) {
            PublishedField field = candidates.get(code);
            boolean searchable = searchableByPublication.values().stream().anyMatch(codes -> codes.contains(code))
                    && !Set.of("ADDRESS", "LOCATION").contains(field.type());
            descriptors.add(new RuntimeListQueryField(code, field.name(), field.type(), searchable,
                    !Set.of("RICH_TEXT", "JSON", "ADDRESS", "LOCATION", "CASCADE", "SECRET", "MULTI_SELECT", "TAG").contains(field.type()),
                    RuntimeListQueryField.operatorsFor(queryType(field.type())), List.of()));
        }
        List<RuntimeListQueryField> fields = descriptors.stream().map(field -> new RuntimeListQueryField(
                field.code(), field.name(), field.fieldType(), field.searchable(), field.sortable(), field.operators(),
                choices.getOrDefault(field.code(), Map.of()).entrySet().stream()
                        .map(choice -> new RuntimeListQueryField.Option(choice.getKey(), choice.getValue())).toList())).toList();
        return new QueryAccessPolicy(queryable, searchableByPublication, builtinNames.keySet(), fields);
    }

    private String queryType(String type) {
        return switch (type) {
            case "SYSTEM_CREATED_AT", "SYSTEM_UPDATED_AT" -> "DATETIME";
            case "SYSTEM_CREATED_BY", "SYSTEM_UPDATED_BY" -> "MEMBER";
            default -> type;
        };
    }

    private String publicationKey(PublishedModule publication) {
        return publication.configuration().versionId() + ":" + publication.configuration().configuration().path("tenantExtensionVersionId").asText();
    }

    /** 视图保存和实时查询使用同一字段、运算符及值类型校验，不允许保存无法执行的条件。 */
    public void validateListQuery(List<RuntimeListQueryField> fields, List<RuntimeListFilter> filters,
                                  String sortField, String sortDirection) {
        Map<String, RuntimeListQueryField> byCode = new HashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        for (RuntimeListFilter filter : filters) {
            RuntimeListQueryField field = filter == null ? null : byCode.get(filter.fieldCode());
            if (field == null) throw new DomainException("LIST_FILTER_FIELD_FORBIDDEN",
                    "筛选字段不存在、不可读或已脱敏", HttpStatus.FORBIDDEN);
            String operator = filter.operator() == null ? "" : filter.operator().strip().toUpperCase(Locale.ROOT);
            if (!field.operators().contains(operator)) throw new DomainException("LIST_FILTER_OPERATOR_INVALID",
                    "该字段不支持所选筛选运算符", HttpStatus.BAD_REQUEST);
            if (!Set.of("EMPTY", "NOT_EMPTY").contains(operator)) queryLiteral(field.fieldType(), filter.value());
        }
        String normalizedSort = sortField == null || sortField.isBlank() ? "updatedAt" : sortField.strip();
        RuntimeListQueryField sort = byCode.get(normalizedSort);
        if (sort == null || !sort.sortable()) throw new DomainException("LIST_SORT_FIELD_FORBIDDEN",
                "排序字段不存在、不可读、已脱敏或不支持排序", HttpStatus.FORBIDDEN);
        if (sortDirection != null && !Set.of("ASC", "DESC").contains(sortDirection.strip().toUpperCase(Locale.ROOT)))
            throw new DomainException("LIST_SORT_DIRECTION_INVALID", "排序方向只支持 ASC 或 DESC", HttpStatus.BAD_REQUEST);
    }

    private JsonNode queryLiteral(String type, String value) {
        String text = value == null ? "" : value;
        try {
            return switch (type) {
                case "NUMBER", "MONEY", "PERCENT" -> DecimalNode.valueOf(new BigDecimal(text));
                case "RATING", "PROGRESS" -> DecimalNode.valueOf(validateScoreOrProgress(type, new BigDecimal(text)));
                case "MEMBER", "DEPARTMENT", "REFERENCE" -> LongNode.valueOf(Long.parseLong(text));
                case "BOOLEAN" -> {
                    if (!Set.of("true", "false").contains(text)) throw new IllegalArgumentException();
                    yield BooleanNode.valueOf(Boolean.parseBoolean(text));
                }
                case "DATE" -> TextNode.valueOf(LocalDate.parse(text).toString());
                case "DATE_RANGE", "TIME_RANGE" -> normalizeTemporalRange(type, TextNode.valueOf(text));
                case "TIME" -> TextNode.valueOf(normalizeClockTime(text));
                case "DATETIME" -> {
                    var parsed = java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(text);
                    LocalDateTime local = parsed.isSupported(java.time.temporal.ChronoField.OFFSET_SECONDS)
                            ? java.time.OffsetDateTime.from(parsed).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime()
                            : LocalDateTime.from(parsed);
                    yield TextNode.valueOf(local.toString());
                }
                case "CASCADE" -> {
                    JsonNode selection = text.startsWith("[") ? objectMapper.readTree(text) : LongNode.valueOf(Long.parseLong(text));
                    if (selection.isArray()) {
                        if (selection.isEmpty()) throw new IllegalArgumentException();
                        for (JsonNode item : selection) if (!item.isIntegralNumber() || item.longValue() <= 0) throw new IllegalArgumentException();
                    } else if (!selection.isIntegralNumber() || selection.longValue() <= 0) throw new IllegalArgumentException();
                    yield selection;
                }
                default -> TextNode.valueOf(text);
            };
        } catch (Exception invalid) {
            throw new DomainException("LIST_FILTER_VALUE_INVALID", "筛选值格式不正确，请按字段类型重新填写", HttpStatus.BAD_REQUEST);
        }
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
                matched = policy.searchableFields().getOrDefault(publicationKey(access.published()), Set.of()).stream()
                        .map(raw::get).filter(java.util.Objects::nonNull)
                        .anyMatch(value -> containsIgnoreCase(value.isArray() ? value.toString() : value.asText(), needle));
            }
            if (!matched) return false;
        }
        for (RuntimeListFilter filter : filters) {
            // 来源专属字段不存在时跳过整条记录；NE/EMPTY 也不能把不存在当成可见空值。
            if (!policy.builtInFields().contains(filter.fieldCode())
                    && access.published().fields().stream().noneMatch(field -> field.code().equals(filter.fieldCode()))) return false;
            JsonNode actual = queryValue(access, filter.fieldCode(), policy, values);
            String type = policy.fields().stream().filter(field -> field.code().equals(filter.fieldCode()))
                    .findFirst().orElseThrow().fieldType();
            if (!matchesFilter(actual, filter, type)) return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private boolean matchesFilter(JsonNode actual, RuntimeListFilter filter, String type) {
        String operator = filter.operator().strip().toUpperCase(Locale.ROOT);
        boolean empty = empty(actual);
        if ("EMPTY".equals(operator)) return empty;
        if ("NOT_EMPTY".equals(operator)) return !empty;
        if (empty) return "NE".equals(operator);
        JsonNode expected = queryLiteral(type, filter.value());
        if (Set.of("MULTI_SELECT", "TAG").contains(type) && actual.isArray()) {
            boolean selected = false;
            for (JsonNode item : actual) if (item.asText().equals(expected.asText())) selected = true;
            return switch (operator) {
                case "EQ", "CONTAINS" -> selected;
                case "NE" -> !selected;
                default -> false;
            };
        }
        int compared = compareQueryValues(actual, expected, type);
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
        String type = policy.fields().stream().filter(item -> item.code().equals(field)).findFirst().orElseThrow().fieldType();
        Comparator<RecordAccess> comparator = (left, right) -> compareQueryValues(
                queryValue(left, field, policy, values), queryValue(right, field, policy, values), type);
        if ("DESC".equals(direction)) comparator = comparator.reversed();
        // 不适用记录始终放在末尾，不借助不存在字段改变升降序中的位置。
        Comparator<RecordAccess> applicability = Comparator.comparing(item -> !policy.builtInFields().contains(field)
                && item.published().fields().stream().noneMatch(candidate -> candidate.code().equals(field)));
        return applicability.thenComparing(comparator).thenComparing(item -> item.record().getId(), Comparator.reverseOrder());
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
            case "createdAt" -> textNode(record.getCreateTime() == null ? null : record.getCreateTime().toString());
            case "updatedAt" -> textNode(record.getUpdateTime() == null ? null : record.getUpdateTime().toString());
            case "dataTenantName" -> textNode(tenantName(record.getTenantId()));
            default -> policy.queryableFields().contains(field) ? queryValues(access, values).get(field) : null;
        };
    }

    private Map<String, JsonNode> queryValues(RecordAccess access, Map<Long, Map<String, JsonNode>> values) {
        return values.computeIfAbsent(access.record().getId(), ignored -> {
            Map<String, JsonNode> raw = readRawValues(access.record().getId(), access.published().fields().stream()
                    .filter(field -> !"SECRET".equals(field.type())).toList());
            access.published().fields().stream().filter(field -> field.type().startsWith("SYSTEM_")).forEach(field -> {
                JsonNode value = systemFieldValue(access.record(), field.type());
                if (value != null) raw.put(field.code(), value);
            });
            access.published().fields().stream().filter(field -> "RICH_TEXT".equals(field.type())).forEach(field -> {
                JsonNode value = raw.get(field.code());
                if (value != null && value.isTextual()) raw.put(field.code(), TextNode.valueOf(StructuredContentFieldProtocol.plainText(value.textValue())));
            });
            return raw;
        });
    }

    private TextNode textNode(String value) {
        return value == null ? null : TextNode.valueOf(value);
    }

    private int compareQueryValues(JsonNode left, JsonNode right, String type) {
        if (left == null || left.isNull()) return right == null || right.isNull() ? 0 : -1;
        if (right == null || right.isNull()) return 1;
        return switch (type) {
            case "NUMBER", "MONEY", "PERCENT", "RATING", "PROGRESS", "MEMBER", "DEPARTMENT", "REFERENCE" ->
                    new BigDecimal(left.asText()).compareTo(new BigDecimal(right.asText()));
            case "BOOLEAN" -> Boolean.compare(left.asBoolean(), right.asBoolean());
            case "DATE" -> LocalDate.parse(left.asText()).compareTo(LocalDate.parse(right.asText()));
            case "TIME" -> LocalTime.parse(left.asText()).compareTo(LocalTime.parse(right.asText()));
            case "DATE_RANGE", "TIME_RANGE" -> {
                int start = left.path(0).asText().compareTo(right.path(0).asText());
                yield start == 0 ? left.path(1).asText().compareTo(right.path(1).asText()) : start;
            }
            case "DATETIME" -> LocalDateTime.parse(left.asText()).compareTo(LocalDateTime.parse(right.asText()));
            case "CASCADE" -> left.toString().compareTo(right.toString());
            default -> left.asText().compareToIgnoreCase(right.asText());
        };
    }

    @Transactional(readOnly = true)
    public List<TenantShareTarget> shareTargets(
            AuthenticatedContext context, String moduleCode, String traceId) {
        requireMultiTenantShare(context.systemId());
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
        requireMultiTenantShare(context.systemId());
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
                        .orderByDesc(BizTenantShare::getGrantTime, BizTenantShare::getId))
                .stream().map(share -> shareView(moduleCode, share)).toList();
    }

    @Transactional
    public RuntimeTenantShareView grantShare(
            AuthenticatedContext context, String moduleCode, Long recordId,
            CreateTenantShareRequest request, String traceId) {
        requireMultiTenantShare(context.systemId());
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
        // API 的本地日期时间以北京时间表达，失效判断不能跟随容器默认时区。
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) {
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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        share.setSystemId(context.systemId());
        share.setSourceTenantId(context.tenantId());
        share.setTargetTenantId(target.getId());
        share.setModuleId(record.getModuleId());
        share.setScopeType("RECORD");
        share.setRecordId(recordId);
        share.setPermissionJson(writeJson(Map.of(
                "moduleCode", moduleCode,
                "allowedActions", allowedActions,
                "sourceTenantId", context.tenantId(),
                "targetTenantId", target.getId(),
                "grantorPermission", permissionSnapshot(context, moduleCode, "SHARE", "PAGE"))));
        share.setStatus("ACTIVE");
        share.setExpireTime(request.expiresAt());
        share.setGrantedByMemberId(context.memberId());
        share.setGrantTime(now);
        share.setRevokeTime(null);
        if (share.getId() == null) {
            share.setVersion(0);
            tenantShareService.insert(share);
        } else {
            if (tenantShareService.updateById(share) == 0) {
                throw new DomainException("SHARE_VERSION_CONFLICT", "共享授权已被其他操作修改", HttpStatus.CONFLICT);
            }
            // Base 更新会略过 null；重新授权须真正清除旧截止时间和撤销标记，仍在同一事务及已更新版本内。
            if (tenantShareService.update(Wrappers.<BizTenantShare>lambdaUpdate()
                    .eq(BizTenantShare::getId, share.getId())
                    .eq(BizTenantShare::getVersion, share.getVersion())
                    .set(BizTenantShare::getExpireTime, request.expiresAt())
                    .set(BizTenantShare::getRevokeTime, null)) != 1) {
                throw new DomainException("SHARE_VERSION_CONFLICT", "共享授权已被其他操作修改", HttpStatus.CONFLICT);
            }
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
        requireMultiTenantShare(context.systemId());
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
        share.setRevokeTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
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

    /** 审批发起必须命中当前发布动作、有效自有记录及该动作的数据权限；共享只读记录不代发审批。 */
    @Transactional(readOnly = true)
    public ApprovalRecord requireApprovalRecord(AuthenticatedContext context, String moduleCode,
                                                Long recordId, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "APPROVAL", recordId, true, traceId);
        if (access.share() != null) {
            throw new DomainException("MODULE_APPROVAL_SHARED_RECORD", "共享记录不能代发审批", HttpStatus.FORBIDDEN);
        }
        JsonNode action = findPublishedAction(access.published(), "APPROVAL");
        if (!"ACTIVE".equals(action.path("status").asText()) || !action.hasNonNull("id")) {
            throw new DomainException("MODULE_APPROVAL_ACTION_DISABLED", "当前发布版本未启用审批动作", HttpStatus.CONFLICT);
        }
        // Flow 条件只读取当前已发布、未脱敏且在 LIST 与 PAGE/FLOW 均可读的字段值。
        Set<Long> activeConditionFieldIds = new HashSet<>();
        for (JsonNode field : access.published().configuration().configuration().path("fields")) {
            if ("ACTIVE".equals(field.path("status").asText())
                    && com.unique.unexamine.moduleconfig.manage.StructuredConditionProtocol.TYPES
                            .contains(field.path("fieldType").asText())) {
                activeConditionFieldIds.add(field.path("id").asLong());
            }
        }
        List<PublishedField> candidates = access.published().fields().stream()
                .filter(field -> activeConditionFieldIds.contains(field.id())).toList();
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(context, moduleCode,
                "LIST", List.of("PAGE", "FLOW"), candidates.stream().map(PublishedField::code).toList());
        List<PublishedField> readable = candidates.stream().filter(field -> {
            FieldAccessDecision decision = decisions.get(field.code());
            return decision != null && decision.readable() && decision.maskStrategy() == null;
        }).toList();
        return new ApprovalRecord(access.record().getModuleId(), action.path("id").asLong(),
                access.record().getId(), access.record().getTitle(), readRawValues(recordId, readable));
    }

    /** 发起审批使用经过权限和发布快照验证的业务记录身份。 */
    public record ApprovalRecord(Long moduleId, Long actionId, Long recordId, String title,
                                 Map<String, JsonNode> conditionFields) { }

    @Transactional(readOnly = true)
    public void requireAttachmentField(
            AuthenticatedContext context, String moduleCode, Long recordId,
            String fieldCode, boolean write, String traceId) {
        String action = write ? "UPDATE" : "DETAIL";
        RecordAccess access = requireRecordAccess(context, moduleCode, action, recordId, false, traceId);
        PublishedField field = access.published().fields().stream()
                .filter(item -> item.code().equals(fieldCode)).findFirst()
                .orElseThrow(() -> new DomainException("ATTACHMENT_FIELD_NOT_FOUND",
                        "当前发布版本没有这个附件字段", HttpStatus.NOT_FOUND));
        if (!Set.of("ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP").contains(field.type())) {
            throw new DomainException("ATTACHMENT_FIELD_TYPE_INVALID", "目标字段不是附件或文件字段",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        FieldAccessDecision decision = fieldPolicyResolver.resolveIntersection(
                context, moduleCode, action, List.of("PAGE", "FILE"), List.of(fieldCode)).get(fieldCode);
        boolean allowed = decision != null && (write ? decision.writable() : decision.readable());
        if (!allowed) {
            throw new DomainException("ATTACHMENT_FIELD_ACCESS_DENIED",
                    write ? "当前成员不能向这个业务字段添加附件" : "当前成员不能查看这个业务字段的附件",
                    HttpStatus.FORBIDDEN);
        }
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
        Map<String, Object> printAuditDetail = new LinkedHashMap<>();
        printAuditDetail.put("moduleCode", moduleCode);
        printAuditDetail.put("visibleFieldCodes", protectedView.fields().keySet());
        printAuditDetail.put("dataTenantId", protectedView.dataTenantId());
        if (access.share() != null) printAuditDetail.put("shareId", access.share().getId());
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_PRINT_AUTHORIZED", "BUSINESS_RECORD", recordId.toString(),
                "SUCCESS", permissionSnapshot(context, moduleCode, "PRINT", "PAGE∩FILE"),
                printAuditDetail);
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
                        "BUSINESS_RECORD_TRANSFERRED", "BUSINESS_RECORD_CONVERTED", "BUSINESS_RECORD_CONVERSION_UNLINKED",
                        "BUSINESS_RECORD_TASK_CREATED", "BUSINESS_RECORD_TASK_UPDATED",
                        "BUSINESS_RECORD_LOG_CREATED", "BUSINESS_RECORD_LOG_UPDATED",
                        "BUSINESS_RECORD_ATTACHMENT_ADDED", "BUSINESS_RECORD_ATTACHMENT_REMOVED",
                        "FLOW_BUSINESS_WRITEBACK", "FLOW_BUSINESS_STATUS_MAPPED",
                        "AI_CONFIRMED_RELATED_RECORD_CREATED")
                .orderByDesc(AuditEvent::getOccurTime, AuditEvent::getId)
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

        List<RuntimeRecordTimelineEntry> entries = events.stream()
                .filter(event -> attachmentEventVisible(event, fieldAccess)).map(event -> {
            Map<String, Object> detail = auditDetail(event);
            List<RuntimeRecordTimelineChange> changes = changesByEvent.getOrDefault(event.getId(), List.of()).stream()
                    .map(change -> timelineChange(change, fieldsByCode.get(change.getFieldCode()), fieldAccess.get(change.getFieldCode())))
                    .toList();
            return new RuntimeRecordTimelineEntry(
                    event.getId(), event.getEventCode(), timelineLabel(event.getEventCode()),
                    event.getActorAccountId(), event.getMemberId(),
                    memberNames.getOrDefault(event.getMemberId(), "系统自动执行"),
                    timelineSummary(event.getEventCode(), detail, fieldsByCode),
                    event.getEventCode().startsWith("BUSINESS_RECORD_TASK_") ? "WORK_TASK"
                            : event.getEventCode().startsWith("BUSINESS_RECORD_LOG_") ? "WORK_LOG"
                            : "AI_CONFIRMED_RELATED_RECORD_CREATED".equals(event.getEventCode())
                            ? "BUSINESS_RECORD" : null,
                    event.getEventCode().startsWith("BUSINESS_RECORD_TASK_") ? nullableString(detail.get("taskId"))
                            : event.getEventCode().startsWith("BUSINESS_RECORD_LOG_")
                            ? nullableString(detail.get("logId"))
                            : "AI_CONFIRMED_RELATED_RECORD_CREATED".equals(event.getEventCode())
                            ? nullableString(detail.get("targetRecordId")) : null,
                    event.getOccurTime(), changes);
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
            case "BUSINESS_RECORD_CONVERSION_UNLINKED" -> "解除转化关系";
            case "BUSINESS_RECORD_TASK_CREATED" -> "创建关联任务";
            case "BUSINESS_RECORD_TASK_UPDATED" -> "更新关联任务";
            case "BUSINESS_RECORD_LOG_CREATED" -> "填写关联日志";
            case "BUSINESS_RECORD_LOG_UPDATED" -> "修订关联日志";
            case "BUSINESS_RECORD_ATTACHMENT_ADDED" -> "添加附件";
            case "BUSINESS_RECORD_ATTACHMENT_REMOVED" -> "移除附件";
            case "FLOW_BUSINESS_WRITEBACK" -> "Flow 自动回写";
            case "FLOW_BUSINESS_STATUS_MAPPED" -> "Flow 状态映射";
            case "AI_CONFIRMED_RELATED_RECORD_CREATED" -> "助手创建关联记录";
            default -> eventCode;
        };
    }

    private Map<String, Object> auditDetail(AuditEvent event) {
        if (event.getDetailJson() == null || event.getDetailJson().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(event.getDetailJson(), new TypeReference<>() { });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String timelineSummary(
            String eventCode, Map<String, Object> detail,
            Map<String, PublishedField> fieldsByCode) {
        // 使用执行时保存的名称快照展示归属前后值，避免成员更名后历史变化或暴露技术编号。
        if ("BUSINESS_RECORD_CONVERSION_UNLINKED".equals(eventCode)) {
            return "关系已解除，目标数据和转化历史保留；原因：" + Objects.toString(detail.get("reason"), "未填写");
        }
        if ("BUSINESS_RECORD_TRANSFERRED".equals(eventCode) && detail.get("preview") instanceof Map<?, ?> preview) {
            String owner = "负责人 " + Objects.toString(preview.get("fromOwnerName"), "不可用成员")
                    + " → " + Objects.toString(preview.get("toOwnerName"), "不可用成员");
            String department = "部门 " + Objects.toString(preview.get("fromDepartmentName"), "未设置")
                    + " → " + Objects.toString(preview.get("toDepartmentName"), "未设置");
            List<String> participants = List.of("fromParticipantNames", "toParticipantNames").stream()
                    .map(key -> preview.get(key) instanceof List<?> names && !names.isEmpty()
                            ? String.join("、", names.stream().map(name -> Objects.toString(name, "不可用成员")).toList())
                            : "无").toList();
            return owner + "；" + department + "；相关人 " + participants.get(0) + " → " + participants.get(1)
                    + "；原因：" + Objects.toString(detail.get("reason"), "未填写");
        }
        if (eventCode.startsWith("BUSINESS_RECORD_TASK_")) {
            String title = nullableString(detail.get("taskTitle"));
            String status = workTaskStatusLabel(nullableString(detail.get("taskStatus")));
            return title == null ? status : title + (status == null ? "" : " · " + status);
        }
        if (eventCode.startsWith("BUSINESS_RECORD_LOG_")) {
            String title = nullableString(detail.get("logTitle"));
            String status = workLogStatusLabel(nullableString(detail.get("logStatus")));
            String minutes = nullableString(detail.get("durationMinutes"));
            String suffix = status == null ? "" : " · " + status;
            if (minutes != null) suffix += " · " + minutes + " 分钟";
            return title == null ? (suffix.isBlank() ? null : suffix.substring(3)) : title + suffix;
        }
        if (eventCode.startsWith("BUSINESS_RECORD_ATTACHMENT_")) {
            String fileName = nullableString(detail.get("fileName"));
            String fieldCode = nullableString(detail.get("fieldCode"));
            PublishedField field = fieldCode == null ? null : fieldsByCode.get(fieldCode);
            return (field == null ? "附件" : field.name()) + (fileName == null ? "" : " · " + fileName);
        }
        if ("AI_CONFIRMED_RELATED_RECORD_CREATED".equals(eventCode)) {
            String moduleName = nullableString(detail.get("targetModuleName"));
            String title = nullableString(detail.get("targetRecordTitle"));
            if (moduleName == null) return title;
            return title == null ? moduleName : moduleName + " · " + title;
        }
        if ("FLOW_BUSINESS_WRITEBACK".equals(eventCode)) {
            String status = nullableString(detail.get("afterStatus"));
            Object fields = detail.get("updatedFields");
            String fieldSummary = fields instanceof List<?> list && !list.isEmpty()
                    ? "更新字段 " + String.join("、", list.stream().map(String::valueOf)
                    .map(code -> fieldsByCode.containsKey(code) ? fieldsByCode.get(code).name() : "业务字段")
                    .toList()) : null;
            if (status != null && fieldSummary != null) return fieldSummary + "；状态变为 " + status;
            return fieldSummary != null ? fieldSummary : status == null ? null : "状态变为 " + status;
        }
        if ("FLOW_BUSINESS_STATUS_MAPPED".equals(eventCode)) {
            String before = nullableString(detail.get("beforeStatus"));
            String after = nullableString(detail.get("afterStatus"));
            return before == null && after == null ? null : (before == null ? "" : before) + " → "
                    + (after == null ? "" : after);
        }
        return null;
    }

    private boolean attachmentEventVisible(
            AuditEvent event, Map<String, FieldAccessDecision> fieldAccess) {
        if (!event.getEventCode().startsWith("BUSINESS_RECORD_ATTACHMENT_")) return true;
        String fieldCode = nullableString(auditDetail(event).get("fieldCode"));
        FieldAccessDecision decision = fieldCode == null ? null : fieldAccess.get(fieldCode);
        return decision != null && decision.readable();
    }

    private String workLogStatusLabel(String status) {
        if (status == null) return null;
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "已提交";
            case "WITHDRAWN" -> "已撤回";
            default -> status;
        };
    }

    private String workTaskStatusLabel(String status) {
        if (status == null) return null;
        return switch (status) {
            case "BACKLOG" -> "待规划";
            case "TODO" -> "待开始";
            case "IN_PROGRESS" -> "进行中";
            case "BLOCKED" -> "已阻塞";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String nullableString(Object value) {
        if (value == null) return null;
        String result = String.valueOf(value).strip();
        return result.isEmpty() ? null : result;
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
    public RuntimeRecordView create(AuthenticatedContext context, String moduleCode,
            CreateRuntimeRecordRequest request, String traceId) {
        return requireSavedRecord(createAction(context, moduleCode, request, traceId, BusinessAction.Options.internal()));
    }

    @Transactional
    public BusinessAction.Result<RuntimeRecordView> createAction(
            AuthenticatedContext context, String moduleCode,
            CreateRuntimeRecordRequest request, String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        requireAction(context, moduleCode, "CREATE", traceId);
        PublishedModule published = published(context, moduleCode);
        AuthenticatedContext dataContext = context;
        BusinessAction.Options effectiveOptions = options.materialized();
        BusinessAction.Input input = new BusinessAction.Input(context, dataContext.tenantId(),
                published.configuration().moduleId(), moduleCode, published.configuration().versionId(),
                "CREATE", null, null, request, effectiveOptions, traceId);
        try {
            BusinessAction.Result<RuntimeRecordView> result = actionControl.execute(input, RuntimeRecordView.class, published.configuration().configuration(),
                    () -> actionBindings.resolveForAction(dataContext, input.moduleId(), input.actionCode(),
                            actionVariables(request, validateFields(context, dataContext, moduleCode, "CREATE",
                                    published.fields(), request.fields(), traceId, null)), traceId),
                    () -> validateImport(context, moduleCode, null, request, null, traceId),
                    () -> createDirect(context, moduleCode, request, traceId));
            return protectActionReplay(context, moduleCode, "CREATE", result, traceId);
        } catch (DomainException exception) {
            recordValidationAnomaly(context, published, moduleCode, "CREATE", null,
                    effectiveOptions.idempotencyKey(), exception);
            throw exception;
        }
    }

    private RuntimeRecordView createDirect(
            AuthenticatedContext context,
            String moduleCode,
            CreateRuntimeRecordRequest request,
            String traceId) {
        requireAction(context, moduleCode, "CREATE", traceId);
        PublishedModule published = published(context, moduleCode);
        Map<String, JsonNode> normalizedValues = validateFields(
                context, context, moduleCode, "CREATE", published.fields(), request.fields(), traceId, null);
        // 编号只在真正写入主记录的事务中领取，审批预览和失败的保存不会消耗序列。
        for (PublishedField field : published.fields()) {
            if (field.parentFieldId() != null || !"AUTO_NUMBER".equals(field.type())) continue;
            String value = foundationPolicyService.nextSequenceByCode(
                    context, field.config().path("sequenceCode").asText(), traceId).value();
            normalizedValues.put(field.code(), TextNode.valueOf(value));
        }
        ruleIndexService.validate(published.configuration(), "CREATE", normalizedValues);
        Long defaultOwner = request.ownerMemberId() == null ? currentSystemOwnerMemberId(context) : request.ownerMemberId();
        List<RuntimeRecordParticipant> team = normalizeParticipants(null, request.participantMemberIds(),
                request.participantMembers(), defaultOwner);
        if (!moduleFunctionEnabled(published, "teamEnabled") && !team.isEmpty()) {
            throw new DomainException("MODULE_TEAM_DISABLED", "当前模块未开放团队成员维护", HttpStatus.FORBIDDEN);
        }
        Ownership ownership = validateOwnership(
                context, request.ownerMemberId(), request.departmentId(), team.stream().map(RuntimeRecordParticipant::memberId).toList(), traceId, null);
        Long createdByMemberId = resolveCreatedByMemberId(context, request.createdByMemberId());
        BusinessRecord proposed = new BusinessRecord();
        proposed.setOwnerMemberId(ownership.ownerMemberId());
        proposed.setCreatedByMemberId(createdByMemberId);
        if (!withinProposedScope(context, moduleCode, "CREATE", proposed)) {
            deny(context, moduleCode, "CREATE", traceId, Map.of("reason", "DATA_SCOPE_DENIED"));
        }

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
        // 业务创建人可显式填写；真实提交者仍以 context 写入审计事件。
        record.setCreatedByMemberId(createdByMemberId);
        record.setUpdatedByMemberId(context.memberId());
        record.setArchived(false);
        record.setDeleted(false);
        record.setVersion(0);
        recordService.insert(record);
        replaceValues(context, moduleCode, record.getId(), published.fields(), normalizedValues);
        replaceParticipantRoles(context, record.getId(), team);
        ruleIndexService.syncIndexes(context, published.configuration(), record.getId(), normalizedValues);

        Long auditEventId = auditRecorder.recordWithPermissionSnapshot(
                traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_CREATED", "BUSINESS_RECORD", record.getId().toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "CREATE", "PAGE"),
                Map.of("moduleCode", moduleCode, "configVersionId", published.configuration().versionId(),
                        "fields", redactSecrets(normalizedValues, published.fields()), "participantMemberIds", ownership.participantMemberIds(),
                        "participantMembers", team, "sourceType",
                        "APPLICATION".equals(context.mfaLevel()) ? "APPLICATION" : "SYSTEM_USER"));
        recordFieldChanges(context, moduleCode, auditEventId, Map.of(), normalizedValues, published.fields());
        // 主记录、自定义字段和索引已在同一事务写入；提交后才通知本地扩展点。
        localEvents.publish(context, record.getTenantId(), LocalBusinessEvent.Type.RECORD_CREATED,
                "BUSINESS_RECORD", record.getId().toString(), moduleCode);
        return view(context, moduleCode, "CREATE", List.of("PAGE"), recordService.selectById(record.getId()), published);
    }

    private RuntimeRecordView requireSavedRecord(BusinessAction.Result<RuntimeRecordView> result) {
        if (result.value() == null) {
            // Legacy callers require a record. Fail inside their transaction, before a Flow can commit alone.
            throw new DomainException("ACTION_RECEIPT_REQUIRED", "当前入口尚不能处理流程结果，请从业务模块页面提交此操作",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return result.value();
    }

    private void validateActionRequest(Object request) {
        var violations = actionValidator.validate(request);
        if (!violations.isEmpty()) {
            throw new DomainException("ACTION_INPUT_INVALID", violations.iterator().next().getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public BusinessAction.Delivery actionDelivery(AuthenticatedContext context, String moduleCode, long actionId, String traceId) {
        return actionControl.actionDelivery(context, moduleCode, actionId);
    }

    public BusinessAction.Delivery retryApproval(AuthenticatedContext context, String moduleCode, long actionId) {
        return actionControl.retryApproval(context, moduleCode, actionId);
    }

    /** 审批只读数据按当前查看者鉴权；原意图只能由服务端已核验关联传入，不能取客户端流程变量。 */
    @Transactional(readOnly = true)
    public RuntimeBusinessView approvalBusinessView(AuthenticatedContext viewer, BusinessAction.Input input,
            JsonNode submittedConfiguration, Long recordId, Map<String, Object> nodePolicy, List<String> channels, String traceId) {
        String moduleCode = input.moduleCode();
        JsonNode payload = objectMapper.valueToTree(input.payload());
        if (!permissionChecker.allows(viewer, "MODULE", moduleCode, "DETAIL")) {
            return new RuntimeBusinessView(moduleCode, input.actionCode(), null, null, "当前没有业务详情权限。");
        }
        try {
            RecordAccess access;
            if (recordId != null) {
                access = requireRecordAccess(viewer, moduleCode, "DETAIL", recordId, false, traceId);
                if (!Objects.equals(access.record().getTenantId(), input.dataTenantId())) {
                    throw new DomainException("ACTION_FLOW_NOT_FOUND", "业务记录归属不匹配", HttpStatus.NOT_FOUND);
                }
            } else {
                access = proposedRecordAccess(viewer, input, "DETAIL", traceId);
            }
            if (!Objects.equals(access.published().configuration().moduleId(), input.moduleId())) {
                return new RuntimeBusinessView(moduleCode, input.actionCode(), null, null, "当前模块与原申请不匹配。");
            }
            RuntimeRecordView current = recordId == null ? null : approvalReadRecord(viewer, moduleCode, access, nodePolicy, channels);
            RuntimeRecordView proposed = proposedReadRecord(viewer, input, submittedConfiguration, access, nodePolicy, channels);
            return new RuntimeBusinessView(moduleCode, input.actionCode(), proposed, current,
                    submittedConfiguration.isObject() ? "提交内容仅供核对；正式生效情况以当前数据和操作结果为准。"
                            : "原申请缺少可核验的字段配置快照，提交内容不展示；当前数据仍按当前权限读取。");
        } catch (DomainException exception) {
            if (exception.status() == HttpStatus.FORBIDDEN || exception.status() == HttpStatus.NOT_FOUND) {
                return new RuntimeBusinessView(moduleCode, input.actionCode(), null, null, "当前记录或共享权限已不可访问。");
            }
            throw exception;
        }
    }

    /** 手动关联只读取真实记录，业务快照中的字段及租户声明不能成为授权依据。 */
    @Transactional(readOnly = true)
    public RuntimeBusinessView flowRecordView(AuthenticatedContext viewer, String moduleCode, Long recordId,
            Long dataTenantId, Map<String, Object> nodePolicy, String traceId) {
        try {
            RecordAccess access = requireRecordAccess(viewer, moduleCode, "DETAIL", recordId, false, traceId);
            if (!Objects.equals(access.record().getTenantId(), dataTenantId)) {
                return new RuntimeBusinessView(moduleCode, null, null, null, "关联记录归属不匹配。");
            }
            return new RuntimeBusinessView(moduleCode, null, null,
                    approvalReadRecord(viewer, moduleCode, access, nodePolicy, List.of("PAGE", "FLOW")), null);
        } catch (DomainException exception) {
            if (exception.status() == HttpStatus.FORBIDDEN || exception.status() == HttpStatus.NOT_FOUND) {
                return new RuntimeBusinessView(moduleCode, null, null, null, "当前没有关联业务的查看权限。");
            }
            throw exception;
        }
    }

    /** 新建审批只构造可信归属的内存行；没有正式 ID，不能查询或保存物理字段值。 */
    private RecordAccess proposedRecordAccess(AuthenticatedContext viewer, BusinessAction.Input input,
            String actionCode, String traceId) {
        if (!"CREATE".equals(input.actionCode()) || input.recordId() != null
                || !Objects.equals(viewer.tenantId(), input.dataTenantId())
                || !Objects.equals(viewer.systemId(), input.actor().systemId())
                || !Objects.equals(input.actor().tenantId(), input.dataTenantId())) {
            throw new DomainException("FLOW_NODE_FORM_NOT_AVAILABLE", "当前不能访问该新建申请的节点表单", HttpStatus.FORBIDDEN);
        }
        requireAction(viewer, input.moduleCode(), actionCode, traceId);
        PublishedModule current = published(viewer, input.moduleCode());
        JsonNode payload = objectMapper.valueToTree(input.payload());
        BusinessRecord proposed = new BusinessRecord();
        proposed.setSystemId(viewer.systemId()); proposed.setTenantId(input.dataTenantId());
        proposed.setModuleId(input.moduleId()); proposed.setCreatedByMemberId(resolveCreatedByMemberId(input.actor(),
                payload.path("createdByMemberId").isIntegralNumber()
                        ? payload.path("createdByMemberId").longValue() : null));
        proposed.setOwnerMemberId(payload.path("ownerMemberId").isIntegralNumber()
                ? Long.valueOf(payload.path("ownerMemberId").longValue()) : currentSystemOwnerMemberId(input.actor()));
        proposed.setDepartmentId(payload.path("departmentId").isIntegralNumber()
                ? Long.valueOf(payload.path("departmentId").longValue())
                : ownerDepartmentId(input.actor().systemId(), input.dataTenantId(), proposed.getOwnerMemberId()));
        if (!withinProposedScope(viewer, input.moduleCode(), actionCode, proposed)) {
            throw new DomainException("PERMISSION_DENIED", "申请草稿不在当前授权的数据范围内", HttpStatus.FORBIDDEN);
        }
        return new RecordAccess(proposed, current, tenantContext(viewer, input.dataTenantId()), null);
    }

    /** 虚拟草稿与物理行使用相同的负责人范围语义；未知项默认拒绝。 */
    private boolean withinProposedScope(AuthenticatedContext viewer, String moduleCode, String actionCode,
            BusinessRecord proposed) {
        Long department = currentDepartmentId(viewer);
        Long ownerDepartment = ownerDepartmentId(viewer.systemId(), viewer.tenantId(), proposed.getOwnerMemberId());
        for (DataScopeTerm term : matchingTerms(viewer, moduleCode, actionCode)) {
            boolean matches = switch (term.type()) {
                case "ALL" -> true;
                case "SELF" -> Objects.equals(proposed.getOwnerMemberId(), viewer.memberId());
                case "SELF_AND_SUBORDINATES" -> organizationRelationRepository.subordinateSystemMemberIds(
                        viewer.tenantId(), viewer.tenantMemberId()).contains(proposed.getOwnerMemberId());
                case "DEPARTMENT" -> department != null && department.equals(ownerDepartment);
                case "DEPARTMENT_AND_DESCENDANTS" -> descendantDepartmentIds(viewer, department).contains(ownerDepartment);
                case "SPECIFIED_DEPARTMENTS" -> longValues(term.condition(), "departmentIds").contains(ownerDepartment);
                case "SPECIFIED_MEMBERS" -> longValues(term.condition(), "memberIds").contains(proposed.getOwnerMemberId());
                default -> false;
            };
            if (matches) return true;
        }
        return false;
    }

    private Map<String, FieldAccessDecision> approvalReadDecisions(AuthenticatedContext viewer, String moduleCode,
            PublishedModule published, Map<String, Object> nodePolicy, List<String> channels) {
        Map<String, FieldAccessDecision> decisions = protectSecretDecisions(viewer, moduleCode, "DETAIL", channels,
                published.fields(), fieldPolicyResolver.resolveIntersection(viewer, moduleCode, "DETAIL", channels,
                        published.fields().stream().map(PublishedField::code).toList()));
        decisions.replaceAll((code, decision) -> published.fields().stream().filter(field -> field.code().equals(code))
                .anyMatch(field -> com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.allowsRead(nodePolicy, code)
                        && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.matchesBinding(nodePolicy,
                        field.id(), code, field.type(), field.dictionaryId(), field.referenceModuleId())) ? decision
                : new FieldAccessDecision(code, decision.channel(), false, false, null, decision.contributingRoleIds(), "节点隐藏字段"));
        return decisions;
    }

    /** 当前正式数据重新读取并重算名称，旧审批快照不参与补值。 */
    private RuntimeRecordView approvalReadRecord(AuthenticatedContext viewer, String moduleCode,
            RecordAccess access, Map<String, Object> nodePolicy, List<String> channels) {
        Map<String, FieldAccessDecision> decisions = approvalReadDecisions(viewer, moduleCode, access.published(), nodePolicy, channels);
        RuntimeRecordView current = view(viewer, moduleCode, "DETAIL", channels, access, decisions);
        return current.withFields(current.fields(), current.organizationLabels(), current.fieldPresentation(), List.of());
    }

    /** 原提交仅投影仍为同一 ID/类型的已发布字段；不生成默认值，不将密钥意图解密给查看者。 */
    private RuntimeRecordView proposedReadRecord(AuthenticatedContext viewer, BusinessAction.Input input,
            JsonNode submittedConfiguration, RecordAccess access, Map<String, Object> nodePolicy, List<String> channels) {
        if (!submittedConfiguration.isObject()) return null;
        JsonNode payload = objectMapper.valueToTree(input.payload());
        JsonNode values = payload.path("fields");
        if (!values.isObject()) values = payload.path("values");
        if (!values.isObject()) return null;
        Map<String, JsonNode> submittedFields = new LinkedHashMap<>();
        submittedConfiguration.path("fields").forEach(field -> submittedFields.put(field.path("code").asText(), field));
        List<PublishedField> sameFields = access.published().fields().stream().filter(field -> {
            JsonNode submitted = submittedFields.get(field.code());
            return submitted != null && field.id().equals(submitted.path("id").asLong())
                    && field.type().equals(submitted.path("fieldType").asText())
                    && Objects.equals(field.dictionaryId(), submitted.hasNonNull("dictionaryId") ? submitted.path("dictionaryId").longValue() : null)
                    && Objects.equals(field.referenceModuleId(), submitted.hasNonNull("referenceModuleId") ? submitted.path("referenceModuleId").longValue() : null);
        }).toList();
        RecordAccess proposedAccess = new RecordAccess(access.record(),
                new PublishedModule(access.published().configuration(), sameFields), access.sourceContext(), access.share());
        Map<String, FieldAccessDecision> decisions = approvalReadDecisions(viewer, input.moduleCode(), proposedAccess.published(), nodePolicy, channels);
        Map<String, JsonNode> visible = new LinkedHashMap<>();
        for (PublishedField field : sameFields) {
            if (!values.has(field.code())) continue;
            FieldAccessDecision decision = decisions.get(field.code());
            JsonNode safe = "SECRET".equals(field.type()) && values.hasNonNull(field.code())
                    ? TextNode.valueOf("***") : values.get(field.code());
            safe = fieldPolicyResolver.protect(safe, decision);
            if (safe != null) visible.put(field.code(), safe);
        }
        // 只为实际提交且可读的字段提供描述，避免未提交字段被公共详情误显示成修改为“未填写”。
        decisions.replaceAll((code, decision) -> visible.containsKey(code) ? decision
                : new FieldAccessDecision(code, decision.channel(), false, false, null, decision.contributingRoleIds(), "未提交或不可读"));
        return proposedView(viewer, input, proposedAccess, visible, decisions);
    }

    /** 只读提交与可编辑候选共用真实归属描述，不生成默认值或占位正式行。 */
    private RuntimeRecordView proposedView(AuthenticatedContext viewer, BusinessAction.Input input,
            RecordAccess access, Map<String, JsonNode> visible, Map<String, FieldAccessDecision> decisions) {
        JsonNode payload = objectMapper.valueToTree(input.payload());
        BusinessRecord record = access.record();
        Long owner = payload.path("ownerMemberId").isIntegralNumber()
                ? Long.valueOf(payload.path("ownerMemberId").longValue()) : record.getOwnerMemberId();
        Long department = payload.path("departmentId").isIntegralNumber()
                ? Long.valueOf(payload.path("departmentId").longValue()) : record.getDepartmentId();
        List<Long> participants = longValues(payload, "participantMemberIds");
        return new RuntimeRecordView(null, payload.path("recordNumber").asText(null), payload.path("title").asText(null),
                "PROPOSED", owner, department, input.actor().memberId(), null, input.configVersionId(), null, participants,
                false, null, false, null, null, null, null, input.dataTenantId(), tenantName(input.dataTenantId()),
                viewer.tenantId().equals(input.dataTenantId()), access.share() != null, null, null, List.of(), null,
                organizationLabels(access, owner, department, participants, visible),
                fieldPresentation(viewer, access, visible, decisions), List.of(), visible, List.of());
    }

    /** 审批进度只接受内部已核验原动作；原动作权限、共享和范围必须在查看时仍然有效。 */
    @Transactional(readOnly = true)
    public RuntimeRecordView authorizeActionProgress(AuthenticatedContext actor, BusinessAction.Input input,
                                                     Long resultRecordId, String traceId) {
        String action = "CONVERSION_UNLINK".equals(input.actionCode()) ? "CONVERT" : input.actionCode();
        requireAction(actor, input.moduleCode(), action, traceId);
        Long recordId = input.recordId() == null ? resultRecordId : input.recordId();
        PublishedModule current;
        if (recordId == null) {
            if (!"CREATE".equals(action) || !Objects.equals(actor.tenantId(), input.dataTenantId())) {
                throw new DomainException("ACTION_FLOW_NOT_FOUND", "原操作的业务关系已不可访问", HttpStatus.NOT_FOUND);
            }
            current = published(actor, input.moduleCode());
        } else {
            RecordAccess access = requireRecordAccess(actor, input.moduleCode(), action, recordId, false, traceId);
            if (!Objects.equals(access.record().getTenantId(), input.dataTenantId())) {
                throw new DomainException("ACTION_FLOW_NOT_FOUND", "原操作的业务关系已不可访问", HttpStatus.NOT_FOUND);
            }
            current = access.published();
        }
        if (!Objects.equals(current.configuration().moduleId(), input.moduleId())) {
            throw new DomainException("ACTION_FLOW_NOT_FOUND", "原操作的模块关系已不可访问", HttpStatus.NOT_FOUND);
        }
        JsonNode configured = findPublishedAction(current, action);
        if (!"ACTIVE".equals(configured.path("status").asText())) {
            throw new DomainException("ACTION_FLOW_NOT_FOUND", "原操作的动作已不可访问", HttpStatus.NOT_FOUND);
        }
        // 命名动作的进度沿用该动作权限与数据范围，不要求额外拥有普通编辑权。
        // 发起人没有 DETAIL 仍可看进度；业务内容必须另过当前 DETAIL 和字段策略，绝不取旧快照。
        if (recordId == null || !permissionChecker.allows(actor, "MODULE", input.moduleCode(), "DETAIL")) return null;
        try {
            RecordAccess readable = requireRecordAccess(actor, input.moduleCode(), "DETAIL", recordId, false, traceId);
            return view(actor, input.moduleCode(), "DETAIL", List.of("PAGE"), readable);
        } catch (DomainException exception) {
            if (exception.status() == HttpStatus.FORBIDDEN || exception.status() == HttpStatus.NOT_FOUND) return null;
            throw exception;
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Object resumeApprovedAction(BusinessAction.ApprovedExecution permit, AuthenticatedContext actor) {
        BusinessAction.Input input = permit.input();
        String permissionAction = "CONVERSION_UNLINK".equals(input.actionCode()) ? "CONVERT" : input.actionCode();
        requireAction(actor, input.moduleCode(), permissionAction, input.traceId());
        PublishedModule current;
        if (input.recordId() == null) {
            current = published(actor, input.moduleCode());
        } else {
            RecordAccess access = requireRecordAccess(actor, input.moduleCode(), permissionAction, input.recordId(), false, input.traceId());
            if (!Objects.equals(access.record().getTenantId(), input.dataTenantId())) {
                throw new DomainException("ACTION_DATA_TENANT_CHANGED", "业务数据归属已变化，请重新提交审批", HttpStatus.CONFLICT);
            }
            current = access.published();
        }
        if (!Objects.equals(current.configuration().moduleId(), input.moduleId())
                || !Objects.equals(current.configuration().versionId(), input.configVersionId())
                || !current.configuration().configuration().equals(permit.configurationSnapshot())) {
            throw new DomainException("ACTION_CONFIGURATION_CHANGED", "模块配置已变化，请按新配置重新提交审批", HttpStatus.CONFLICT);
        }
        // These are the same physical handlers used by DIRECT, with their current validations and version checks.
        return switch (input.actionCode()) {
            case "CREATE" -> createDirect(actor, input.moduleCode(), objectMapper.convertValue(input.payload(), CreateRuntimeRecordRequest.class), input.traceId());
            case "UPDATE" -> updateDirect(actor, input.moduleCode(), input.recordId(),
                    objectMapper.convertValue(input.payload(), UpdateRuntimeRecordRequest.class),
                    input.traceId(), "UPDATE", null, "FLOW");
            case "DELETE" -> deleteDirect(actor, input.moduleCode(), input.recordId(), objectMapper.convertValue(input.payload(), RecordLifecycleRequest.class), input.traceId());
            case "TRANSFER" -> transferDirect(actor, input.moduleCode(), input.recordId(),
                    objectMapper.convertValue(input.payload(), RecordTransferRequest.class), input.traceId());
            case "CONVERT" -> convertDirect(actor, input.moduleCode(), input.recordId(),
                    objectMapper.convertValue(input.payload(), RecordConversionRequest.class), input.traceId());
            case "CONVERSION_UNLINK" -> unlinkConversionDirect(actor, input.moduleCode(), input.recordId(),
                    objectMapper.convertValue(input.payload(), RecordConversionUnlinkRequest.class), input.traceId());
            default -> executeManualDirect(actor, input.moduleCode(), input.recordId(), input.actionCode(),
                    objectMapper.convertValue(input.payload(), ManualRecordActionRequest.class), input.traceId());
        };
    }

    private Map<String, Object> actionVariables(Object request) {
        Map<String, Object> variables = objectMapper.convertValue(request, new TypeReference<>() {});
        if (variables.get("fields") instanceof Map<?, ?> fields) {
            fields.forEach((key, value) -> variables.putIfAbsent(String.valueOf(key), value));
        }
        return variables;
    }

    private Map<String, Object> actionVariables(Object request, Map<String, JsonNode> normalizedFields) {
        Map<String, Object> variables = objectMapper.convertValue(request, new TypeReference<>() {});
        variables.put("fields", normalizedFields);
        normalizedFields.forEach(variables::putIfAbsent);
        return variables;
    }

    private BusinessAction.Result<RuntimeRecordView> protectActionReplay(
            AuthenticatedContext context, String moduleCode, String action,
            BusinessAction.Result<RuntimeRecordView> result, String traceId) {
        if (!result.replayed() || result.value() == null) return result;
        RecordAccess access = requireRecordAccess(context, moduleCode, action, result.value().id(), false, traceId, true);
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(context, moduleCode,
                action, List.of("PAGE"), access.published().fields().stream().map(PublishedField::code).toList());
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        result.value().fields().forEach((code, value) -> {
            JsonNode protectedValue = fieldPolicyResolver.protect(value, decisions.get(code));
            if (protectedValue != null) fields.put(code, protectedValue);
        });
        return new BusinessAction.Result<>(result.idempotencyRecordId(), result.outboxId(), result.idempotencyKey(),
                result.status(), true, result.mode(), result.value().withFields(fields, organizationLabels(access, result.value().ownerMemberId(),
                        result.value().departmentId(), result.value().participantMemberIds(), fields),
                        fieldPresentation(context, access, fields, decisions), recordActions(context, moduleCode, access)), result.flowInstanceId());
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
            // 审批和导入预演也必须按当前发布开关拒绝团队写入，不能只在最终落库时拦截。
            Long defaultOwner = request.ownerMemberId() == null ? currentSystemOwnerMemberId(context) : request.ownerMemberId();
            if (!moduleFunctionEnabled(published, "teamEnabled")
                    && !normalizeParticipants(null, request.participantMemberIds(),
                            request.participantMembers(), defaultOwner).isEmpty()) {
                throw new DomainException("MODULE_TEAM_DISABLED", "当前模块未开放团队成员维护", HttpStatus.FORBIDDEN);
            }
            Map<String, JsonNode> importFields = resolveImportDisplayValues(
                    context, published.fields(), request.fields());
            Map<String, JsonNode> normalized = validateFields(
                    context, context, moduleCode, "CREATE", published.fields(), importFields, traceId, null);
            // 预检查须覆盖无权查看的其他负责人记录，不能等执行时才发现唯一值冲突。
            ruleIndexService.validateCreatePreview(context, published.configuration(), normalized);
            Ownership ownership = validateOwnership(context, request.ownerMemberId(), request.departmentId(),
                    request.participantMemberIds(), traceId, null);
            resolveCreatedByMemberId(context, request.createdByMemberId());
            BusinessRecord proposed = new BusinessRecord();
            proposed.setOwnerMemberId(ownership.ownerMemberId());
            if (!withinProposedScope(context, moduleCode, "CREATE", proposed)) {
                deny(context, moduleCode, "CREATE", traceId, Map.of("reason", "DATA_SCOPE_DENIED"));
            }
            return new RuntimeImportValidation(null, null, normalized, null);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, "UPDATE", recordId, true, traceId);
        BusinessRecord record = access.record();
        Long updatedOwner = request.ownerMemberId() == null ? record.getOwnerMemberId() : request.ownerMemberId();
        if (!moduleFunctionEnabled(access.published(), "teamEnabled")
                && !normalizeParticipants(recordId, request.participantMemberIds(), request.participantMembers(),
                        updatedOwner).equals(participantRoles(recordId))) {
            throw new DomainException("MODULE_TEAM_DISABLED", "当前模块未开放团队成员维护", HttpStatus.FORBIDDEN);
        }
        if (expectedVersion == null || !expectedVersion.equals(record.getVersion())) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> beforeFields = readRawValues(recordId, access.published().fields());
        Map<String, JsonNode> importFields = resolveImportDisplayValues(
                access.sourceContext(), access.published().fields(), request.fields());
        Map<String, JsonNode> normalized = validateFields(context, access.sourceContext(), moduleCode, "UPDATE",
                access.published().fields(), importFields, traceId, recordId);
        ruleIndexService.validate(access.published().configuration(), "UPDATE", normalized);
        ruleIndexService.validateUpdatePreview(access.sourceContext(), access.published().configuration(),
                normalized, recordId);
        Ownership ownership = validateOwnership(access.sourceContext(),
                request.ownerMemberId() == null ? record.getOwnerMemberId() : request.ownerMemberId(),
                request.departmentId(), request.participantMemberIds(), traceId, recordId);
        if (access.share() == null) {
            BusinessRecord proposed = new BusinessRecord();
            proposed.setOwnerMemberId(ownership.ownerMemberId());
            if (!withinProposedScope(context, moduleCode, "UPDATE", proposed)) {
                deny(context, moduleCode, "UPDATE", traceId, Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
            }
        }
        CreateRuntimeRecordRequest before = new CreateRuntimeRecordRequest(
                record.getTitle(), record.getRecordNumber(), record.getStatus(), record.getOwnerMemberId(),
                record.getDepartmentId(), participantIds(recordId), beforeFields);
        return new RuntimeImportValidation(recordId, record.getVersion(), normalized, before);
    }

    @Transactional
    public RuntimeRecordView update(AuthenticatedContext context, String moduleCode, Long recordId,
            UpdateRuntimeRecordRequest request, String traceId) {
        return requireSavedRecord(updateAction(context, moduleCode, recordId, request, traceId, BusinessAction.Options.internal()));
    }

    @Transactional
    public BusinessAction.Result<RuntimeRecordView> updateAction(
            AuthenticatedContext context, String moduleCode, Long recordId,
            UpdateRuntimeRecordRequest request, String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        RecordAccess access = requireRecordAccess(context, moduleCode, "UPDATE", recordId, false, traceId);
        requireRecordNotPendingApproval(access.record(), moduleCode);
        PublishedModule published = access.published();
        AuthenticatedContext dataContext = access.sourceContext();
        BusinessAction.Options effectiveOptions = options.materialized();
        BusinessAction.Input input = new BusinessAction.Input(context, dataContext.tenantId(),
                published.configuration().moduleId(), moduleCode, published.configuration().versionId(),
                "UPDATE", recordId, request.version(), request, effectiveOptions, traceId);
        try {
            BusinessAction.Result<RuntimeRecordView> result = actionControl.execute(input, RuntimeRecordView.class, published.configuration().configuration(),
                    () -> actionBindings.resolveForAction(dataContext, input.moduleId(), input.actionCode(),
                            actionVariables(request, validateFields(context, dataContext, moduleCode, "UPDATE",
                                    published.fields(), request.fields(), traceId, recordId)), traceId),
                    () -> validateImport(context, moduleCode, recordId, new CreateRuntimeRecordRequest(
                            request.title(), request.recordNumber(), request.status(), request.ownerMemberId(),
                            request.departmentId(), request.participantMemberIds(), request.fields()), request.version(), traceId),
                    () -> updateDirect(context, moduleCode, recordId, request, traceId));
            return protectActionReplay(context, moduleCode, "UPDATE", result, traceId);
        } catch (DomainException exception) {
            recordValidationAnomaly(context, published, moduleCode, "UPDATE", recordId,
                    effectiveOptions.idempotencyKey(), exception);
            throw exception;
        }
    }

    /** 只把已发布业务规则拒绝转成脱敏异常事实，权限、并发及系统错误保持原处理路径。 */
    private void recordValidationAnomaly(AuthenticatedContext context, PublishedModule published,
            String moduleCode, String actionCode, Long recordId, String actionKey, DomainException exception) {
        if (!Set.of("BUSINESS_RULE_REJECTED", "UNIQUE_RULE_VIOLATION").contains(exception.code())) return;
        Map<String, Object> details = exception.details();
        String ruleType = String.valueOf(details.getOrDefault("anomalyType", "")).strip();
        if (!Set.of("BUSINESS_RULE", "UNIQUE_RULE").contains(ruleType)) return;
        Long ruleId = details.get("ruleId") instanceof Number value ? value.longValue() : null;
        String ruleCode = String.valueOf(details.getOrDefault("ruleCode", "")).strip();
        String ruleName = String.valueOf(details.getOrDefault("ruleName", ruleCode)).strip();
        try {
            flowEvents.enqueueModuleValidationException(context, published.configuration().moduleId(),
                    moduleCode, published.configuration().versionId(), actionCode,
                    recordId, actionKey, exception.code(), ruleType, ruleId, ruleCode, ruleName);
        } catch (RuntimeException deliveryFailure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Module validation anomaly could not be persisted for {} {}", moduleCode, actionCode,
                    deliveryFailure);
        }
    }

    /**
     * 执行已发布的命名动作。动作权限、字段权限、条件和四种 Flow 模式都经过公共动作入口。
     */
    @Transactional
    public BusinessAction.Result<RuntimeRecordView> manualAction(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode,
            ManualRecordActionRequest request, String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        String action = actionCode == null ? "" : actionCode.strip().toUpperCase(Locale.ROOT);
        ManualActionPlan plan = manualActionPlan(context, moduleCode, recordId, action, request, traceId, false);
        requireRecordNotPendingApproval(plan.access().record(), moduleCode);
        BusinessAction.Input input = new BusinessAction.Input(context, plan.access().sourceContext().tenantId(),
                plan.access().published().configuration().moduleId(), moduleCode,
                plan.access().published().configuration().versionId(), action, recordId, request.version(),
                request, options, traceId);
        BusinessAction.Result<RuntimeRecordView> result = actionControl.execute(
                input, RuntimeRecordView.class, plan.access().published().configuration().configuration(),
                () -> actionBindings.resolveManualAction(plan.access().sourceContext(), input.moduleId(),
                        plan.actionId(), actionVariables(plan.update(), plan.update().fields()), traceId),
                () -> manualActionPlan(context, moduleCode, recordId, action, request, traceId, true),
                () -> executeManualDirect(context, moduleCode, recordId, action, request, traceId));
        return protectActionReplay(context, moduleCode, action, result, traceId);
    }

    /** 字段动作合并局部输入；状态动作保留全部原值，两者都复用标准更新校验。 */
    private ManualActionPlan manualActionPlan(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode,
            ManualRecordActionRequest request, String traceId, boolean checkVersion) {
        ManualActionDefinition definition = manualActionDefinition(context, moduleCode, recordId, actionCode, traceId);
        RecordAccess access = definition.access();
        JsonNode action = definition.action();
        JsonNode config = parseConfig(action.path("configJson").asText("{}"));
        boolean setStatus = "SET_STATUS".equals(config.path("operation").asText());
        Set<String> configured = new LinkedHashSet<>();
        definition.parameters().forEach(field -> configured.add(field.code()));
        Set<String> preservedSecrets = definition.parameters().stream().filter(field -> "SECRET".equals(field.type()))
                .map(PublishedField::code).collect(java.util.stream.Collectors.toSet());
        if ((setStatus && !request.values().isEmpty()) || !configured.containsAll(request.values().keySet()) || configured.stream()
                .anyMatch(code -> !request.values().containsKey(code) && !preservedSecrets.contains(code))) {
            throw new DomainException("MANUAL_ACTION_VALUES_INVALID", "请完整填写动作要求的业务字段",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        BusinessRecord record = access.record();
        if (checkVersion && !request.version().equals(record.getVersion())) {
            recordVersionConflict(context, moduleCode, recordId, request.version(), record.getVersion(), traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> normalized = setStatus ? Map.of() : validateFields(context, access.sourceContext(), moduleCode,
                actionCode, definition.parameters(), request.values(), traceId, recordId);
        // 未选字段原样保留，避免局部动作覆盖只读、脱敏或无写入权限的其他业务值。
        Map<String, JsonNode> merged = new LinkedHashMap<>(readRawValues(recordId, access.published().fields()));
        configured.forEach(merged::remove);
        merged.putAll(normalized);
        String targetStatus = setStatus ? config.path("statusValue").asText() : record.getStatus();
        UpdateRuntimeRecordRequest update = new UpdateRuntimeRecordRequest(
                record.getTitle(), record.getRecordNumber(), targetStatus, record.getOwnerMemberId(),
                record.getDepartmentId(), participantIds(recordId), merged, request.version());
        long actionId = action.path("id").asLong(0);
        if (actionId <= 0) throw new DomainException("MANUAL_ACTION_CONFIG_INVALID", "手动动作配置无效",
                HttpStatus.UNPROCESSABLE_ENTITY);
        return new ManualActionPlan(access, actionId, update);
    }

    /** 命名动作按自身编码和字段授权执行；普通编辑权限不替代或额外限制该动作。 */
    private ManualActionDefinition manualActionDefinition(
            AuthenticatedContext actor, String moduleCode, Long recordId, String actionCode, String traceId) {
        RecordAccess access = requireRecordAccess(actor, moduleCode, actionCode, recordId, true, traceId);
        JsonNode action = findPublishedAction(access.published(), actionCode);
        JsonNode config = parseConfig(action.path("configJson").asText("{}"));
        if (!"CUSTOM".equals(action.path("actionType").asText()) || !"ACTIVE".equals(action.path("status").asText())
                || !supportedManualActionConfig(config) || action.path("id").asLong(0) <= 0) {
            throw new DomainException("MANUAL_ACTION_NOT_AVAILABLE", "手动动作未发布、停用或配置无效", HttpStatus.CONFLICT);
        }
        List<PublishedField> parameters = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode code : config.path("parameterFieldCodes")) {
            PublishedField field = access.published().fields().stream().filter(f -> f.code().equals(code.asText())).findFirst().orElse(null);
            if (field == null || !unique.add(field.code()) || field.type().startsWith("SYSTEM_")
                    || field.config().path("readOnly").asBoolean(false)) {
                throw new DomainException("MANUAL_ACTION_CONFIG_INVALID", "动作参数不存在、重复或只读", HttpStatus.CONFLICT);
            }
            parameters.add(field);
        }
        List<String> codes = parameters.stream().map(PublishedField::code).toList();
        Map<String, FieldAccessDecision> actionFields = fieldPolicyResolver.resolve(actor, moduleCode, actionCode, "PAGE", codes);
        if (codes.stream().anyMatch(code -> !actionFields.get(code).writable())) {
            deny(actor, moduleCode, actionCode, traceId, Map.of("recordId", recordId, "reason", "ACTION_FIELD_NOT_WRITABLE"));
        }
        return new ManualActionDefinition(access, action, parameters, actionFields);
    }

    /** 发布快照中的命名动作只接受字段填写或固定状态两种效果，损坏配置不进入运行链。 */
    private boolean supportedManualActionConfig(JsonNode config) {
        JsonNode parameters = config.path("parameterFieldCodes");
        if (!parameters.isArray()) return false;
        String operation = config.path("operation").asText();
        if ("UPDATE_FIELDS".equals(operation)) return !parameters.isEmpty() && parameters.size() <= 20;
        if (!"SET_STATUS".equals(operation) || !parameters.isEmpty()) return false;
        JsonNode status = config.path("statusValue");
        String value = status.isTextual() ? status.asText() : "";
        return !value.isBlank() && value.length() <= 64 && value.equals(value.strip())
                && value.chars().noneMatch(Character::isISOControl);
    }

    /** 只给本记录实际动作的参数，不暴露其他可读字段、默认值或任意来源目录。 */
    @Transactional(readOnly = true)
    public RuntimeManualActionContext manualActionContext(
            AuthenticatedContext actor, String moduleCode, Long recordId, String actionCode, String traceId) {
        String code = actionCode.strip().toUpperCase(Locale.ROOT);
        ManualActionDefinition definition = manualActionDefinition(actor, moduleCode, recordId, code, traceId);
        RecordAccess source = definition.access();
        RecordAccess parameterAccess = new RecordAccess(source.record(),
                new PublishedModule(source.published().configuration(), definition.parameters()), source.sourceContext(), source.share());
        RuntimeRecordView record = view(actor, moduleCode, code, List.of("PAGE"), parameterAccess);
        Map<String, JsonNode> values = new LinkedHashMap<>();
        record.fields().forEach((field, value) -> values.put(field, fieldPolicyResolver.protect(value, definition.actionFields().get(field))));
        Map<String, FieldAccessDecision> decisions = new LinkedHashMap<>();
        List<JsonNode> parameters = new ArrayList<>();
        for (PublishedField field : definition.parameters()) {
            FieldAccessDecision action = definition.actionFields().get(field.code());
            String mask = action.maskStrategy();
            if ("SECRET".equals(field.type())) mask = "FULL";
            FieldAccessDecision intersection = new FieldAccessDecision(field.code(), "PAGE", true, true, mask,
                    action.contributingRoleIds(), "按当前命名动作字段权限访问");
            decisions.put(field.code(), intersection);
            for (JsonNode configured : source.published().configuration().configuration().path("fields")) {
                if (!field.code().equals(configured.path("code").asText())) continue;
                com.fasterxml.jackson.databind.node.ObjectNode parameter = configured.deepCopy();
                com.fasterxml.jackson.databind.node.ObjectNode fieldConfig = field.config().deepCopy();
                fieldConfig.remove("defaultValue");
                parameter.put("configJson", fieldConfig.toString());
                parameter.set("access", objectMapper.valueToTree(intersection));
                parameters.add(parameter);
                break;
            }
        }
        record = record.withFields(values, organizationLabels(parameterAccess, record.ownerMemberId(), record.departmentId(),
                record.participantMemberIds(), values), fieldPresentation(actor, parameterAccess, values, decisions), List.of());
        // 确认规则同样来自实际来源；不能继续沿用接收方同编码动作的确认配置。
        JsonNode actionConfig = parseConfig(definition.action().path("configJson").asText("{}"));
        return new RuntimeManualActionContext(code, definition.action().path("name").asText(), record, parameters,
                actionConfig.path("confirmation").asBoolean(false), actionConfig.path("confirmationText").asText(""),
                actionConfig.path("operation").asText(), actionConfig.path("statusValue").asText(""));
    }

    private record ManualActionDefinition(RecordAccess access, JsonNode action, List<PublishedField> parameters,
                                          Map<String, FieldAccessDecision> actionFields) {
    }

    private RuntimeRecordView executeManualDirect(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode,
            ManualRecordActionRequest request, String traceId) {
        ManualActionPlan plan = manualActionPlan(context, moduleCode, recordId, actionCode, request, traceId, true);
        return updateDirect(context, moduleCode, recordId, plan.update(), traceId, actionCode, plan.update().fields());
    }

    private RuntimeRecordView updateDirect(
            AuthenticatedContext context,
            String moduleCode,
            Long recordId,
            UpdateRuntimeRecordRequest request,
            String traceId) {
        return updateDirect(context, moduleCode, recordId, request, traceId, "UPDATE", null);
    }

    private RuntimeRecordView updateDirect(
            AuthenticatedContext context, String moduleCode, Long recordId,
            UpdateRuntimeRecordRequest request, String traceId, String permissionAction,
            Map<String, JsonNode> validatedFields) {
        return updateDirect(context, moduleCode, recordId, request, traceId, permissionAction, validatedFields, "PAGE");
    }

    private RuntimeRecordView updateDirect(
            AuthenticatedContext context, String moduleCode, Long recordId,
            UpdateRuntimeRecordRequest request, String traceId, String permissionAction,
            Map<String, JsonNode> validatedFields, String sourceType) {
        RecordAccess access = requireRecordAccess(context, moduleCode, permissionAction, recordId, true, traceId);
        // 审批恢复持有原动作许可；其他更新入口（含命名动作和自动节点）均须等待当前审批结束。
        if (!"FLOW".equals(sourceType)) requireRecordNotPendingApproval(access.record(), moduleCode);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();
        if (!request.version().equals(record.getVersion())) {
            recordVersionConflict(context, moduleCode, recordId, request.version(), record.getVersion(), traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> before = readRawValues(recordId, published.fields());
        String beforeStatus = record.getStatus();
        // 局部动作已经校验输入并合并原值；普通编辑仍执行全表单校验。
        Map<String, JsonNode> normalizedValues = validatedFields == null ? validateFields(
                context, access.sourceContext(), moduleCode, "UPDATE", published.fields(), request.fields(), traceId, recordId)
                : validatedFields;
        ruleIndexService.validate(published.configuration(), "UPDATE", normalizedValues);
        Long updatedOwner = request.ownerMemberId() == null ? record.getOwnerMemberId() : request.ownerMemberId();
        List<RuntimeRecordParticipant> team = normalizeParticipants(recordId, request.participantMemberIds(),
                request.participantMembers(), updatedOwner);
        if (!moduleFunctionEnabled(published, "teamEnabled") && !team.equals(participantRoles(recordId))) {
            throw new DomainException("MODULE_TEAM_DISABLED", "当前模块未开放团队成员维护", HttpStatus.FORBIDDEN);
        }
        Ownership ownership = validateOwnership(
                access.sourceContext(), updatedOwner, request.departmentId(),
                team.stream().map(RuntimeRecordParticipant::memberId).toList(), traceId, recordId);
        if (access.share() == null) {
            BusinessRecord proposed = new BusinessRecord();
            proposed.setOwnerMemberId(ownership.ownerMemberId());
            if (!withinProposedScope(context, moduleCode, permissionAction, proposed)) {
                deny(context, moduleCode, permissionAction, traceId,
                        Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
            }
        }

        record.setUpdatedConfigVersionId(published.configuration().versionId());
        record.setRecordNumber(blankToNull(request.recordNumber()));
        record.setTitle(request.title().strip());
        record.setStatus(blankToDefault(request.status(), "ACTIVE"));
        record.setOwnerMemberId(ownership.ownerMemberId());
        record.setDepartmentId(ownership.departmentId());
        record.setUpdatedByMemberId(context.memberId());
        record.setUpdateTime(null);
        if (recordService.updateById(record) == 0) {
            recordVersionConflict(context, moduleCode, recordId, request.version(), null, traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        replaceValues(access.sourceContext(), moduleCode, recordId, published.fields(), normalizedValues);
        replaceParticipantRoles(access.sourceContext(), recordId, team);
        ruleIndexService.syncIndexes(access.sourceContext(), published.configuration(), recordId, normalizedValues);
        // 普通编辑和命名动作共用状态历史，避免仅审计事件有前后值而业务详情缺少状态轨迹。
        if (!Objects.equals(beforeStatus, record.getStatus())) {
            BizRecordStateHistory history = new BizRecordStateHistory();
            history.setRecordId(recordId);
            history.setFromStatus(beforeStatus);
            history.setToStatus(record.getStatus());
            history.setReason("FLOW".equals(sourceType) ? "审批通过后更新记录状态"
                    : "UPDATE".equals(permissionAction) ? "编辑记录状态" : "业务动作更新记录状态");
            history.setSourceType("FLOW".equals(sourceType) ? "FLOW"
                    : "UPDATE".equals(permissionAction) ? "PAGE" : "ACTION");
            history.setSourceId(traceId);
            history.setChangedByMemberId(context.memberId());
            stateHistoryService.insert(history);
        }

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("moduleCode", moduleCode);
        auditDetail.put("configVersionId", published.configuration().versionId());
        auditDetail.put("beforeFields", redactSecrets(before, published.fields()));
        auditDetail.put("afterFields", redactSecrets(normalizedValues, published.fields()));
        auditDetail.put("beforeStatus", beforeStatus);
        auditDetail.put("afterStatus", record.getStatus());
        auditDetail.put("participantMemberIds", ownership.participantMemberIds());
        auditDetail.put("participantMembers", team);
        auditDetail.put("actorTenantId", context.tenantId());
        auditDetail.put("dataTenantId", record.getTenantId());
        if (access.share() != null) auditDetail.put("shareId", access.share().getId());
        Long auditEventId = auditRecorder.recordWithPermissionSnapshot(
                traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_RECORD_UPDATED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, permissionAction, "PAGE"),
                auditDetail);
        recordFieldChanges(context, moduleCode, auditEventId, before, normalizedValues, published.fields());
        if (access.share() != null) recordShareUsage(context, access.share(), permissionAction, "SUCCESS");
        localEvents.publish(context, record.getTenantId(), LocalBusinessEvent.Type.RECORD_UPDATED,
                "BUSINESS_RECORD", recordId.toString(), moduleCode);
        return view(context, moduleCode, permissionAction, List.of("PAGE"), new RecordAccess(
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
        record.setUpdateTime(LocalDateTime.now());
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

    /** 与审批申请串行检查同一记录，防止普通写入绕过待审批编辑限制。 */
    private void requireRecordNotPendingApproval(BusinessRecord record, String moduleCode) {
        recordService.selectOne(Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getId, record.getId())
                .eq(BusinessRecord::getSystemId, record.getSystemId())
                .eq(BusinessRecord::getTenantId, record.getTenantId()).last("FOR UPDATE"));
        if (approvalCoordinator.getObject().pendingForRecord(record.getSystemId(),
                record.getTenantId(), moduleCode, record.getId())) {
            throw new DomainException("RECORD_APPROVAL_PENDING", "记录正在审批中，请等待审批结束后再编辑", HttpStatus.CONFLICT);
        }
    }

    /** 审批未通过时仅根据已保存的原动作和已发布映射更改已有记录状态，不重放被拒绝的业务字段。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyApprovalTerminalStatus(BusinessAction.Input input, String targetStatus,
                                            String flowStatus, Long flowId) {
        if (input.recordId() == null) return;
        String target = targetStatus == null ? "" : targetStatus.strip().toUpperCase(Locale.ROOT);
        if (!target.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new DomainException("FLOW_STATUS_MAPPING_INVALID", "审批结果的业务状态编码无效", HttpStatus.CONFLICT);
        }
        BusinessRecord record = recordService.selectOne(Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getId, input.recordId())
                .eq(BusinessRecord::getSystemId, input.actor().systemId())
                .eq(BusinessRecord::getTenantId, input.dataTenantId())
                .eq(BusinessRecord::getModuleId, input.moduleId()).last("FOR UPDATE"));
        if (record == null || Boolean.TRUE.equals(record.getDeleted()) || Boolean.TRUE.equals(record.getArchived())) {
            throw new DomainException("FLOW_STATUS_RECORD_INVALID", "审批关联记录不存在或已被移出正常业务范围", HttpStatus.CONFLICT);
        }
        String before = record.getStatus();
        if (Objects.equals(before, target)) return;
        record.setStatus(target);
        record.setUpdatedByMemberId(input.actor().memberId());
        record.setUpdateTime(LocalDateTime.now());
        if (recordService.updateById(record) != 1) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "审批关联记录已变化，请核对后重试", HttpStatus.CONFLICT);
        }
        BizRecordStateHistory history = new BizRecordStateHistory();
        history.setRecordId(record.getId());
        history.setFromStatus(before);
        history.setToStatus(target);
        history.setReason("审批" + ("REJECTED".equals(flowStatus) ? "拒绝" : "撤回") + "后状态联动");
        history.setSourceType("FLOW");
        history.setSourceId(flowId.toString());
        history.setChangedByMemberId(input.actor().memberId());
        stateHistoryService.insert(history);
        auditRecorder.record(input.traceId(), input.actor().accountId(), record.getSystemId(), record.getTenantId(),
                input.actor().memberId(), "FLOW_BUSINESS_STATUS_MAPPED", "BUSINESS_RECORD",
                record.getId().toString(), "SUCCESS",
                Map.of("moduleCode", input.moduleCode(), "flowInstanceId", flowId,
                        "flowStatus", flowStatus, "beforeStatus", before, "afterStatus", target));
    }

    /**
     * 自动数据节点在独立事务内进入统一动作执行器。业务写入先提交，流程推进失败后用同一幂等键重放，
     * 不会再次创建或更新数据；Flow 内部动作不再次解析同一 Flow 绑定，避免自触发循环。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessAction.Result<RuntimeRecordView> applyFlowAutomaticAction(
            AuthenticatedContext context, String operation, String moduleCode, Long recordId, String title,
            Map<String, Object> fieldUpdates, Long frozenModuleVersionId,
            List<FlowAutomaticActionPolicy.FieldBinding> frozenFields,
            BusinessAction.Options options, String traceId) {
        String action = operation == null ? "" : operation.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("CREATE", "UPDATE").contains(action)) {
            throw new DomainException("FLOW_AUTOMATIC_OPERATION_INVALID", "自动业务节点只支持新建或更新数据",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        PublishedModule published = published(context, moduleCode);
        verifyFlowAutomaticSnapshot(published, frozenModuleVersionId, frozenFields);
        Map<String, JsonNode> requested = new LinkedHashMap<>();
        (fieldUpdates == null ? Map.<String, Object>of() : fieldUpdates)
                .forEach((code, value) -> requested.put(code, objectMapper.valueToTree(value)));
        if ("CREATE".equals(action)) {
            if (title == null || title.isBlank()) {
                throw new DomainException("FLOW_AUTOMATIC_TITLE_REQUIRED", "新建数据节点必须得到非空标题",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            CreateRuntimeRecordRequest request = new CreateRuntimeRecordRequest(title.strip(), null, "ACTIVE",
                    context.memberId(), null, List.of(), requested);
            validateActionRequest(request);
            BusinessAction.Input input = new BusinessAction.Input(context, context.tenantId(),
                    published.configuration().moduleId(), moduleCode, published.configuration().versionId(),
                    action, null, null, request, options, traceId);
            return protectActionReplay(context, moduleCode, action,
                    actionControl.execute(input, RuntimeRecordView.class, published.configuration().configuration(),
                            () -> null, () -> { }, () -> createDirect(context, moduleCode, request, traceId)), traceId);
        }
        if (recordId == null) {
            throw new DomainException("FLOW_AUTOMATIC_RECORD_REQUIRED", "更新数据节点没有得到目标记录",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, "UPDATE", recordId, true, traceId);
        BusinessRecord record = access.record();
        Map<String, JsonNode> merged = new LinkedHashMap<>(readRawValues(recordId, access.published().fields()));
        requested.forEach((code, value) -> {
            if (empty(value)) merged.remove(code);
            else merged.put(code, value);
        });
        UpdateRuntimeRecordRequest request = new UpdateRuntimeRecordRequest(record.getTitle(), record.getRecordNumber(),
                record.getStatus(), record.getOwnerMemberId(), record.getDepartmentId(), participantIds(recordId),
                merged, record.getVersion());
        validateActionRequest(request);
        BusinessAction.Input input = new BusinessAction.Input(context, access.sourceContext().tenantId(),
                published.configuration().moduleId(), moduleCode, published.configuration().versionId(),
                action, recordId, record.getVersion(), request, options, traceId);
        return protectActionReplay(context, moduleCode, action,
                actionControl.execute(input, RuntimeRecordView.class, published.configuration().configuration(),
                        () -> null, () -> { }, () -> updateDirect(context, moduleCode, recordId, request, traceId)), traceId);
    }

    private void verifyFlowAutomaticSnapshot(PublishedModule published, Long versionId,
                                             List<FlowAutomaticActionPolicy.FieldBinding> bindings) {
        if (versionId == null || !Objects.equals(versionId, published.configuration().versionId())) {
            throw new DomainException("FLOW_AUTOMATIC_MODULE_CHANGED",
                    "自动业务节点使用的模块版本已变化，请检查并重新发布流程", HttpStatus.CONFLICT);
        }
        Map<String, PublishedField> current = published.fields().stream().collect(java.util.stream.Collectors.toMap(
                PublishedField::code, field -> field, (left, right) -> left, LinkedHashMap::new));
        for (FlowAutomaticActionPolicy.FieldBinding frozen : bindings == null ? List.<FlowAutomaticActionPolicy.FieldBinding>of() : bindings) {
            PublishedField field = current.get(frozen.code());
            if (field == null || !Objects.equals(field.id(), frozen.id()) || !Objects.equals(field.type(), frozen.fieldType())
                    || !Objects.equals(field.dictionaryId(), frozen.dictionaryId())
                    || !Objects.equals(field.referenceModuleId(), frozen.referenceModuleId())) {
                throw new DomainException("FLOW_AUTOMATIC_FIELD_CHANGED",
                        "自动业务节点字段“" + frozen.name() + "”已变化，请检查并重新发布流程", HttpStatus.CONFLICT);
            }
        }
    }

    /** 兼容旧字段回写配置，但写入同样必须经过统一动作、幂等和可靠事件。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessAction.Result<RuntimeRecordView> applyFlowWritebackAction(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode,
            String expectedCurrentStatus, String targetStatus, Map<String, Object> fieldUpdates,
            BusinessAction.Options options, String traceId) {
        String action = actionCode == null ? "" : actionCode.strip().toUpperCase(Locale.ROOT);
        if (action.isBlank()) {
            throw new DomainException("FLOW_WRITEBACK_ACTION_REQUIRED",
                    "字段回写节点必须配置业务动作", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, action, recordId, true, traceId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedCurrentStatus", expectedCurrentStatus);
        payload.put("targetStatus", targetStatus);
        payload.put("fieldUpdates", fieldUpdates == null ? Map.of() : fieldUpdates);
        BusinessAction.Input input = new BusinessAction.Input(context, access.sourceContext().tenantId(),
                access.published().configuration().moduleId(), moduleCode,
                access.published().configuration().versionId(), action, recordId, access.record().getVersion(),
                payload, options, traceId);
        return protectActionReplay(context, moduleCode, action,
                actionControl.execute(input, RuntimeRecordView.class,
                        access.published().configuration().configuration(), () -> null, () -> { },
                        () -> applyFlowWritebackDirect(context, moduleCode, recordId, action,
                                expectedCurrentStatus, targetStatus, fieldUpdates, traceId)), traceId);
    }

    private RuntimeRecordView applyFlowWritebackDirect(
            AuthenticatedContext context, String moduleCode, Long recordId, String actionCode,
            String expectedCurrentStatus, String targetStatus, Map<String, Object> fieldUpdates,
            String traceId) {
        String action = actionCode == null ? "" : actionCode.strip().toUpperCase(Locale.ROOT);
        if (action.isBlank()) {
            throw new DomainException("FLOW_WRITEBACK_ACTION_REQUIRED",
                    "字段回写节点必须配置业务动作", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String target = targetStatus == null ? "" : targetStatus.strip().toUpperCase(Locale.ROOT);
        if (!target.isBlank() && !target.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new DomainException("FLOW_STATUS_MAPPING_INVALID",
                    "Flow 业务状态映射目标无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        RecordAccess access = requireRecordAccess(context, moduleCode, action, recordId, true, traceId);
        BusinessRecord record = access.record();
        if (expectedCurrentStatus != null && !expectedCurrentStatus.isBlank()
                && !expectedCurrentStatus.equalsIgnoreCase(record.getStatus())) {
            throw new DomainException("FLOW_STATUS_MAPPING_INCOMPATIBLE",
                    "业务数据当前状态与字段回写前置状态不兼容", HttpStatus.CONFLICT);
        }
        PublishedModule published = access.published();
        Map<String, PublishedField> fieldsByCode = new LinkedHashMap<>();
        published.fields().forEach(field -> fieldsByCode.put(field.code(), field));
        Map<String, Object> requested = fieldUpdates == null ? Map.of() : fieldUpdates;
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolve(
                context, moduleCode, action, "FLOW", new ArrayList<>(requested.keySet()));
        Map<String, JsonNode> before = readRawValues(recordId, published.fields());
        Map<String, JsonNode> merged = new LinkedHashMap<>(before);
        List<String> errors = new ArrayList<>();
        requested.forEach((code, raw) -> {
            PublishedField field = fieldsByCode.get(code);
            if (field == null) {
                errors.add(code + ": 字段未发布或不存在");
                return;
            }
            FieldAccessDecision decision = decisions.get(code);
            if (decision == null || !decision.writable()) {
                errors.add(code + ": Flow 渠道没有字段写入权限");
                return;
            }
            JsonNode value = objectMapper.valueToTree(raw);
            if (empty(value)) {
                merged.remove(code);
                return;
            }
            try {
                JsonNode normalized = normalize(access.sourceContext(), field, value);
                validateReferenceValue(access.sourceContext(), field, normalized);
                if (!Objects.equals(before.get(code), normalized)) validateActorReference(context, field, normalized, traceId);
                merged.put(code, normalized);
            } catch (IllegalArgumentException exception) {
                errors.add(code + ": " + exception.getMessage());
            }
        });
        published.fields().stream().filter(PublishedField::required)
                .filter(field -> empty(merged.get(field.code())))
                .forEach(field -> errors.add(field.code() + ": 必填字段不能为空"));
        if (!errors.isEmpty()) {
            throw new DomainException("FLOW_WRITEBACK_FIELD_INVALID", String.join("；", errors),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        ruleIndexService.validate(published.configuration(), action, merged);
        String beforeStatus = record.getStatus();
        if (!target.isBlank()) record.setStatus(target);
        record.setUpdatedConfigVersionId(published.configuration().versionId());
        record.setUpdatedByMemberId(context.memberId());
        record.setUpdateTime(LocalDateTime.now());
        if (recordService.updateById(record) != 1) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
        replaceValues(access.sourceContext(), moduleCode, recordId, published.fields(), merged);
        ruleIndexService.syncIndexes(access.sourceContext(), published.configuration(), recordId, merged);
        Long auditEventId = auditRecorder.recordWithPermissionSnapshot(
                traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FLOW_BUSINESS_WRITEBACK", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, action, "FLOW"),
                Map.of("moduleCode", moduleCode, "actionCode", action,
                        "beforeStatus", beforeStatus, "afterStatus", record.getStatus(),
                        "updatedFields", requested.keySet()));
        recordFieldChanges(context, moduleCode, auditEventId, before, merged, published.fields());
        if (!Objects.equals(beforeStatus, record.getStatus())) {
            BizRecordStateHistory history = new BizRecordStateHistory();
            history.setRecordId(recordId);
            history.setFromStatus(beforeStatus);
            history.setToStatus(record.getStatus());
            history.setReason("Flow 字段回写节点更新业务状态");
            history.setSourceType("FLOW");
            history.setSourceId(traceId);
            history.setChangedByMemberId(context.memberId());
            stateHistoryService.insert(history);
        }
        if (access.share() != null) recordShareUsage(context, access.share(), action, "SUCCESS");
        return view(context, moduleCode, action, List.of("FLOW"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    @Transactional(readOnly = true)
    public RuntimeRecordTransferPreview transferPreview(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordTransferRequest request, String traceId) {
        validateActionRequest(request);
        RecordAccess access = requireRecordAccess(context, moduleCode, "TRANSFER", recordId, true, traceId);
        BusinessRecord record = access.record();
        requireRecordVersion(context, moduleCode, "TRANSFER", record, request.version(), traceId);
        List<Long> currentParticipants = participantIds(recordId);
        List<Long> targetParticipants = request.participantMemberIds() == null
                ? currentParticipants : new LinkedHashSet<>(request.participantMemberIds()).stream().sorted().toList();
        if (!moduleFunctionEnabled(access.published(), "teamEnabled")
                && !currentParticipants.equals(targetParticipants)) {
            throw new DomainException("MODULE_TEAM_DISABLED", "当前模块未开放团队成员维护", HttpStatus.FORBIDDEN);
        }
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
                currentParticipants, targetParticipants,
                currentParticipants.stream().map(id -> memberName(access.sourceContext(), id)).toList(),
                targetParticipants.stream().map(id -> memberName(access.sourceContext(), id)).toList(),
                relationService.selectCount(Wrappers.<BizRecordRelation>lambdaQuery()
                        .eq(BizRecordRelation::getSystemId, context.systemId())
                        .eq(BizRecordRelation::getTenantId, record.getTenantId())
                        .eq(BizRecordRelation::getSourceRecordId, recordId)),
                relationService.selectCount(Wrappers.<BizRecordRelation>lambdaQuery()
                        .eq(BizRecordRelation::getSystemId, context.systemId())
                        .eq(BizRecordRelation::getTenantId, record.getTenantId())
                        .eq(BizRecordRelation::getTargetRecordId, recordId)));
    }

    @Transactional
    public RuntimeRecordView transfer(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordTransferRequest request, String traceId) {
        return requireSavedRecord(transferAction(context, moduleCode, recordId, request, traceId,
                BusinessAction.Options.internal()));
    }

    /** 转移共用动作事务、权限、幂等和四种 Flow 模式；审批前不改变正式归属。 */
    @Transactional
    public BusinessAction.Result<RuntimeRecordView> transferAction(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordTransferRequest request, String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        RecordAccess access = requireRecordAccess(context, moduleCode, "TRANSFER", recordId, true, traceId);
        requireRecordNotPendingApproval(access.record(), moduleCode);
        PublishedModule published = access.published();
        BusinessAction.Input input = new BusinessAction.Input(context, access.sourceContext().tenantId(),
                published.configuration().moduleId(), moduleCode, published.configuration().versionId(),
                "TRANSFER", recordId, request.version(), request, options, traceId);
        // 目标与版本校验留给首次执行；回放读取首次结果，不重复转移或申请审批。
        BusinessAction.Result<RuntimeRecordView> result = actionControl.execute(input, RuntimeRecordView.class,
                published.configuration().configuration(),
                () -> actionBindings.resolveForAction(access.sourceContext(), input.moduleId(), "TRANSFER",
                        actionVariables(request, readRawValues(recordId, published.fields())), traceId),
                () -> transferPreview(context, moduleCode, recordId, request, traceId),
                () -> transferDirect(context, moduleCode, recordId, request, traceId));
        return protectActionReplay(context, moduleCode, "TRANSFER", result, traceId);
    }

    /** 唯一物理转移处理：批准后重查权限、版本和有效目标，保留模块及数据租户。 */
    private RuntimeRecordView transferDirect(
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
        record.setUpdatedConfigVersionId(published.configuration().versionId());
        record.setUpdatedByMemberId(context.memberId());
        // 沿用基础 CRUD 的数据库自动更新时间，避免应用和数据库时区不同导致时间倒退。
        record.setUpdateTime(null);
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
        var result = convertAction(context, moduleCode, recordId, request, traceId, BusinessAction.Options.internal());
        if (result.value() == null) {
            throw new DomainException("ACTION_REQUIRES_RECEIPT", "转化已进入流程，请通过业务动作入口查看结果", HttpStatus.CONFLICT);
        }
        return result.value();
    }

    /** 转化是一个完整业务动作；审批和重试共用入口，目标落库不再另起新建审批。 */
    @Transactional
    public BusinessAction.Result<RuntimeRecordConversionExecution> convertAction(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionRequest request, String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        RecordAccess access = requireRecordAccess(context, moduleCode, "CONVERT", recordId, true, traceId);
        requireRecordNotPendingApproval(access.record(), moduleCode);
        requireAction(context, request.targetModuleCode(), "CREATE", traceId);
        BusinessAction.Input input = new BusinessAction.Input(context, access.sourceContext().tenantId(),
                access.published().configuration().moduleId(), moduleCode, access.published().configuration().versionId(),
                "CONVERT", recordId, request.version(), request, options, traceId);
        var result = actionControl.execute(input, RuntimeRecordConversionExecution.class,
                access.published().configuration().configuration(),
                () -> actionBindings.resolveForAction(access.sourceContext(), input.moduleId(), "CONVERT",
                        actionVariables(request, readRawValues(recordId, access.published().fields())), traceId),
                () -> requireExecutableConversion(context, moduleCode, recordId, request, traceId),
                () -> convertDirect(context, moduleCode, recordId, request, traceId));
        if (!result.replayed() || result.value() == null) return result;
        var value = result.value();
        var protectedTarget = protectActionReplay(context, value.targetModuleCode(), "DETAIL",
                new BusinessAction.Result<>(result.idempotencyRecordId(), result.outboxId(), result.idempotencyKey(),
                        result.status(), true, result.mode(), value.targetRecord(), result.flowInstanceId()), traceId);
        return new BusinessAction.Result<>(result.idempotencyRecordId(), result.outboxId(), result.idempotencyKey(),
                result.status(), true, result.mode(), new RuntimeRecordConversionExecution(value.conversionId(),
                        value.targetModuleCode(), protectedTarget.value()), result.flowInstanceId());
    }

    /** 延迟执行与立即执行使用同一预校验，失败时目标、成功标记和日志一起回滚。 */
    private ConversionPlan requireExecutableConversion(AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionRequest request, String traceId) {
        ConversionPlan plan = buildConversionPreview(context, moduleCode, recordId, request, traceId);
        if (!plan.preview().executable()) {
            recordOperationFailure(context, moduleCode, "CONVERT", recordId, "CONVERSION_NOT_EXECUTABLE",
                    String.join("；", plan.preview().issues()), traceId);
            throw new DomainException(plan.preview().alreadyConverted()
                    ? "RECORD_ALREADY_CONVERTED" : "CONVERSION_MAPPING_INVALID",
                    String.join("；", plan.preview().issues()), HttpStatus.CONFLICT);
        }
        return plan;
    }

    /** 唯一物理转化处理：同一事务创建目标、永久成功标记、转化关系及操作日志。 */
    private RuntimeRecordConversionExecution convertDirect(AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionRequest request, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "CONVERT", recordId, true, traceId);
        // 锁定已经授权的来源归属，不用操作者所在租户代替来源租户，也不接受请求指定归属。
        lockConversionSource(context, moduleCode, recordId, request.version(), traceId, access.sourceContext().tenantId());
        ConversionPlan plan = requireExecutableConversion(context, moduleCode, recordId, request, traceId);
        BizRecordConversion conversion = new BizRecordConversion();
        conversion.setSystemId(context.systemId());
        conversion.setTenantId(plan.source().getTenantId());
        // 永久唯一约束按来源记录和目标模块判断，模块身份必须与实际发布版本一致。
        conversion.setSourceModuleId(access.published().configuration().moduleId());
        conversion.setSourceRecordId(recordId);
        conversion.setActionId(plan.actionId());
        conversion.setTargetModuleId(plan.target().configuration().moduleId());
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
            if (!(exception instanceof org.springframework.dao.DuplicateKeyException)) throw exception;
            recordOperationFailure(context, moduleCode, "CONVERT", recordId, "RECORD_ALREADY_CONVERTED",
                    "该记录已转化到所选目标模块", traceId);
            throw new DomainException("RECORD_ALREADY_CONVERTED", "该记录已转化到所选目标模块", HttpStatus.CONFLICT);
        }

        BusinessRecord source = plan.source();
        List<Long> participantIds = participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, recordId)
                        .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                .stream().map(BusinessRecordParticipant::getSystemMemberId).toList();
        RuntimeRecordView targetRecord = createDirect(context, request.targetModuleCode(),
                new CreateRuntimeRecordRequest(plan.preview().targetTitle(), null, "ACTIVE",
                        source.getOwnerMemberId(), source.getDepartmentId(),
                        participantIds, plan.targetValues()), traceId);

        BizRecordConversionResult result = new BizRecordConversionResult();
        result.setConversionId(conversion.getId());
        result.setTenantId(source.getTenantId());
        result.setTargetModuleId(plan.target().configuration().moduleId());
        result.setTargetRecordId(targetRecord.id());
        result.setResultType("PRIMARY");
        conversionResultService.insert(result);
        conversion.setStatus("SUCCEEDED");
        // 完成时间沿用连接的数据库业务时区，避免容器 UTC 与创建时间相差八小时。
        conversion.setFinishTime(recordWrites.currentDatabaseTime());
        if (conversionService.updateById(conversion) != 1) {
            throw new DomainException("CONVERSION_VERSION_CONFLICT", "转化关系已被其他操作修改", HttpStatus.CONFLICT);
        }

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
        return new RuntimeRecordConversionExecution(conversion.getId(), request.targetModuleCode(), targetRecord);
    }

    /** 解除关系仍经过公共动作入口，独立配置触发点，不混用会创建目标的转化绑定。 */
    @Transactional
    public BusinessAction.Result<RuntimeRecordConversionUnlink> unlinkConversionAction(
            AuthenticatedContext context, String moduleCode, Long recordId, RecordConversionUnlinkRequest request,
            String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        RecordAccess access = requireConversionUnlinkAccess(context, moduleCode, recordId, request, traceId, false);
        requireRecordNotPendingApproval(access.record(), moduleCode);
        BusinessAction.Input input = new BusinessAction.Input(context, access.sourceContext().tenantId(),
                access.published().configuration().moduleId(), moduleCode, access.published().configuration().versionId(),
                "CONVERSION_UNLINK", recordId, request.version(), request, options, traceId);
        return actionControl.execute(input, RuntimeRecordConversionUnlink.class,
                access.published().configuration().configuration(),
                () -> actionBindings.resolveForAction(access.sourceContext(), input.moduleId(), "CONVERSION_UNLINK",
                        actionVariables(request, readRawValues(recordId, access.published().fields())), traceId),
                () -> requireConversionUnlinkAccess(context, moduleCode, recordId, request, traceId, true),
                () -> unlinkConversionDirect(context, moduleCode, recordId, request, traceId));
    }

    /** 来源动作权限与目标详情权限同时约束关系操作；软删不释放转化历史。 */
    private RecordAccess requireConversionUnlinkAccess(AuthenticatedContext context, String moduleCode, Long recordId,
            RecordConversionUnlinkRequest request, String traceId, boolean checkVersion) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "CONVERT", recordId, false, traceId);
        BizRecordConversion conversion = conversionService.selectById(request.conversionId());
        if (!context.tenantId().equals(access.record().getTenantId()) || conversion == null
                || !context.systemId().equals(conversion.getSystemId()) || !context.tenantId().equals(conversion.getTenantId())
                || !recordId.equals(conversion.getSourceRecordId())
                || !Set.of("SUCCEEDED", "UNLINKED").contains(conversion.getStatus())) {
            throw new DomainException("CONVERSION_NOT_FOUND", "当前数据下不存在该转化关系", HttpStatus.NOT_FOUND);
        }
        List<BizRecordConversionResult> results = conversionResultService.selectList(
                Wrappers.<BizRecordConversionResult>lambdaQuery().eq(BizRecordConversionResult::getConversionId, conversion.getId()));
        for (BizRecordConversionResult result : results) {
            ConfiguredModule target = moduleService.selectById(result.getTargetModuleId());
            if (target == null || !context.systemId().equals(target.getSystemId())) {
                throw new DomainException("CONVERSION_NOT_FOUND", "当前数据下不存在该转化关系", HttpStatus.NOT_FOUND);
            }
            requireRecordAccess(context, target.getCode(), "DETAIL", result.getTargetRecordId(), false, traceId);
        }
        if (checkVersion) {
            requireRecordVersion(context, moduleCode, "CONVERT", access.record(), request.version(), traceId);
            if (!request.conversionVersion().equals(conversion.getVersion())) {
                throw new DomainException("CONVERSION_VERSION_CONFLICT", "转化关系已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
            }
            if ("UNLINKED".equals(conversion.getStatus())) {
                throw new DomainException("CONVERSION_ALREADY_UNLINKED", "该转化关系已解除", HttpStatus.CONFLICT);
            }
        }
        return access;
    }

    /** 原子地标记关系失效并保存原因；永久唯一标记、目标数据和映射快照保留。 */
    private RuntimeRecordConversionUnlink unlinkConversionDirect(AuthenticatedContext context, String moduleCode,
            Long recordId, RecordConversionUnlinkRequest request, String traceId) {
        lockConversionSource(context, moduleCode, recordId, request.version(), traceId, context.tenantId());
        requireConversionUnlinkAccess(context, moduleCode, recordId, request, traceId, true);
        // 上面的权限、归属、状态和版本校验完成后，使用 Base 的乐观锁更新关系状态。
        BizRecordConversion conversion = conversionService.selectById(request.conversionId());
        conversion.setStatus("UNLINKED");
        int updated = conversionService.updateById(conversion);
        if (updated != 1) throw new DomainException("CONVERSION_VERSION_CONFLICT", "转化关系已被其他操作修改", HttpStatus.CONFLICT);
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_CONVERSION_UNLINKED", "BUSINESS_RECORD", recordId.toString(), "SUCCESS",
                permissionSnapshot(context, moduleCode, "CONVERT", "PAGE"),
                Map.of("conversionId", request.conversionId(), "moduleCode", moduleCode, "reason", request.reason().strip(),
                        "fromRelationStatus", "ACTIVE", "toRelationStatus", "INVALID"));
        return new RuntimeRecordConversionUnlink(request.conversionId(), request.conversionVersion() + 1, "INVALID");
    }

    /** 锁定当前来源版本，防止转化或审批恢复与编辑交错后使用旧数据成功提交。 */
    private void lockConversionSource(AuthenticatedContext context, String moduleCode, Long recordId,
            Integer expectedVersion, String traceId, Long dataTenantId) {
        List<Integer> versions = recordWrites.lockSourceVersions(recordId, context.systemId(), dataTenantId);
        if (versions.isEmpty()) throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        if (!expectedVersion.equals(versions.get(0))) {
            recordVersionConflict(context, moduleCode, recordId, expectedVersion, versions.get(0), traceId);
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据已被其他操作修改", HttpStatus.CONFLICT);
        }
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
                .in(BizRecordConversion::getStatus, "SUCCEEDED", "UNLINKED"));
        boolean canUnlink = false;
        if (context.tenantId().equals(access.record().getTenantId())
                && permissionChecker.allows(context, "MODULE", moduleCode, "CONVERT")) {
            try {
                requireRecordAccess(context, moduleCode, "CONVERT", recordId, false, traceId);
                canUnlink = true;
            } catch (DomainException ignored) {
                // 有详情权限不等于有转化操作的数据范围权限。
            }
        }
        appendOutgoingConversionLinks(context, outgoing, links, traceId, canUnlink);

        List<BizRecordConversionResult> incomingResults = conversionResultService.selectList(
                Wrappers.<BizRecordConversionResult>lambdaQuery()
                        .eq(BizRecordConversionResult::getTargetRecordId, recordId));
        if (!incomingResults.isEmpty()) {
            Map<Long, BizRecordConversion> incoming = conversionService.selectList(
                            Wrappers.<BizRecordConversion>lambdaQuery()
                                    .in(BizRecordConversion::getId,
                                            incomingResults.stream().map(BizRecordConversionResult::getConversionId).toList())
                                    .eq(BizRecordConversion::getSystemId, context.systemId())
                                    .in(BizRecordConversion::getStatus, "SUCCEEDED", "UNLINKED"))
                    .stream().collect(java.util.stream.Collectors.toMap(BizRecordConversion::getId, item -> item));
            for (BizRecordConversion conversion : incoming.values()) {
                BusinessRecord source = recordService.selectById(conversion.getSourceRecordId());
                if (source == null) continue;
                ConfiguredModule module = moduleService.selectById(source.getModuleId());
                if (module != null && context.systemId().equals(module.getSystemId())) {
                    try {
                        requireRecordAccess(context, module.getCode(), "DETAIL", source.getId(), false, traceId);
                        links.add(new RuntimeRecordConversionLink(conversion.getId(), "SOURCE", module.getCode(),
                                module.getName(), source.getId(), source.getTitle(), source.getDeleted() ? "DELETED"
                                        : source.getArchived() ? "ARCHIVED" : source.getStatus(), conversion.getVersion(),
                                "UNLINKED".equals(conversion.getStatus()) ? "INVALID" : "ACTIVE", false, conversion.getFinishTime()));
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
        validateActionRequest(request);
        RecordAccess access = requireRecordAccess(context, moduleCode, "CONVERT", recordId, true, traceId);
        // 转化只接受本租户来源，目标也在同一租户创建并校验创建权限。
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
            issues.add("转化动作尚未配置字段对应关系");
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
                issues.add(targetField.name() + "：配置的来源字段不存在，请重新选择");
            } else if ("SECRET".equals(sourceFields.get(sourceCode).type()) && !"SECRET".equals(targetField.type())) {
                status = "DENIED";
                message = "密码/密钥只能转化到同类受保护字段";
                issues.add(targetField.name() + "：不能将密码/密钥转化到普通字段");
            } else if (sourceAccess.get(sourceCode) == null || !sourceAccess.get(sourceCode).readable()
                    || sourceAccess.get(sourceCode).maskStrategy() != null) {
                status = "DENIED";
                message = "来源字段无读取权限";
                issues.add(targetField.name() + "：来源字段无读取权限");
            } else if (targetAccess.get(targetField.code()) == null || !targetAccess.get(targetField.code()).writable()) {
                status = "DENIED";
                message = "目标字段无写入权限";
                issues.add(targetField.name() + "：目标字段无写入权限");
            } else {
                value = sourceValues.get(sourceCode);
                // JSON 的明确空值是一项合法业务值；不能把已保存的 null 当作缺少来源而丢失映射。
                boolean explicitJsonNull = "JSON".equals(targetField.type())
                        && "NULL".equals(targetField.config().path("jsonSchema").path("type").asText())
                        && value != null && value.isNull();
                if (empty(value) && !explicitJsonNull) {
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
                    targetField.required(), sourceCode, sourceFields.containsKey(sourceCode)
                            ? sourceFields.get(sourceCode).name() : null,
                    value != null && ("SECRET".equals(targetField.type())
                            || sourceFields.containsKey(sourceCode) && "SECRET".equals(sourceFields.get(sourceCode).type()))
                            ? TextNode.valueOf("***") : value, status, message));
        }
        if (issues.isEmpty()) {
            try {
                ruleIndexService.validateCreatePreview(context, target.configuration(), targetValues);
            } catch (DomainException exception) {
                // 底层唯一索引以字段编号报错，转化页面须显示当前发布的业务名称。
                String message = exception.getMessage();
                for (PublishedField field : target.fields()) message = message.replace(field.code(), field.name());
                issues.add(message);
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
        throw new DomainException("MODULE_ACTION_NOT_PUBLISHED", "当前发布版本未包含该动作", HttpStatus.CONFLICT);
    }

    /** 评论写入始终读取模块当前发布版本；旧记录不能沿用创建时已经失效的功能开关。 */
    public boolean commentsEnabled(AuthenticatedContext context, String moduleCode) {
        return moduleFunctionEnabled(published(context, moduleCode), "commentEnabled");
    }

    /** 旧发布版本没有开关时沿用原有可用行为，停用只影响后续写入。 */
    private boolean moduleFunctionEnabled(PublishedModule published, String key) {
        JsonNode detail = findPublishedAction(published, "DETAIL");
        JsonNode value = parseConfig(detail.path("configJson").asText("{}")).path(key);
        return !value.isBoolean() || value.booleanValue();
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
        // 与数据库永久唯一键一致：关系失效或目标软删不能释放已成功占用的来源、目标模块组合。
        return !conversionService.selectList(Wrappers.<BizRecordConversion>lambdaQuery()
                .eq(BizRecordConversion::getSystemId, context.systemId())
                .eq(BizRecordConversion::getTenantId, context.tenantId())
                .eq(BizRecordConversion::getSourceRecordId, sourceRecordId)
                .eq(BizRecordConversion::getIdempotencyKey, "convert:" + sourceRecordId + ":" + targetModuleId)).isEmpty();
    }

    private void appendOutgoingConversionLinks(AuthenticatedContext context, List<BizRecordConversion> conversions,
            List<RuntimeRecordConversionLink> links, String traceId, boolean sourceCanUnlink) {
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
                try {
                    requireRecordAccess(context, module.getCode(), "DETAIL", target.getId(), false, traceId);
                    links.add(new RuntimeRecordConversionLink(conversion.getId(), "TARGET", module.getCode(),
                            module.getName(), target.getId(), target.getTitle(), target.getDeleted() ? "DELETED"
                                    : target.getArchived() ? "ARCHIVED" : target.getStatus(), conversion.getVersion(),
                            "UNLINKED".equals(conversion.getStatus()) ? "INVALID" : "ACTIVE",
                            "SUCCEEDED".equals(conversion.getStatus()) && sourceCanUnlink,
                            conversion.getFinishTime()));
                } catch (DomainException ignored) {
                    // 无目标详情权限时，转化关系也不能泄漏目标标题或状态。
                }
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
        return participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
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
        long outgoing = relationService.selectCount(Wrappers.<BizRecordRelation>lambdaQuery()
                .eq(BizRecordRelation::getSystemId, context.systemId())
                .eq(BizRecordRelation::getTenantId, record.getTenantId())
                .eq(BizRecordRelation::getSourceRecordId, recordId));
        long incoming = activeIncomingRelations(record).size();
        long participants = participantService.selectCount(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                .eq(BusinessRecordParticipant::getSystemId, context.systemId())
                .eq(BusinessRecordParticipant::getTenantId, record.getTenantId())
                .eq(BusinessRecordParticipant::getRecordId, recordId));
        String warning = switch (action) {
            case "ARCHIVE" -> "归档后记录从默认列表移除，可从归档列表恢复。";
            case "DELETE" -> incoming > 0
                    ? "该记录仍被业务数据引用；请选择同模块的替代记录，引用转移成功后才能删除。"
                    : "删除后记录进入回收站，正文和历史保留，可在校验通过后恢复。";
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
        requireRecordNotPendingApproval(access.record(), moduleCode);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();
        requireLifecycleVersion(context, moduleCode, "ARCHIVE", record, request.version(), traceId);
        String fromState = lifecycleState(record);
        record.setArchived(true);
        record.setArchiveTime(LocalDateTime.now());
        record.setUpdatedByMemberId(context.memberId());
        persistLifecycleRecord(context, moduleCode, "ARCHIVE", record, request.reason(), fromState,
                "ARCHIVED", "BUSINESS_RECORD_ARCHIVED", published, traceId,
                access.share() == null ? null : access.share().getId());
        ruleIndexService.clearIndexes(recordId);
        if (access.share() != null) recordShareUsage(context, access.share(), "ARCHIVE", "SUCCESS");
        return view(context, moduleCode, "ARCHIVE", List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    @Transactional
    public RuntimeRecordView delete(AuthenticatedContext context, String moduleCode, Long recordId,
            RecordLifecycleRequest request, String traceId) {
        return requireSavedRecord(deleteAction(context, moduleCode, recordId, request, traceId, BusinessAction.Options.internal()));
    }

    @Transactional
    public BusinessAction.Result<RuntimeRecordView> deleteAction(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordLifecycleRequest request, String traceId, BusinessAction.Options options) {
        validateActionRequest(request);
        RecordAccess access = requireRecordAccess(context, moduleCode, "DELETE", recordId, false, traceId);
        requireRecordNotPendingApproval(access.record(), moduleCode);
        PublishedModule published = access.published();
        AuthenticatedContext dataContext = access.sourceContext();
        BusinessAction.Input input = new BusinessAction.Input(context, dataContext.tenantId(),
                published.configuration().moduleId(), moduleCode, published.configuration().versionId(),
                "DELETE", recordId, request.version(), request, options, traceId);
        BusinessAction.Result<RuntimeRecordView> result = actionControl.execute(input, RuntimeRecordView.class, published.configuration().configuration(),
                () -> actionBindings.resolveForAction(dataContext, input.moduleId(), input.actionCode(),
                        // 删除条件读取当前业务字段，不能只提供原因和版本而导致字段条件永远不匹配。
                        actionVariables(request, readRawValues(recordId, published.fields())), traceId),
                () -> {
                    if (Boolean.TRUE.equals(access.record().getDeleted())) {
                        throw new DomainException("RECORD_ALREADY_DELETED", "业务数据已在回收站", HttpStatus.CONFLICT);
                    }
                    requireLifecycleVersion(context, moduleCode, "DELETE", access.record(), request.version(), traceId);
                    if (!activeIncomingRelations(access.record()).isEmpty() && request.replacementRecordId() == null) {
                        throw new DomainException("RECORD_IN_USE", "该记录仍被业务数据引用，请选择替代记录后再删除", HttpStatus.CONFLICT);
                    }
                },
                () -> deleteDirect(context, moduleCode, recordId, request, traceId));
        return protectActionReplay(context, moduleCode, "DELETE", result, traceId);
    }

    private RuntimeRecordView deleteDirect(
            AuthenticatedContext context, String moduleCode, Long recordId,
            RecordLifecycleRequest request, String traceId) {
        RecordAccess access = requireRecordAccess(context, moduleCode, "DELETE", recordId, false, traceId);
        PublishedModule published = access.published();
        BusinessRecord record = access.record();
        if (Boolean.TRUE.equals(record.getDeleted())) {
            throw new DomainException("RECORD_ALREADY_DELETED", "业务数据已在回收站", HttpStatus.CONFLICT);
        }
        requireLifecycleVersion(context, moduleCode, "DELETE", record, request.version(), traceId);
        // 与来源保存读取目标时的共享锁配合，先锁目标再按当前已提交关系决定是否允许删除。
        record = recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                        .eq(BusinessRecord::getSystemId, record.getSystemId())
                        .eq(BusinessRecord::getTenantId, record.getTenantId())
                        .eq(BusinessRecord::getId, recordId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> new DomainException("RECORD_NOT_FOUND",
                        "业务数据不存在", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(record.getDeleted())) throw new DomainException("RECORD_ALREADY_DELETED",
                "业务数据已在回收站", HttpStatus.CONFLICT);
        requireLifecycleVersion(context, moduleCode, "DELETE", record, request.version(), traceId);
        transferIncomingRelations(context, moduleCode, record, request.replacementRecordId(), traceId);
        // 自引用来源也会更新本记录版本，删除时使用转移后的最新行。
        record = recordService.selectById(recordId);
        String fromState = lifecycleState(record);
        record.setDeleted(true);
        record.setDeleteTime(LocalDateTime.now());
        record.setUpdatedByMemberId(context.memberId());
        persistLifecycleRecord(context, moduleCode, "DELETE", record, request.reason(), fromState,
                "DELETED", "BUSINESS_RECORD_DELETED", published, traceId,
                access.share() == null ? null : access.share().getId());
        ruleIndexService.clearIndexes(recordId);
        if (access.share() != null) recordShareUsage(context, access.share(), "DELETE", "SUCCESS");
        localEvents.publish(context, record.getTenantId(), LocalBusinessEvent.Type.RECORD_DELETED,
                "BUSINESS_RECORD", recordId.toString(), moduleCode);
        return view(context, moduleCode, "DELETE", List.of("PAGE"), new RecordAccess(
                recordService.selectById(recordId), published, access.sourceContext(), access.share()));
    }

    /** 只处理仍可恢复或正在使用的来源记录；回收站中的旧引用不阻止目标删除。 */
    private List<BizRecordRelation> activeIncomingRelations(BusinessRecord target) {
        boolean currentRead = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        List<BizRecordRelation> relations = relationService.selectList(Wrappers.<BizRecordRelation>lambdaQuery()
                .eq(BizRecordRelation::getSystemId, target.getSystemId())
                .eq(BizRecordRelation::getTenantId, target.getTenantId())
                .eq(BizRecordRelation::getTargetModuleId, target.getModuleId())
                .eq(BizRecordRelation::getTargetRecordId, target.getId())
                .ne(BizRecordRelation::getSourceRecordId, target.getId()).last(currentRead, "FOR SHARE"));
        if (relations.isEmpty()) return List.of();
        Set<Long> sourceIds = new HashSet<>();
        relations.forEach(relation -> sourceIds.add(relation.getSourceRecordId()));
        Set<Long> liveIds = new HashSet<>();
        recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                        .eq(BusinessRecord::getSystemId, target.getSystemId())
                        .eq(BusinessRecord::getTenantId, target.getTenantId())
                        .eq(BusinessRecord::getDeleted, false)
                        .in(BusinessRecord::getId, sourceIds).last(currentRead, "FOR SHARE"))
                .forEach(source -> liveIds.add(source.getId()));
        return relations.stream().filter(relation -> liveIds.contains(relation.getSourceRecordId())).toList();
    }

    /** 删除目标前经来源模块的普通更新入口转移字段值与关系行；任一来源失败则整笔回滚。 */
    private void transferIncomingRelations(AuthenticatedContext actor, String targetModuleCode,
                                           BusinessRecord target, Long replacementId, String traceId) {
        List<BizRecordRelation> incoming = activeIncomingRelations(target);
        if (incoming.isEmpty()) {
            if (replacementId != null) throw new DomainException("REPLACEMENT_NOT_NEEDED",
                    "该记录没有需要转移的引用", HttpStatus.UNPROCESSABLE_ENTITY);
            return;
        }
        if (replacementId == null) throw new DomainException("RECORD_IN_USE",
                "该记录仍被业务数据引用，请选择替代记录后再删除", HttpStatus.CONFLICT);
        if (replacementId.equals(target.getId())) throw new DomainException("REPLACEMENT_INVALID",
                "替代记录不能是待删除记录", HttpStatus.UNPROCESSABLE_ENTITY);
        BusinessRecord replacement = requireBoundaryRecord(actor, target.getModuleId(), replacementId);
        if (Boolean.TRUE.equals(replacement.getArchived())) throw new DomainException("REPLACEMENT_INVALID",
                "替代记录必须是正常状态", HttpStatus.UNPROCESSABLE_ENTITY);
        requireRecordAccess(actor, targetModuleCode, "DETAIL", replacementId, true, traceId);

        Map<Long, List<BizRecordRelation>> bySource = new LinkedHashMap<>();
        incoming.forEach(relation -> bySource.computeIfAbsent(relation.getSourceRecordId(), ignored -> new ArrayList<>())
                .add(relation));
        for (Map.Entry<Long, List<BizRecordRelation>> entry : bySource.entrySet()) {
            Long sourceId = entry.getKey();
            BusinessRecord source = recordService.selectById(sourceId);
            ConfiguredModule sourceModule = moduleService.selectById(source.getModuleId());
            if (sourceModule == null || !"ACTIVE".equals(sourceModule.getStatus())) throw new DomainException(
                    "REFERENCE_SOURCE_UNAVAILABLE", "引用来源模块不可更新，请先处理来源配置", HttpStatus.CONFLICT);
            String sourceCode = sourceModule.getCode();
            RecordAccess sourceAccess = requireRecordAccess(actor, sourceCode, "UPDATE", sourceId, false, traceId);
            if (Boolean.TRUE.equals(source.getArchived())) throw new DomainException("REFERENCE_SOURCE_ARCHIVED",
                    "被引用的来源记录已归档，请先恢复来源记录后再转移引用", HttpStatus.CONFLICT);
            Map<String, JsonNode> raw = readRawValues(sourceId, sourceAccess.published().fields());
            Map<String, JsonNode> changed = new LinkedHashMap<>();
            Set<Long> processedFields = new HashSet<>();
            for (BizRecordRelation relation : entry.getValue()) {
                if (!processedFields.add(relation.getFieldId())) continue;
                PublishedField field = sourceAccess.published().fields().stream()
                        .filter(candidate -> candidate.id().equals(relation.getFieldId()))
                        .findFirst().orElseThrow(() -> new DomainException("REFERENCE_FIELD_UNAVAILABLE",
                                "引用来源字段未发布，请先处理来源配置", HttpStatus.CONFLICT));
                if (!"REFERENCE".equals(field.type()) || !target.getModuleId().equals(field.referenceModuleId())) {
                    throw new DomainException("REFERENCE_FIELD_UNAVAILABLE", "引用来源字段配置已变更", HttpStatus.CONFLICT);
                }
                if (field.parentFieldId() == null) {
                    JsonNode value = raw.get(field.code());
                    if (value == null) throw new DomainException("REFERENCE_VALUE_CONFLICT",
                            "引用来源字段值与关联关系不一致", HttpStatus.CONFLICT);
                    changed.put(field.code(), replaceReferenceValue(value, target.getId(), replacementId));
                } else {
                    PublishedField parent = sourceAccess.published().fields().stream()
                            .filter(candidate -> candidate.id().equals(field.parentFieldId()))
                            .findFirst().orElseThrow(() -> new DomainException("REFERENCE_FIELD_UNAVAILABLE",
                                    "引用来源子表未发布", HttpStatus.CONFLICT));
                    JsonNode rows = changed.getOrDefault(parent.code(), raw.get(parent.code()));
                    if (rows == null || !rows.isArray()) throw new DomainException("REFERENCE_VALUE_CONFLICT",
                            "引用来源子表与关联关系不一致", HttpStatus.CONFLICT);
                    ArrayNode revised = (ArrayNode) rows.deepCopy();
                    boolean found = false;
                    for (JsonNode row : revised) {
                        JsonNode value = row.path("fields").get(field.code());
                        if (value == null || !containsReference(value, target.getId())) continue;
                        ((ObjectNode) row.path("fields")).set(field.code(),
                                replaceReferenceValue(value, target.getId(), replacementId));
                        found = true;
                    }
                    if (!found) throw new DomainException("REFERENCE_VALUE_CONFLICT",
                            "引用来源子表与关联关系不一致", HttpStatus.CONFLICT);
                    changed.put(parent.code(), revised);
                }
            }
            List<Long> participants = participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                            .eq(BusinessRecordParticipant::getRecordId, sourceId))
                    .stream().map(BusinessRecordParticipant::getSystemMemberId).toList();
            UpdateRuntimeRecordRequest update = new UpdateRuntimeRecordRequest(source.getTitle(),
                    source.getRecordNumber(), source.getStatus(), source.getOwnerMemberId(), source.getDepartmentId(),
                    participants, changed, source.getVersion());
            BusinessAction.Result<RuntimeRecordView> updated = updateAction(actor, sourceCode, sourceId,
                    update, traceId, BusinessAction.Options.internal());
            if (updated.value() == null) throw new DomainException("REFERENCE_SOURCE_APPROVAL_REQUIRED",
                    "引用来源更新需要单独审批，当前删除已取消", HttpStatus.CONFLICT);
        }
        if (!activeIncomingRelations(target).isEmpty()) throw new DomainException("REFERENCE_TRANSFER_INCOMPLETE",
                "引用转移未完成，删除已取消", HttpStatus.CONFLICT);
    }

    /** 保留多选引用的原顺序，替换后去重，避免同一目标重复出现在一个字段中。 */
    private JsonNode replaceReferenceValue(JsonNode value, Long oldId, Long newId) {
        if (!value.isArray()) {
            if (value.canConvertToLong() && value.longValue() == oldId) return LongNode.valueOf(newId);
            throw new DomainException("REFERENCE_VALUE_CONFLICT", "引用来源字段值与关联关系不一致", HttpStatus.CONFLICT);
        }
        ArrayNode replaced = objectMapper.createArrayNode();
        Set<Long> seen = new HashSet<>();
        boolean found = false;
        for (JsonNode selected : value) {
            long id = selected.longValue();
            if (id == oldId) found = true;
            long next = id == oldId ? newId : id;
            if (seen.add(next)) replaced.add(next);
        }
        if (!found) throw new DomainException("REFERENCE_VALUE_CONFLICT",
                "引用来源字段值与关联关系不一致", HttpStatus.CONFLICT);
        return replaced;
    }

    private boolean containsReference(JsonNode value, Long recordId) {
        if (value.isArray()) {
            for (JsonNode item : value) if (item.canConvertToLong() && item.longValue() == recordId) return true;
            return false;
        }
        return value.canConvertToLong() && value.longValue() == recordId;
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
        record.setArchiveTime(null);
        record.setDeleted(false);
        record.setDeleteTime(null);
        record.setUpdatedByMemberId(context.memberId());
        try {
            ruleIndexService.syncIndexes(context, published.configuration(), recordId, rawValues);
        } catch (DomainException exception) {
            recordLifecycleFailure(context, moduleCode, "RESTORE", recordId, exception.code(),
                    exception.getMessage(), traceId);
            throw exception;
        }
        persistLifecycleRecord(context, moduleCode, "RESTORE", record, request.reason(), fromState,
                "ACTIVE", "BUSINESS_RECORD_RESTORED", published, traceId, null);
        // Base 按默认策略忽略 null；恢复时必须显式清空旧归档和删除时间，避免正常记录残留历史状态时间。
        if (recordService.update(Wrappers.<BusinessRecord>lambdaUpdate()
                .eq(BusinessRecord::getId, recordId)
                .eq(BusinessRecord::getSystemId, record.getSystemId())
                .eq(BusinessRecord::getTenantId, record.getTenantId())
                .set(BusinessRecord::getArchiveTime, null)
                .set(BusinessRecord::getDeleteTime, null)) == 0) {
            throw new DomainException("RECORD_VERSION_CONFLICT", "业务数据恢复时状态发生变化", HttpStatus.CONFLICT);
        }
        return view(context, moduleCode, "RESTORE", List.of("PAGE"), recordService.selectById(recordId), published);
    }

    private void validateRestore(
            AuthenticatedContext context, Long recordId, PublishedModule published, Map<String, JsonNode> values) {
        List<String> conflicts = new ArrayList<>();
        for (PublishedField field : published.fields()) {
            if (field.parentFieldId() != null) continue;
            JsonNode value = values.get(field.code());
            if (field.required() && empty(value)) {
                conflicts.add(field.code() + ": 当前发布配置要求必填");
            }
            if ("REFERENCE".equals(field.type())) validateRestoredReference(context, recordId, field, value, conflicts);
            if ("SUBTABLE".equals(field.type()) && value != null && value.isArray()) {
                List<PublishedField> columns = published.fields().stream()
                        .filter(column -> field.id().equals(column.parentFieldId())).toList();
                for (JsonNode row : value) {
                    for (PublishedField column : columns) {
                        JsonNode childValue = row.path("fields").get(column.code());
                        if (column.required() && empty(childValue)) {
                            conflicts.add(field.code() + "." + column.code() + ": 当前发布配置要求必填");
                        }
                        if ("REFERENCE".equals(column.type())) {
                            validateRestoredReference(context, recordId, column, childValue, conflicts);
                        }
                    }
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

    /** 生命周期审计在共享操作时保存实际命中的授权，便于从单条操作追溯授权来源。 */
    private void persistLifecycleRecord(
            AuthenticatedContext context, String moduleCode, String action, BusinessRecord record,
            String reason, String fromState, String toState, String eventCode,
            PublishedModule published, String traceId, Long shareId) {
        Map<String, JsonNode> snapshotFields = readRawValues(record.getId(), published.fields());
        record.setUpdateTime(null);
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
        if (shareId != null) detail.put("shareId", shareId);
        detail.put("snapshot", lifecycleSnapshot(record, redactSecrets(snapshotFields, published.fields())));
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
                    field.path("parentFieldId").isNumber() ? field.path("parentFieldId").longValue() : null,
                    parseConfig(field.path("configJson").asText("{}"))));
        }
        fields.sort(Comparator.comparing(PublishedField::code));
        return new PublishedModule(configuration, fields);
    }

    /** 组织管理员替换部门字段后重建该记录的查询索引；唯一索引冲突会回滚整个部门转移事务。 */
    public void reindexAfterDepartmentReplacement(AuthenticatedContext context, BusinessRecord record) {
        if (!context.systemId().equals(record.getSystemId()) || !context.tenantId().equals(record.getTenantId())) {
            throw new DomainException("DEPARTMENT_TRANSFER_SCOPE_INVALID", "业务记录不属于当前系统工作空间", HttpStatus.FORBIDDEN);
        }
        if (Boolean.TRUE.equals(record.getDeleted()) || Boolean.TRUE.equals(record.getArchived())) {
            ruleIndexService.clearIndexes(record.getId());
            return;
        }
        ConfiguredModule module = moduleService.selectById(record.getModuleId());
        if (module == null || !context.systemId().equals(module.getSystemId())
                || !context.tenantId().equals(module.getOwnerTenantId())) {
            throw new DomainException("DEPARTMENT_TRANSFER_MODULE_INVALID", "业务记录所属模块已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (!"ACTIVE".equals(module.getStatus())) {
            ruleIndexService.clearIndexes(record.getId());
            return;
        }
        PublishedModule current = published(context, module.getCode());
        ruleIndexService.syncIndexes(context, current.configuration(), record.getId(),
                readRawValues(record.getId(), current.fields()));
    }

    private JsonNode parseConfig(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Published field configuration is invalid", exception);
        }
    }

    private void requireAction(AuthenticatedContext context, String moduleCode, String actionCode, String traceId) {
        SystemTenant tenant = context.tenantId() == null ? null : tenantService.selectById(context.tenantId());
        if (tenant == null || !Objects.equals(tenant.getSystemId(), context.systemId())
                || !"ACTIVE".equals(tenant.getStatus())
                || !multiTenantEnabled(context.systemId()) && !Boolean.TRUE.equals(tenant.getMain())) {
            throw new DomainException("TENANT_CONTEXT_UNAVAILABLE", "当前租户不可访问，请重新进入系统", HttpStatus.FORBIDDEN);
        }
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
        // 写事务内的预校验、物理执行和审批恢复都重读当前授权及范围值；只读页面不持有写事务锁。
        boolean currentRead = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return requireRecordAccess(context, moduleCode, actionCode, recordId, activeOnly, traceId, currentRead);
    }

    private RecordAccess requireRecordAccess(
            AuthenticatedContext context, String moduleCode, String actionCode,
            Long recordId, boolean activeOnly, String traceId, boolean currentRead) {
        requireAction(context, moduleCode, actionCode, traceId);
        // 先确认真实记录归属；来源私有模块不要求接收方另建同编码配置。
        BusinessRecord sharedRecord = recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                        .eq(BusinessRecord::getSystemId, context.systemId())
                        .eq(BusinessRecord::getId, recordId).last(currentRead, "FOR SHARE"))
                .stream().findFirst().orElse(null);
        if (sharedRecord == null || (activeOnly && (Boolean.TRUE.equals(sharedRecord.getArchived())
                || Boolean.TRUE.equals(sharedRecord.getDeleted())))) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        }
        ConfiguredModule sourceModule = moduleService.selectById(sharedRecord.getModuleId());
        if (sourceModule == null || !context.systemId().equals(sourceModule.getSystemId())
                || !"ACTIVE".equals(sourceModule.getStatus())
                || !moduleCode.equalsIgnoreCase(sourceModule.getCode())) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        }
        if (context.tenantId().equals(sharedRecord.getTenantId())) {
            PublishedModule ownPublished = published(context, moduleCode);
            if (!sourceModule.getId().equals(ownPublished.configuration().moduleId())) {
                throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
            }
            if (!withinScope(context, moduleCode, actionCode, recordId, sourceModule.getId(), currentRead)) {
                deny(context, moduleCode, actionCode, traceId,
                        Map.of("recordId", recordId, "reason", "DATA_SCOPE_DENIED"));
            }
            requireTeamWriteRole(context, moduleCode, actionCode, sharedRecord, traceId);
            return new RecordAccess(sharedRecord, ownPublished, context, null);
        }
        BizTenantShare share = matchingShare(context, moduleCode, sharedRecord, actionCode, currentRead);
        if (share == null || !sharedSourceMetadata.activeTenantPair(context, sharedRecord.getTenantId())) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在或共享授权已失效", HttpStatus.NOT_FOUND);
        }
        // 本租户角色范围与有效共享授权是并集；前面的 requireAction 已校验目标成员动作权限。
        AuthenticatedContext sourceContext = tenantContext(context, sharedRecord.getTenantId());
        PublishedModule sourcePublished = published(sourceContext, moduleCode);
        if (!sourceModule.getId().equals(sourcePublished.configuration().moduleId())) {
            throw new DomainException("SHARE_CONFIGURATION_CONFLICT", "共享记录与来源发布模块不一致", HttpStatus.CONFLICT);
        }
        return new RecordAccess(sharedRecord, sourcePublished, sourceContext, share);
    }

    /** 只读和关注关系只增加可见性，不能借模块通用写授权修改这条记录。 */
    private void requireTeamWriteRole(AuthenticatedContext context, String moduleCode, String actionCode,
                                      BusinessRecord record, String traceId) {
        if (Set.of("LIST", "DETAIL", "PRINT").contains(actionCode)
                || Objects.equals(record.getOwnerMemberId(), context.memberId())) return;
        if (teamWriteRestricted(context, record)) deny(context, moduleCode, actionCode, traceId,
                Map.of("recordId", record.getId(), "reason", "TEAM_ROLE_READ_ONLY"));
    }

    private boolean teamWriteRestricted(AuthenticatedContext context, BusinessRecord record) {
        if (Objects.equals(record.getOwnerMemberId(), context.memberId())) return false;
        return participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, record.getId())
                        .eq(BusinessRecordParticipant::getSystemMemberId, context.memberId()))
                .stream().anyMatch(item -> Set.of("READ_ONLY", "FOLLOWER").contains(item.getParticipantType()));
    }

    private boolean withinSharedScope(
            AuthenticatedContext context, String moduleCode, String actionCode, BusinessRecord record) {
        return withinSharedScope(context, moduleCode, actionCode, record, false);
    }

    private boolean withinSharedScope(
            AuthenticatedContext context, String moduleCode, String actionCode, BusinessRecord record, boolean currentRead) {
        LambdaQueryWrapper<BusinessRecord> query = Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, context.systemId())
                .eq(BusinessRecord::getModuleId, record.getModuleId())
                .eq(BusinessRecord::getId, record.getId());
        applyDataScope(query, context, moduleCode, actionCode);
        return !recordService.selectList(query.last(currentRead, "FOR SHARE")).isEmpty();
    }

    /** 每个动作独立匹配授权；重叠范围不得把不匹配授权的动作合并到此记录。 */
    private BizTenantShare matchingShare(AuthenticatedContext actor, String code, BusinessRecord record,
                                        String action, boolean currentRead) {
        if (!multiTenantEnabled(actor.systemId()) || "CONVERT".equals(action)) return null;
        return tenantShareService.selectList(Wrappers.<BizTenantShare>lambdaQuery()
                .eq(BizTenantShare::getSystemId, actor.systemId())
                .eq(BizTenantShare::getSourceTenantId, record.getTenantId())
                .eq(BizTenantShare::getTargetTenantId, actor.tenantId())
                .eq(BizTenantShare::getModuleId, record.getModuleId())
                .eq(BizTenantShare::getStatus, "ACTIVE").orderByAsc(BizTenantShare::getId)
                .last(currentRead, "FOR SHARE")).stream()
                .filter(share -> shareAllows(share, action) && tenantShareScope.matches(actor, share, record, code, currentRead))
                .findFirst().orElse(null);
    }

    private List<BizTenantShare> activeTargetShares(AuthenticatedContext context) {
        if (!multiTenantEnabled(context.systemId())) return List.of();
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
        Set<String> prohibited = Set.of("CREATE", "RESTORE", "SHARE", "CONVERT");
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
        return tenantShareScope.status(share);
    }

    /** 多租户开关只暂停共享生效，不修改已保存的授权和租户状态。 */
    private boolean multiTenantEnabled(Long systemId) {
        SystemDefinition system = systemId == null ? null : systemService.selectById(systemId);
        return system != null && "MULTI".equals(system.getTenantMode());
    }

    private void requireMultiTenantShare(Long systemId) {
        if (!multiTenantEnabled(systemId)) {
            throw new DomainException("MULTI_TENANT_NOT_ENABLED", "请先启用多租户模式", HttpStatus.CONFLICT);
        }
    }

    private RuntimeTenantShareView shareView(String moduleCode, BizTenantShare share) {
        return new RuntimeTenantShareView(share.getId(), share.getSourceTenantId(), tenantName(share.getSourceTenantId()),
                share.getTargetTenantId(), tenantName(share.getTargetTenantId()), share.getRecordId(), moduleCode,
                shareActions(share), effectiveShareStatus(share), share.getGrantTime(), share.getExpireTime(),
                share.getRevokeTime(), share.getGrantedByMemberId(), share.getVersion());
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
        return withinScope(context, moduleCode, actionCode, recordId, moduleId, false);
    }

    private boolean withinScope(AuthenticatedContext context, String moduleCode, String actionCode,
                                Long recordId, Long moduleId, boolean currentRead) {
        LambdaQueryWrapper<BusinessRecord> query = baseBoundary(context, moduleId).eq(BusinessRecord::getId, recordId);
        applyDataScope(query, context, moduleCode, actionCode);
        return !recordService.selectList(query.last(currentRead, "FOR SHARE")).isEmpty();
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
                case "SELF_AND_SUBORDINATES" -> {
                    List<Long> memberIds = organizationRelationRepository.subordinateSystemMemberIds(
                            context.tenantId(), context.tenantMemberId());
                    if (!memberIds.isEmpty()) {
                        clauses.add(wrapper -> wrapper.in(BusinessRecord::getOwnerMemberId, memberIds));
                    }
                }
                case "DEPARTMENT" -> {
                    if (currentDepartmentId != null) {
                        List<Long> owners = ownerMemberIdsForDepartments(context, List.of(currentDepartmentId));
                        if (!owners.isEmpty()) clauses.add(wrapper -> wrapper.in(BusinessRecord::getOwnerMemberId, owners));
                    }
                }
                case "DEPARTMENT_AND_DESCENDANTS" -> {
                    if (!descendantDepartmentIds.isEmpty()) {
                        List<Long> owners = ownerMemberIdsForDepartments(context, descendantDepartmentIds);
                        if (!owners.isEmpty()) clauses.add(wrapper -> wrapper.in(BusinessRecord::getOwnerMemberId, owners));
                    }
                }
                case "SPECIFIED_DEPARTMENTS" -> {
                    List<Long> ids = longValues(term.condition(), "departmentIds");
                    if (!ids.isEmpty()) {
                        List<Long> owners = ownerMemberIdsForDepartments(context, ids);
                        if (!owners.isEmpty()) clauses.add(wrapper -> wrapper.in(BusinessRecord::getOwnerMemberId, owners));
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

    /** 冻结来源授予时的共享权限上限，沿用原运行查询的角色和组织范围定义。 */
    public JsonNode sourceShareScopeSnapshot(AuthenticatedContext context, String moduleCode) {
        requireMultiTenantShare(context.systemId());
        requireAction(context, moduleCode, "SHARE", "range-share-scope");
        List<DataScopeTerm> terms = matchingTerms(context, moduleCode, "SHARE");
        var clauses = objectMapper.createArrayNode();
        if (terms.stream().anyMatch(term -> "ALL".equals(term.type()))) {
            clauses.addObject().put("type", "ALL");return clauses;
        }
        Long department = currentDepartmentId(context);
        for (DataScopeTerm term : terms) {
            String type;List<Long> ids;
            switch (term.type()) {
                case "SELF" -> { type = "OWNER";ids = context.memberId() == null ? List.of() : List.of(context.memberId()); }
                case "SELF_AND_SUBORDINATES" -> { type = "OWNER";ids = organizationRelationRepository.subordinateSystemMemberIds(context.tenantId(), context.tenantMemberId()); }
                case "DEPARTMENT" -> { type = "DEPARTMENT";ids = department == null ? List.of() : List.of(department); }
                case "DEPARTMENT_AND_DESCENDANTS" -> { type = "DEPARTMENT";ids = descendantDepartmentIds(context, department); }
                case "SPECIFIED_DEPARTMENTS" -> { type = "DEPARTMENT";ids = longValues(term.condition(), "departmentIds"); }
                case "SPECIFIED_MEMBERS" -> { type = "OWNER";ids = longValues(term.condition(), "memberIds"); }
                default -> { continue; }
            }
            if (!ids.isEmpty()) { var clause = clauses.addObject().put("type", type);clause.set("ids", objectMapper.valueToTree(ids)); }
        }
        return clauses;
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
        return ownerDepartmentId(context.systemId(), context.tenantId(), context.memberId());
    }

    /** 部门范围使用负责人的当前租户组织归属，历史记录里的负责部门只用于展示和追溯。 */
    private Long ownerDepartmentId(Long systemId, Long tenantId, Long ownerMemberId) {
        if (systemId == null || tenantId == null || ownerMemberId == null) return null;
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, systemId)
                        .eq(SystemTenantMember::getTenantId, tenantId)
                        .eq(SystemTenantMember::getSystemMemberId, ownerMemberId)
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().findFirst().map(SystemTenantMember::getDepartmentId).orElse(null);
    }

    private List<Long> ownerMemberIdsForDepartments(AuthenticatedContext context, List<Long> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) return List.of();
        Set<Long> tenantMembers = new LinkedHashSet<>();
        tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE")
                        .in(SystemTenantMember::getDepartmentId, departmentIds))
                .forEach(member -> tenantMembers.add(member.getSystemMemberId()));
        if (tenantMembers.isEmpty()) return List.of();
        return memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())
                        .eq(SystemMember::getStatus, "ACTIVE")
                        .in(SystemMember::getId, tenantMembers))
                .stream().map(SystemMember::getId).distinct().toList();
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
        fields.stream().filter(field -> field.parentFieldId() == null)
                .forEach(field -> byCode.put(field.code(), field));
        List<String> errors = new ArrayList<>();
        submitted.keySet().stream().filter(code -> !byCode.containsKey(code)).sorted()
                .forEach(code -> errors.add(code + ": 字段未发布或不存在"));
        Map<String, FieldAccessDecision> access = fieldPolicyResolver.resolve(
                context, moduleCode, actionCode, "PAGE", fields.stream().map(PublishedField::code).toList());
        Map<String, JsonNode> normalized = new LinkedHashMap<>();
        // 命名字段动作也是对既有记录的局部更新，保留未提交字段和密钥的原值。
        Map<String, JsonNode> existing = recordId != null
                ? readRawValues(recordId, fields) : Map.of();
        for (PublishedField field : fields) {
            if (field.parentFieldId() != null) continue;
            boolean readOnly = field.type().startsWith("SYSTEM_") || "AUTO_NUMBER".equals(field.type())
                    || field.config().path("readOnly").asBoolean(false);
            if (readOnly) {
                if (submitted.containsKey(field.code())) {
                    errors.add(field.code() + ": 只读字段由系统维护，不能写入");
                }
                if (existing.containsKey(field.code())) normalized.put(field.code(), existing.get(field.code()));
                continue;
            }
            if (!access.get(field.code()).writable()) {
                if (submitted.containsKey(field.code())) errors.add(field.code() + ": 当前页面渠道没有字段写入权限");
                // 不可写字段的原值参与来源业务规则校验，但不回显给调用方。
                if (existing.containsKey(field.code())) normalized.put(field.code(), existing.get(field.code()));
                continue;
            }
            // 导入和局部动作只提交映射字段；更新时遗漏的可写字段必须保留原值，不能被批量导入静默清空。
            if (recordId != null && !submitted.containsKey(field.code())
                    && existing.containsKey(field.code())) {
                normalized.put(field.code(), existing.get(field.code()));
                continue;
            }
            JsonNode value = submitted.get(field.code());
            // 编辑及局部动作不预填密钥；遗漏键保留原值，显式null才按清空校验。
            if ("SECRET".equals(field.type()) && !submitted.containsKey(field.code()) && existing.containsKey(field.code())) {
                normalized.put(field.code(), existing.get(field.code()));
                continue;
            }
            boolean explicitJsonNull = "JSON".equals(field.type()) && "NULL".equals(field.config().path("jsonSchema").path("type").asText())
                    && value != null && value.isNull();
            // 默认值只补遗漏输入；显式清空必须保留，尤其不能覆盖审批节点已批准的 null。
            if (empty(value) && !explicitJsonNull && "CREATE".equalsIgnoreCase(actionCode) && !submitted.containsKey(field.code())) {
                value = configuredDefault(field);
            }
            if (empty(value) && !explicitJsonNull) {
                // 子表最少行数是业务约束；即使字段本身未标必填，也不能用空数组跳过。
                if ("SUBTABLE".equals(field.type()) && field.config().path("minRows").asInt(0) > 0) {
                    errors.add(field.code() + ": 子表至少需要 " + field.config().path("minRows").asInt() + " 行");
                } else if (field.required()) {
                    errors.add(field.code() + ": 必填字段不能为空");
                }
                continue;
            }
            try {
                JsonNode normalizedValue = "SUBTABLE".equals(field.type())
                        ? normalizeChildRows(context, validationContext, moduleCode, actionCode,
                                field, fields, value, traceId, recordId)
                        : normalize(validationContext, field, value, moduleCode, recordId);
                if (empty(normalizedValue) && !explicitJsonNull) {
                    if (field.required()) throw new IllegalArgumentException("必填字段不能为空");
                    continue;
                }
                validateReferenceValue(validationContext, field, normalizedValue);
                if (!Objects.equals(existing.get(field.code()), normalizedValue)) {
                    validateActorReference(context, field, normalizedValue, traceId);
                }
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

    /**
     * 导入文件允许使用成员姓名/工号、部门名称/编码、关联记录编号/标题和选项名称；
     * 解析结果必须唯一，避免让业务用户查找数据库内部 ID。
     */
    private Map<String, JsonNode> resolveImportDisplayValues(
            AuthenticatedContext context, List<PublishedField> fields, Map<String, JsonNode> submitted) {
        Map<String, PublishedField> byCode = new LinkedHashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        Map<String, JsonNode> resolved = new LinkedHashMap<>();
        submitted.forEach((code, value) -> {
            PublishedField field = byCode.get(code);
            resolved.put(code, field == null ? value : resolveImportDisplayValue(context, field, value));
        });
        return resolved;
    }

    private JsonNode resolveImportDisplayValue(
            AuthenticatedContext context, PublishedField field, JsonNode value) {
        if (value == null || value.isNull()) return value;
        if ("MEMBER".equals(field.type()) && value.isTextual() && !numeric(value.textValue())) {
            String key = value.textValue().strip();
            Set<Long> tenantMembers = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                            .eq(SystemTenantMember::getSystemId, context.systemId())
                            .eq(SystemTenantMember::getTenantId, context.tenantId())
                            .eq(SystemTenantMember::getStatus, "ACTIVE"))
                    .stream().map(SystemTenantMember::getSystemMemberId).collect(java.util.stream.Collectors.toSet());
            List<SystemMember> matches = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                            .eq(SystemMember::getSystemId, context.systemId())
                            .eq(SystemMember::getStatus, "ACTIVE")
                            .and(query -> query.eq(SystemMember::getDisplayName, key)
                                    .or().eq(SystemMember::getEmployeeNumber, key)))
                    .stream().filter(member -> tenantMembers.contains(member.getId())).toList();
            return LongNode.valueOf(uniqueImportTarget(matches.stream().map(SystemMember::getId).toList(),
                    "成员姓名或工号", key));
        }
        if ("DEPARTMENT".equals(field.type()) && value.isTextual() && !numeric(value.textValue())) {
            String key = value.textValue().strip();
            List<Long> matches = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                            .eq(SystemDepartment::getSystemId, context.systemId())
                            .eq(SystemDepartment::getTenantId, context.tenantId())
                            .eq(SystemDepartment::getStatus, "ACTIVE")
                            .and(query -> query.eq(SystemDepartment::getName, key)
                                    .or().eq(SystemDepartment::getCode, key)))
                    .stream().map(SystemDepartment::getId).toList();
            return LongNode.valueOf(uniqueImportTarget(matches, "部门名称或编码", key));
        }
        if ("REFERENCE".equals(field.type()) && value.isTextual() && !numeric(value.textValue())) {
            if (field.referenceModuleId() == null) throw new IllegalArgumentException("关联字段未配置目标模块");
            String key = value.textValue().strip();
            List<Long> matches = recordService.selectList(boundary(context, field.referenceModuleId())
                            .and(query -> query.eq(BusinessRecord::getRecordNumber, key)
                                    .or().eq(BusinessRecord::getTitle, key)))
                    .stream().map(BusinessRecord::getId).toList();
            return LongNode.valueOf(uniqueImportTarget(matches, "关联记录编号或标题", key));
        }
        if (Set.of("SINGLE_SELECT", "STATUS").contains(field.type()) && value.isTextual()) {
            return TextNode.valueOf(resolveImportOption(context, field, value.textValue()));
        }
        if (Set.of("MULTI_SELECT", "TAG").contains(field.type()) && value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(item.isTextual()
                    ? resolveImportOption(context, field, item.textValue()) : item.asText()));
            return result;
        }
        return value;
    }

    private String resolveImportOption(AuthenticatedContext context, PublishedField field, String raw) {
        String submitted = raw == null ? "" : raw.strip();
        if (field.dictionaryId() != null) {
            List<JsonNode> items = dictionaryConfigurationService.runtime(context, field.dictionaryId(), null).items();
            List<String> codes = items.stream().filter(item -> "ACTIVE".equals(item.path("status").asText()))
                    .filter(item -> submitted.equals(item.path("code").asText())
                            || submitted.equals(item.path("label").asText()))
                    .map(item -> item.path("code").asText()).distinct().toList();
            if (codes.size() == 1) return codes.getFirst();
            if (codes.isEmpty()) throw new IllegalArgumentException("选项名称或编码不存在：" + submitted);
            throw new IllegalArgumentException("选项名称不唯一，请改用编码：" + submitted);
        }
        List<String> values = new ArrayList<>();
        field.config().path("options").forEach(item -> {
            String code = item.path("value").asText(item.path("code").asText());
            if (submitted.equals(code) || submitted.equals(item.path("label").asText())) values.add(code);
        });
        List<String> unique = values.stream().filter(item -> !item.isBlank()).distinct().toList();
        if (unique.size() == 1) return unique.getFirst();
        if (unique.isEmpty()) throw new IllegalArgumentException("选项名称或编码不存在：" + submitted);
        throw new IllegalArgumentException("选项名称不唯一，请改用编码：" + submitted);
    }

    private Long uniqueImportTarget(List<Long> ids, String label, String value) {
        List<Long> unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (unique.size() == 1) return unique.getFirst();
        if (unique.isEmpty()) throw new IllegalArgumentException(label + "不存在：" + value);
        throw new IllegalArgumentException(label + "不唯一，请改用唯一编码：" + value);
    }

    private boolean numeric(String value) {
        return value != null && value.strip().matches("[1-9][0-9]*");
    }

    private JsonNode configuredDefault(PublishedField field) {
        JsonNode configured = field.config().get("defaultValue");
        if (empty(configured)) return null;
        if (!configured.isTextual()) return configured;
        String value = configured.textValue().strip();
        if (value.isEmpty()) return null;
        if ("BOOLEAN".equals(field.type())) {
            if ("true".equalsIgnoreCase(value)) return BooleanNode.TRUE;
            if ("false".equalsIgnoreCase(value)) return BooleanNode.FALSE;
        }
        if (Set.of("MEMBER", "DEPARTMENT", "REFERENCE").contains(field.type())) {
            try {
                return LongNode.valueOf(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                return configured;
            }
        }
        return configured;
    }

    /** 子表列必须属于当前已发布的容器字段，逐行复用普通字段的值校验与目标授权。 */
    private JsonNode normalizeChildRows(
            AuthenticatedContext actor, AuthenticatedContext dataContext, String moduleCode, String actionCode,
            PublishedField parent, List<PublishedField> allFields, JsonNode submitted, String traceId, Long recordId) {
        if (!submitted.isArray()) throw new IllegalArgumentException("子表必须是明细行列表");
        int minimum = Math.max(0, parent.config().path("minRows").asInt(0));
        int maximum = Math.max(minimum, parent.config().path("maxRows").asInt(100));
        if (submitted.size() < minimum || submitted.size() > maximum) {
            throw new IllegalArgumentException("子表行数必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        List<PublishedField> columns = allFields.stream()
                .filter(item -> parent.id().equals(item.parentFieldId())).toList();
        if (columns.isEmpty() && !submitted.isEmpty()) throw new IllegalArgumentException("子表尚未配置已发布的列");
        Map<String, PublishedField> byCode = new LinkedHashMap<>();
        columns.forEach(column -> byCode.put(column.code(), column));
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolve(
                actor, moduleCode, actionCode, "PAGE", new ArrayList<>(byCode.keySet()));
        Map<String, JsonNode> previousRows = new HashMap<>();
        if (recordId != null) {
            JsonNode existing = readRawValues(recordId, allFields).get(parent.code());
            if (existing != null && existing.isArray()) {
                for (JsonNode row : existing) previousRows.put(row.path("rowKey").asText(), row.path("fields"));
            }
        }
        Set<String> rowKeys = new HashSet<>();
        ArrayNode rows = objectMapper.createArrayNode();
        for (JsonNode row : submitted) {
            if (!row.isObject() || !row.path("fields").isObject()) {
                throw new IllegalArgumentException("每条子表明细必须包含字段对象");
            }
            String rowKey = row.path("rowKey").asText("").strip();
            if (rowKey.isEmpty()) rowKey = java.util.UUID.randomUUID().toString();
            if (rowKey.length() > 100 || !rowKeys.add(rowKey)) {
                throw new IllegalArgumentException("子表行标识无效或重复");
            }
            JsonNode rawFields = row.path("fields");
            for (java.util.Iterator<String> names = rawFields.fieldNames(); names.hasNext(); ) {
                if (!byCode.containsKey(names.next())) throw new IllegalArgumentException("子表含有未发布的列");
            }
            ObjectNode normalizedFields = objectMapper.createObjectNode();
            JsonNode previousFields = previousRows.get(rowKey);
            for (PublishedField column : columns) {
                JsonNode value = rawFields.get(column.code());
                if (value == null && previousFields != null && previousFields.has(column.code())) {
                    normalizedFields.set(column.code(), previousFields.get(column.code()));
                    continue;
                }
                // 显式清空也是写入；只读列和无写权限列不能借空值绕过校验。
                if (rawFields.has(column.code())) {
                    FieldAccessDecision decision = decisions.get(column.code());
                    if (column.type().startsWith("SYSTEM_") || column.config().path("readOnly").asBoolean(false)
                            || decision == null || !decision.writable()) {
                        throw new IllegalArgumentException(column.code() + ": 没有子表列写入权限");
                    }
                }
                if (value == null || empty(value)) {
                    if (column.required()) throw new IllegalArgumentException(column.code() + ": 必填列不能为空");
                    continue;
                }
                JsonNode normalized = normalize(dataContext, column, value, moduleCode, recordId);
                validateReferenceValue(dataContext, column, normalized);
                validateActorReference(actor, column, normalized, traceId);
                normalizedFields.set(column.code(), normalized);
            }
            ObjectNode normalizedRow = objectMapper.createObjectNode();
            normalizedRow.put("rowKey", rowKey);
            normalizedRow.set("fields", normalizedFields);
            rows.add(normalizedRow);
        }
        return rows;
    }

    private void validateReferenceValue(AuthenticatedContext context, PublishedField field, JsonNode value) {
        if ("MEMBER".equals(field.type())) {
            // 页面候选与写入校验共用有效成员目录，防止手工请求写入已停用账号。
            boolean valid = peopleDirectoryService.recordOptions(context, true, false).people().stream()
                    .anyMatch(person -> java.util.Objects.equals(person.systemMemberId(), value.longValue()));
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
            for (JsonNode selected : value.isArray() ? value : List.of(value)) {
                boolean valid = !recordService.selectList(boundary(context, field.referenceModuleId())
                        .eq(BusinessRecord::getId, selected.longValue())).isEmpty();
                if (!valid) throw new IllegalArgumentException("引用记录不存在或不属于当前租户");
            }
        }
    }

    /** 引用归属校验不能代替实际操作者的目标记录授权；保持原引用不扩大其读取权限。 */
    private void validateActorReference(AuthenticatedContext actor, PublishedField field, JsonNode value, String traceId) {
        if (!"REFERENCE".equals(field.type())) return;
        ConfiguredModule target = moduleService.selectById(field.referenceModuleId());
        if (target == null || !actor.systemId().equals(target.getSystemId()) || !"ACTIVE".equals(target.getStatus())) {
            throw new IllegalArgumentException("关联目标模块不可用");
        }
        try {
            for (JsonNode selected : value.isArray() ? value : List.of(value)) {
                requireRecordAccess(actor, target.getCode(), "LIST", selected.longValue(), true, traceId);
                requireRecordAccess(actor, target.getCode(), "DETAIL", selected.longValue(), true, traceId);
            }
        } catch (DomainException exception) {
            if (exception.status() != HttpStatus.FORBIDDEN && exception.status() != HttpStatus.NOT_FOUND) throw exception;
            throw new IllegalArgumentException("没有选择该关联记录的权限或共享授权已失效");
        }
    }

    private JsonNode normalize(AuthenticatedContext context, PublishedField field, JsonNode value) {
        return normalize(context, field, value, null, null);
    }

    private JsonNode normalize(
            AuthenticatedContext context, PublishedField field, JsonNode value,
            String moduleCode, Long recordId) {
        return switch (field.type()) {
            case "TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE" ->
                    TextFieldProtocol.normalize(field.type(), field.config(), value);
            case "RICH_TEXT", "JSON" -> StructuredContentFieldProtocol.normalize(field.type(), field.config(), value);
            case "SECRET" -> com.unique.unexamine.moduleconfig.manage.SecretFieldProtocol.normalize(field.config(), value);
            case "SINGLE_SELECT", "STATUS" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是选项值");
                }
                validateOption(context, field, value.textValue());
                yield TextNode.valueOf(value.textValue());
            }
            case "MULTI_SELECT", "TAG" -> {
                if (!value.isArray()) throw new IllegalArgumentException("必须是多选值列表");
                if (field.required() && value.isEmpty()) throw new IllegalArgumentException("必填字段至少选择一项");
                // 保存去重后的选项编码；每一项都校验实际来源当前发布字典，不能仅校验显示名称。
                Set<String> selected = new LinkedHashSet<>();
                for (JsonNode item : value) {
                    if (!item.isTextual()) throw new IllegalArgumentException("多选项必须是有效选项值");
                    validateOption(context, field, item.textValue());
                    selected.add(item.textValue());
                }
                ArrayNode normalized = objectMapper.createArrayNode();
                selected.forEach(normalized::add);
                yield normalized;
            }
            case "NUMBER", "MONEY", "PERCENT", "RATING", "PROGRESS" -> {
                BigDecimal number;
                if (value.isNumber()) {
                    number = value.decimalValue();
                } else if (value.isTextual()) {
                    try {
                        number = new BigDecimal(value.textValue().strip());
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("必须是数字");
                    }
                } else {
                    throw new IllegalArgumentException("必须是数字");
                }
                // 主记录和子表的数值列均为 DECIMAL(24,8)，保存前拒绝无法精确落库的输入。
                BigDecimal exact = number.stripTrailingZeros();
                if (exact.scale() > 8 || exact.precision() - exact.scale() > 16) {
                    throw new IllegalArgumentException("数字最多 16 位整数、8 位小数");
                }
                if ("MONEY".equals(field.type()) && exact.scale() > field.config().path("precision").asInt(2)) {
                    throw new IllegalArgumentException("小数位超过金额字段配置的精度");
                }
                if ("PERCENT".equals(field.type()) && (exact.signum() < 0
                        || exact.compareTo(BigDecimal.valueOf(100)) > 0)) {
                    throw new IllegalArgumentException("百分比必须在 0 到 100 之间");
                }
                if (Set.of("RATING", "PROGRESS").contains(field.type())) {
                    exact = validateScoreOrProgress(field.type(), exact);
                }
                yield DecimalNode.valueOf(exact);
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
            case "DATE_RANGE", "TIME_RANGE" -> normalizeTemporalRange(field.type(), value);
            case "TIME" -> {
                if (!value.isTextual()) throw new IllegalArgumentException("时间格式应为 HH:mm 或 HH:mm:ss");
                yield TextNode.valueOf(normalizeClockTime(value.textValue()));
            }
            case "DATETIME" -> {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("必须是日期时间");
                }
                yield TextNode.valueOf(com.unique.unexamine.moduleconfig.manage.DateTimeFieldProtocol.normalize(value.textValue()));
            }
            case "ADDRESS", "LOCATION" -> normalizePlace(field.type(), value);
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
            case "REFERENCE" -> {
                if (field.config().path("multiple").asBoolean(false)) {
                    if (!value.isArray()) throw new IllegalArgumentException("多选关联必须是记录列表");
                    LinkedHashSet<Long> ids = new LinkedHashSet<>();
                    for (JsonNode item : value) {
                        if (!item.canConvertToLong() || item.longValue() < 1) throw new IllegalArgumentException("关联记录 ID 无效");
                        ids.add(item.longValue());
                    }
                    ArrayNode result = objectMapper.createArrayNode();
                    ids.forEach(result::add);
                    yield result;
                }
                if (!value.canConvertToLong() || value.longValue() < 1) throw new IllegalArgumentException("必须是有效 ID");
                yield LongNode.valueOf(value.longValue());
            }
            case "ATTACHMENT", "IMAGE", "FILE" -> {
                if (!value.canConvertToLong() || value.longValue() < 1) {
                    throw new IllegalArgumentException("必须选择已上传并通过安全扫描的文件");
                }
                validateFileValue(context, field, value.longValue(), moduleCode, recordId);
                yield LongNode.valueOf(value.longValue());
            }
            case "FILE_GROUP" -> {
                if (!value.isArray()) throw new IllegalArgumentException("必须是文件列表");
                int maximum = Math.max(1, Math.min(50,
                        field.config().path("maxFiles").asInt(field.config().path("maxCount").asInt(10))));
                if (value.size() > maximum) throw new IllegalArgumentException("最多选择 " + maximum + " 个文件");
                LinkedHashSet<Long> ids = new LinkedHashSet<>();
                value.forEach(item -> {
                    if (!item.canConvertToLong() || item.longValue() < 1) {
                        throw new IllegalArgumentException("文件列表包含无效文件");
                    }
                    validateFileValue(context, field, item.longValue(), moduleCode, recordId);
                    ids.add(item.longValue());
                });
                ArrayNode normalizedFiles = objectMapper.createArrayNode();
                ids.forEach(normalizedFiles::add);
                yield normalizedFiles;
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

    /** 地址和坐标按固定结构存入自定义值 JSON，阻止任意对象或越界坐标进入业务数据。 */
    private ObjectNode normalizePlace(String type, JsonNode submitted) {
        JsonNode value = submitted;
        if (value != null && value.isTextual()) {
            try {
                value = objectMapper.readTree(value.textValue());
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("地址或位置必须是有效 JSON 对象");
            }
        }
        if (value == null || !value.isObject()) throw new IllegalArgumentException("地址或位置必须是对象");
        ObjectNode normalized = objectMapper.createObjectNode();
        if ("ADDRESS".equals(type)) {
            for (var names = value.fieldNames(); names.hasNext();) {
                if (!Set.of("province", "city", "district", "detail").contains(names.next())) {
                    throw new IllegalArgumentException("地址包含未定义的属性");
                }
            }
            for (String key : List.of("province", "city", "district", "detail")) {
                JsonNode part = value.get(key);
                if (part != null && !part.isNull() && !part.isTextual()) {
                    throw new IllegalArgumentException("地址各部分必须是文本");
                }
                String text = part == null || part.isNull() ? "" : part.textValue().strip();
                int maximum = "detail".equals(key) ? 500 : 80;
                if (text.length() > maximum) throw new IllegalArgumentException("地址内容过长");
                if ("detail".equals(key) && text.isBlank()) throw new IllegalArgumentException("详细地址不能为空");
                normalized.put(key, text);
            }
            return normalized;
        }
        for (var names = value.fieldNames(); names.hasNext();) {
            if (!Set.of("latitude", "longitude", "name").contains(names.next())) {
                throw new IllegalArgumentException("地理位置包含未定义的属性");
            }
        }
        for (String key : List.of("latitude", "longitude")) {
            JsonNode coordinate = value.get(key);
            if (coordinate == null || !coordinate.isNumber()) {
                throw new IllegalArgumentException("经纬度必须是数字");
            }
            BigDecimal number = coordinate.decimalValue().stripTrailingZeros();
            int bound = "latitude".equals(key) ? 90 : 180;
            if (number.abs().compareTo(BigDecimal.valueOf(bound)) > 0 || number.scale() > 6) {
                throw new IllegalArgumentException("经纬度越界或超过 6 位小数");
            }
            normalized.put(key, number);
        }
        JsonNode name = value.get("name");
        if (name != null && !name.isNull() && !name.isTextual()) {
            throw new IllegalArgumentException("位置名称必须是文本");
        }
        String label = name == null || name.isNull() ? "" : name.textValue().strip();
        if (label.length() > 120) throw new IllegalArgumentException("位置名称过长");
        normalized.put("name", label);
        return normalized;
    }

    /** 评分只收半星刻度，进度只收整数百分比；筛选条件与业务写入共用相同边界。 */
    private BigDecimal validateScoreOrProgress(String type, BigDecimal number) {
        BigDecimal exact = number.stripTrailingZeros();
        if ("RATING".equals(type)) {
            if (exact.signum() < 0 || exact.compareTo(BigDecimal.valueOf(5)) > 0
                    || exact.remainder(new BigDecimal("0.5")).signum() != 0) {
                throw new IllegalArgumentException("评分必须在 0 到 5 之间，且以半星为单位");
            }
        } else if ("PROGRESS".equals(type)) {
            if (exact.signum() < 0 || exact.compareTo(BigDecimal.valueOf(100)) > 0 || exact.scale() > 0) {
                throw new IllegalArgumentException("进度必须是 0 到 100 之间的整数");
            }
        }
        return exact;
    }

    /** 范围字段保留两个端点的顺序；Excel 导出的 JSON 数组文本也可直接再次导入。 */
    private ArrayNode normalizeTemporalRange(String type, JsonNode submitted) {
        JsonNode value = submitted;
        if (value != null && value.isTextual()) {
            try {
                value = objectMapper.readTree(value.textValue());
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("范围必须是开始和结束两个值");
            }
        }
        if (value == null || !value.isArray() || value.size() != 2
                || !value.get(0).isTextual() || !value.get(1).isTextual()) {
            throw new IllegalArgumentException("范围必须是开始和结束两个值");
        }
        String start = value.get(0).textValue();
        String end = value.get(1).textValue();
        if ("DATE_RANGE".equals(type)) {
            if (!start.matches("\\d{4}-\\d{2}-\\d{2}") || !end.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new IllegalArgumentException("日期范围格式应为 yyyy-MM-dd");
            }
            try {
                if (LocalDate.parse(start).isAfter(LocalDate.parse(end))) {
                    throw new IllegalArgumentException("日期范围开始不能晚于结束");
                }
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("日期范围格式应为 yyyy-MM-dd");
            }
        } else {
            if (start.length() != end.length()) {
                throw new IllegalArgumentException("时间范围两端格式必须一致");
            }
            start = normalizeClockTime(start);
            end = normalizeClockTime(end);
            if (LocalTime.parse(start).isAfter(LocalTime.parse(end))) {
                throw new IllegalArgumentException("时间范围开始不能晚于结束");
            }
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        normalized.add(start).add(end);
        return normalized;
    }

    /** 单独的时刻只保存本地钟点，不引入日期或时区；缺省秒数统一补零。 */
    private String normalizeClockTime(String text) {
        if (text == null || !text.matches("\\d{2}:\\d{2}(?::\\d{2})?")) {
            throw new IllegalArgumentException("时间格式应为 HH:mm 或 HH:mm:ss");
        }
        try {
            return LocalTime.parse(text).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间格式应为 HH:mm 或 HH:mm:ss");
        }
    }

    /** 绑定字典与手动选项共用规范化入口；新建、编辑和转化都校验当前有效选项。 */
    private void validateOption(AuthenticatedContext context, PublishedField field, String submitted) {
        if (field.dictionaryId() != null) {
            dictionaryConfigurationService.validateRuntimeOption(context, field.dictionaryId(), submitted);
            return;
        }
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
        Long owner = requestedOwner == null ? currentSystemOwnerMemberId(context) : requestedOwner;
        Long department = requestedDepartment == null
                ? ownerDepartmentId(context.systemId(), context.tenantId(), owner) : requestedDepartment;
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

    /** 显式创建人必须属于目标系统与租户；缺失时使用当前系统所属人。 */
    private Long resolveCreatedByMemberId(AuthenticatedContext context, Long requestedCreatedBy) {
        if (requestedCreatedBy == null) return currentSystemOwnerMemberId(context);
        SystemMember member = memberService.selectById(requestedCreatedBy);
        if (member == null || !Objects.equals(member.getSystemId(), context.systemId())
                || !"ACTIVE".equals(member.getStatus())
                || tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, requestedCreatedBy)
                        .eq(SystemTenantMember::getStatus, "ACTIVE")).isEmpty()) {
            throw new DomainException("RECORD_CREATOR_INVALID", "创建人不是当前租户的有效成员", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return requestedCreatedBy;
    }

    /** 未指定负责人或创建人时使用当前系统所属人，缺少有效成员映射必须中止保存。 */
    private Long currentSystemOwnerMemberId(AuthenticatedContext context) {
        SystemDefinition system = context.systemId() == null ? null : systemService.selectById(context.systemId());
        if (system == null || system.getCreatorAccountId() == null) {
            throw new DomainException("SYSTEM_OWNER_UNAVAILABLE", "系统所属人不可用，请由管理员修正", HttpStatus.CONFLICT);
        }
        Long memberId = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId())
                        .eq(SystemMember::getAccountId, system.getCreatorAccountId())
                        .eq(SystemMember::getStatus, "ACTIVE"))
                .stream().findFirst().map(SystemMember::getId).orElse(null);
        if (memberId == null || tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, context.systemId())
                .eq(SystemTenantMember::getTenantId, context.tenantId())
                .eq(SystemTenantMember::getSystemMemberId, memberId)
                .eq(SystemTenantMember::getStatus, "ACTIVE")).isEmpty()) {
            throw new DomainException("SYSTEM_OWNER_UNAVAILABLE", "系统所属人缺少当前租户的有效成员身份", HttpStatus.CONFLICT);
        }
        return memberId;
    }

    private void replaceValues(
            AuthenticatedContext context, String moduleCode, Long recordId,
            List<PublishedField> fields, Map<String, JsonNode> values) {
        BusinessRecord owner = recordService.selectById(recordId);
        if (owner == null || !context.systemId().equals(owner.getSystemId())
                || !context.tenantId().equals(owner.getTenantId())) {
            throw new DomainException("RECORD_NOT_FOUND", "业务记录不存在", HttpStatus.NOT_FOUND);
        }
        List<Long> currentFieldIds = fields.stream().map(PublishedField::id).toList();
        Map<Long, PublishedField> fieldsById = new HashMap<>();
        fields.forEach(field -> fieldsById.put(field.id(), field));
        List<BusinessRecordValue> previousFileValues = currentFieldIds.isEmpty() ? List.of()
                : valueService.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                        .eq(BusinessRecordValue::getRecordId, recordId)
                        .in(BusinessRecordValue::getFieldId, currentFieldIds)).stream()
                .filter(item -> isFileField(item.getValueType())).toList();
        if (!currentFieldIds.isEmpty()) {
            valueService.delete(Wrappers.<BusinessRecordValue>lambdaQuery()
                    .eq(BusinessRecordValue::getRecordId, recordId)
                    .in(BusinessRecordValue::getFieldId, currentFieldIds));
            relationService.delete(Wrappers.<BizRecordRelation>lambdaQuery()
                    .eq(BizRecordRelation::getSystemId, context.systemId())
                    .eq(BizRecordRelation::getTenantId, context.tenantId())
                    .eq(BizRecordRelation::getSourceRecordId, recordId)
                    .in(BizRecordRelation::getFieldId, currentFieldIds));
        }
        removeManagedFileReferences(moduleCode, recordId, previousFileValues, fieldsById);
        Map<String, PublishedField> byCode = new HashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        values.forEach((code, value) -> {
            PublishedField field = byCode.get(code);
            if (field == null || field.parentFieldId() != null) return;
            if ("SUBTABLE".equals(field.type())) return;
            valueService.insert(toStoredValue(owner.getModuleId(), recordId, field, value));
            if ("REFERENCE".equals(field.type())) {
                int order = 0;
                for (JsonNode selected : value.isArray() ? value : List.of(value)) {
                    BizRecordRelation relation = new BizRecordRelation();
                    relation.setSystemId(context.systemId());
                    relation.setTenantId(context.tenantId());
                    relation.setSourceModuleId(owner.getModuleId());
                    relation.setSourceRecordId(recordId);
                    relation.setFieldId(field.id());
                    relation.setTargetModuleId(field.referenceModuleId());
                    relation.setTargetRecordId(selected.longValue());
                    relation.setRelationOrder(order++);
                    relationService.insert(relation);
                }
            }
            if (isFileField(field.type())) {
                for (Long fileId : fileIds(field.type(), value)) {
                    addManagedFileReference(context, moduleCode, recordId, field, fileId);
                }
            }
        });
        replaceChildRows(context, moduleCode, owner, fields, values);
    }

    private BusinessRecordValue toStoredValue(Long moduleId, Long recordId, PublishedField field, JsonNode value) {
        BusinessRecordValue stored = new BusinessRecordValue();
        stored.setModuleId(moduleId);
        stored.setRecordId(recordId);
        stored.setFieldId(field.id());
        stored.setFieldCode(field.code());
        stored.setValueType(field.type());
        switch (field.type()) {
            case "TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE", "SINGLE_SELECT", "STATUS",
                    "AUTO_NUMBER" ->
                    stored.setValueText(value.textValue());
            case "NUMBER", "MONEY", "PERCENT", "RATING", "PROGRESS" -> stored.setValueNumber(value.decimalValue());
            case "DATE" -> stored.setValueDate(LocalDate.parse(value.textValue()));
            case "TIME" -> stored.setValueText(value.textValue());
            case "DATE_RANGE", "TIME_RANGE", "ADDRESS", "LOCATION" -> stored.setValueJson(value.toString());
            case "DATETIME" -> stored.setValueTime(LocalDateTime.parse(value.textValue()));
            case "BOOLEAN" -> stored.setValueBoolean(value.booleanValue());
            case "RICH_TEXT", "JSON", "MULTI_SELECT", "TAG" -> stored.setValueJson(value.toString());
            case "SECRET" -> stored.setValueText(secretCipher.encrypt(value.textValue(), "record:" + recordId + ":field:" + field.id()));
            case "MEMBER", "DEPARTMENT" -> stored.setValueReferenceId(value.longValue());
            case "REFERENCE" -> {
                if (value.isArray()) stored.setValueJson(value.toString());
                else stored.setValueReferenceId(value.longValue());
            }
            case "ATTACHMENT", "IMAGE", "FILE" -> stored.setValueFileId(value.longValue());
            case "FILE_GROUP" -> stored.setValueJson(value.toString());
            case "CASCADE" -> {
                if (value.isArray()) stored.setValueJson(value.toString());
                else stored.setValueReferenceId(value.longValue());
            }
            default -> throw new IllegalStateException("Unsupported published field type: " + field.type());
        }
        return stored;
    }

    /** 一次业务保存替换当前主记录的明细；同一事务内任何列写入失败都会回滚主记录。 */
    private void replaceChildRows(AuthenticatedContext context, String moduleCode, BusinessRecord owner,
                                  List<PublishedField> fields, Map<String, JsonNode> values) {
        List<PublishedField> parents = fields.stream().filter(field -> "SUBTABLE".equals(field.type())
                && field.parentFieldId() == null).toList();
        for (PublishedField parent : parents) {
            List<BizRecordChildRow> previous = childRowService.selectList(Wrappers.<BizRecordChildRow>lambdaQuery()
                    .eq(BizRecordChildRow::getRecordId, owner.getId())
                    .eq(BizRecordChildRow::getChildFieldId, parent.id()));
            if (!previous.isEmpty()) {
                Map<Long, PublishedField> fileColumns = new HashMap<>();
                fields.stream().filter(field -> parent.id().equals(field.parentFieldId())
                        && isFileField(field.type())).forEach(field -> fileColumns.put(field.id(), field));
                if (!fileColumns.isEmpty()) {
                    childValueService.selectList(Wrappers.<BizRecordChildValue>lambdaQuery()
                                    .in(BizRecordChildValue::getChildRowId,
                                            previous.stream().map(BizRecordChildRow::getId).toList())
                                    .in(BizRecordChildValue::getFieldId, fileColumns.keySet()))
                            .forEach(saved -> {
                                PublishedField field = fileColumns.get(saved.getFieldId());
                                for (Long fileId : fileIds(field.type(), fromChildValue(owner.getId(), saved))) {
                                    removeManagedFileReference(owner.getId(), field.code(), fileId, moduleCode);
                                }
                            });
                }
                childValueService.delete(Wrappers.<BizRecordChildValue>lambdaQuery()
                        .in(BizRecordChildValue::getChildRowId, previous.stream().map(BizRecordChildRow::getId).toList()));
                childRowService.delete(Wrappers.<BizRecordChildRow>lambdaQuery()
                        .in(BizRecordChildRow::getId, previous.stream().map(BizRecordChildRow::getId).toList()));
            }
            JsonNode rows = values.get(parent.code());
            if (rows == null) continue;
            Map<String, PublishedField> columns = new LinkedHashMap<>();
            fields.stream().filter(field -> parent.id().equals(field.parentFieldId()))
                    .forEach(field -> columns.put(field.code(), field));
            Map<Long, Integer> relationOrders = new HashMap<>();
            int order = 0;
            for (JsonNode source : rows) {
                BizRecordChildRow row = new BizRecordChildRow();
                row.setSystemId(context.systemId());
                row.setTenantId(owner.getTenantId());
                row.setModuleId(owner.getModuleId());
                row.setRecordId(owner.getId());
                row.setChildFieldId(parent.id());
                row.setRowKey(source.path("rowKey").asText());
                row.setSortOrder(order++);
                row.setDeleted(false);
                row.setVersion(0);
                childRowService.insert(row);
                JsonNode rowFields = source.path("fields");
                rowFields.fields().forEachRemaining(entry -> {
                    PublishedField column = columns.get(entry.getKey());
                    if (column == null) throw new IllegalStateException("未发布的子表列不能保存");
                    BusinessRecordValue stored = toStoredValue(owner.getModuleId(), owner.getId(), column, entry.getValue());
                    BizRecordChildValue childValue = new BizRecordChildValue();
                    childValue.setModuleId(owner.getModuleId());
                    childValue.setChildFieldId(parent.id());
                    childValue.setChildRowId(row.getId());
                    childValue.setFieldId(column.id());
                    childValue.setValueType(stored.getValueType());
                    childValue.setValueText(stored.getValueText());
                    childValue.setValueNumber(stored.getValueNumber());
                    childValue.setValueDate(stored.getValueDate());
                    childValue.setValueTime(stored.getValueTime());
                    childValue.setValueBoolean(stored.getValueBoolean());
                    childValue.setValueReferenceId(stored.getValueReferenceId());
                    childValue.setValueFileId(stored.getValueFileId());
                    childValue.setValueJson(stored.getValueJson());
                    childValue.setNormalizedText(stored.getNormalizedText());
                    childValueService.insert(childValue);
                    if (isFileField(column.type())) {
                        for (Long fileId : fileIds(column.type(), entry.getValue())) {
                            addManagedFileReference(context, moduleCode, owner.getId(), column, fileId);
                        }
                    }
                    if ("REFERENCE".equals(column.type())) {
                        for (JsonNode selected : entry.getValue().isArray() ? entry.getValue() : List.of(entry.getValue())) {
                            BizRecordRelation relation = new BizRecordRelation();
                            relation.setSystemId(context.systemId());
                            relation.setTenantId(owner.getTenantId());
                            relation.setSourceModuleId(owner.getModuleId());
                            relation.setSourceRecordId(owner.getId());
                            relation.setFieldId(column.id());
                            relation.setTargetModuleId(column.referenceModuleId());
                            relation.setTargetRecordId(selected.longValue());
                            relation.setRelationOrder(relationOrders.merge(column.id(), 1, Integer::sum) - 1);
                            relationService.insert(relation);
                        }
                    }
                });
            }
        }
    }

    /** 旧的转交和集成入口只传成员 ID，原有角色不能因此被重置。 */
    private void replaceParticipants(AuthenticatedContext context, Long recordId, List<Long> memberIds) {
        replaceParticipantRoles(context, recordId, normalizeParticipants(recordId, memberIds, null, null));
    }

    /** 按一次保存的最终团队替换关系；校验发生在主记录写入之前。 */
    private void replaceParticipantRoles(AuthenticatedContext context, Long recordId,
            List<RuntimeRecordParticipant> team) {
        participantService.delete(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                .eq(BusinessRecordParticipant::getRecordId, recordId));
        for (RuntimeRecordParticipant member : team) {
            BusinessRecordParticipant participant = new BusinessRecordParticipant();
            participant.setSystemId(context.systemId());
            participant.setTenantId(context.tenantId());
            participant.setRecordId(recordId);
            participant.setSystemMemberId(member.memberId());
            participant.setParticipantType(member.role());
            participant.setCreatedByMemberId(context.memberId());
            participantService.insert(participant);
        }
    }

    /** 多选和子表关联逐个校验，不能把 JSON 数组隐式转成记录 ID 0。 */
    private void validateRestoredReference(AuthenticatedContext context, Long recordId, PublishedField field,
                                           JsonNode value, List<String> conflicts) {
        if (empty(value)) return;
        for (JsonNode selected : value.isArray() ? value : List.of(value)) {
            if (!selected.canConvertToLong()) {
                conflicts.add(field.code() + ": 引用记录 ID 无效");
                continue;
            }
            long targetId = selected.longValue();
            if (recordId.equals(targetId)) continue;
            boolean targetAvailable = field.referenceModuleId() != null && !recordService.selectList(
                    boundary(context, field.referenceModuleId()).eq(BusinessRecord::getId, targetId)).isEmpty();
            if (!targetAvailable) conflicts.add(field.code() + ": 引用记录缺失、已归档或已删除");
        }
    }

    /** 团队功能停用后，普通编辑仍可原样提交旧成员，不得悄悄清空历史协作关系。 */
    private List<RuntimeRecordParticipant> participantRoles(Long recordId) {
        return participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, recordId)
                        .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                .stream().map(item -> new RuntimeRecordParticipant(item.getSystemMemberId(),
                        normalizedParticipantRole(item.getParticipantType()))).toList();
    }

    /** 兼容只传成员 ID 的旧调用；新表单必须同时提交一致的角色明细。 */
    private List<RuntimeRecordParticipant> normalizeParticipants(Long recordId, List<Long> memberIds,
            List<RuntimeRecordParticipant> submitted, Long ownerId) {
        if (memberIds == null || memberIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new DomainException("PARTICIPANT_INVALID", "团队成员无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Set<Long> requested = new LinkedHashSet<>(memberIds);
        // 旧接口可能把负责人也传进相关人；只在未提供角色明细时去重兼容。
        if (submitted == null) requested.remove(ownerId);
        Map<Long, String> current = new HashMap<>();
        if (recordId != null) {
            participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                    .eq(BusinessRecordParticipant::getRecordId, recordId)).forEach(item ->
                    current.put(item.getSystemMemberId(), normalizedParticipantRole(item.getParticipantType())));
        }
        List<RuntimeRecordParticipant> result = new ArrayList<>();
        if (submitted == null) {
            for (Long memberId : requested) {
                result.add(new RuntimeRecordParticipant(memberId, current.getOrDefault(memberId, "COLLABORATOR")));
            }
        } else {
            Set<Long> seen = new LinkedHashSet<>();
            for (RuntimeRecordParticipant item : submitted) {
                if (item == null || item.memberId() == null || item.memberId() <= 0
                        || !seen.add(item.memberId()) || item.role() == null
                        || !Set.of("COLLABORATOR", "READ_ONLY", "FOLLOWER").contains(item.role())) {
                    throw new DomainException("PARTICIPANT_INVALID", "团队成员或角色无效", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                result.add(item);
            }
            if (!seen.equals(requested)) {
                throw new DomainException("PARTICIPANT_INVALID", "团队成员与角色明细不一致", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        if (ownerId != null && requested.contains(ownerId)) {
            throw new DomainException("PARTICIPANT_INVALID", "负责人不能重复添加到团队成员", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        result.sort(java.util.Comparator.comparing(RuntimeRecordParticipant::memberId));
        return result;
    }

    private String normalizedParticipantRole(String value) {
        return value != null && Set.of("COLLABORATOR", "READ_ONLY", "FOLLOWER").contains(value)
                ? value : "COLLABORATOR";
    }

    private RuntimeRecordView view(AuthenticatedContext context, String moduleCode, String actionCode,
                                   List<String> channels, BusinessRecord record, PublishedModule published) {
        return view(context, moduleCode, actionCode, channels,
                new RecordAccess(record, published, tenantContext(context, record.getTenantId()), null));
    }

    private RuntimeRecordView view(AuthenticatedContext context, String moduleCode, String actionCode,
                                   List<String> channels, RecordAccess access) {
        Map<String, FieldAccessDecision> decisions = protectSecretDecisions(context, moduleCode, actionCode, channels,
                access.published().fields(), fieldPolicyResolver.resolveIntersection(context, moduleCode, actionCode, channels,
                        access.published().fields().stream().map(PublishedField::code).toList()));
        return view(context, moduleCode, actionCode, channels, access, decisions);
    }

    /** 详情与审批共用同一读取路径；调用方可收紧字段决策，隐藏字段在解密和查名称前已排除。 */
    private RuntimeRecordView view(AuthenticatedContext context, String moduleCode, String actionCode,
                                   List<String> channels, RecordAccess access, Map<String, FieldAccessDecision> decisions) {
        BusinessRecord record = access.record();
        PublishedModule published = access.published();
        List<RuntimeRecordParticipant> team = participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getRecordId, record.getId())
                        .orderByAsc(BusinessRecordParticipant::getSystemMemberId))
                .stream().map(item -> new RuntimeRecordParticipant(item.getSystemMemberId(),
                        normalizedParticipantRole(item.getParticipantType()))).toList();
        List<Long> participantIds = team.stream().map(RuntimeRecordParticipant::memberId).distinct().toList();
        Map<String, JsonNode> visibleValues = readProtectedValues(context, access, decisions);
        return new RuntimeRecordView(
                record.getId(), record.getRecordNumber(), record.getTitle(), record.getStatus(), record.getOwnerMemberId(),
                record.getDepartmentId(), record.getCreatedByMemberId(), record.getUpdatedByMemberId(),
                record.getCreatedConfigVersionId(), record.getUpdatedConfigVersionId(), participantIds,
                record.getArchived(), record.getArchiveTime(), record.getDeleted(), record.getDeleteTime(),
                record.getVersion(), record.getCreateTime(), record.getUpdateTime(),
                record.getTenantId(), tenantName(record.getTenantId()), context.tenantId().equals(record.getTenantId()),
                access.share() != null, access.share() == null ? null : access.share().getId(),
                access.share() == null ? null : effectiveShareStatus(access.share()),
                access.share() == null ? List.of() : shareActions(access.share()),
                access.share() == null ? null : access.share().getExpireTime(),
                organizationLabels(access, record.getOwnerMemberId(), record.getDepartmentId(), participantIds, visibleValues),
                fieldPresentation(context, access, visibleValues, decisions),
                recordActions(context, moduleCode, access),
                visibleValues, team);
    }

    /** 来源动作投影不借接收方定义；这里只判断可用性，执行时公共入口仍重新验证。 */
    private List<RuntimeRecordAction> recordActions(AuthenticatedContext actor, String moduleCode, RecordAccess access) {
        Set<String> recordCodes = Set.of("DETAIL", "UPDATE", "ARCHIVE", "DELETE", "RESTORE", "TRANSFER", "CONVERT", "SHARE", "PRINT", "APPROVAL");
        BusinessRecord record = access.record();
        boolean active = !Boolean.TRUE.equals(record.getArchived()) && !Boolean.TRUE.equals(record.getDeleted());
        boolean teamReadOnly = teamWriteRestricted(actor, record);
        List<RuntimeRecordAction> result = new ArrayList<>();
        for (JsonNode action : access.published().configuration().configuration().path("actions")) {
            String code = action.path("code").asText();
            boolean custom = "CUSTOM".equals(action.path("actionType").asText());
            if ((!custom && !recordCodes.contains(code)) || !"ACTIVE".equals(action.path("status").asText())
                    || action.path("id").asLong(0) <= 0 || !action.path("version").canConvertToInt()) continue;
            if (teamReadOnly && !Set.of("DETAIL", "PRINT").contains(code)) continue;
            if ((custom || Set.of("UPDATE", "ARCHIVE", "TRANSFER", "CONVERT", "SHARE", "APPROVAL").contains(code)) && !active) continue;
            if ("DELETE".equals(code) && Boolean.TRUE.equals(record.getDeleted())) continue;
            if ("RESTORE".equals(code) && active) continue;
            if ("CONVERT".equals(code) && access.share() != null) continue;
            if ("APPROVAL".equals(code) && access.share() != null) continue;
            if ("SHARE".equals(code) && (!multiTenantEnabled(actor.systemId())
                    || access.share() != null || !actor.tenantId().equals(record.getTenantId()))) continue;
            if (!recordActionAllowed(actor, moduleCode, code, access)) continue;
            JsonNode config = parseConfig(action.path("configJson").asText("{}"));
            if (custom) {
                if (!supportedManualActionConfig(config)) continue;
                Set<String> parameters = new LinkedHashSet<>();
                config.path("parameterFieldCodes").forEach(p -> parameters.add(p.asText()));
                if (parameters.size() != config.path("parameterFieldCodes").size()) continue;
                List<PublishedField> fields = access.published().fields().stream().filter(f -> parameters.contains(f.code())).toList();
                if (fields.size() != parameters.size() || fields.stream().anyMatch(f -> f.type().startsWith("SYSTEM_")
                        || f.config().path("readOnly").asBoolean(false))) continue;
                Map<String, FieldAccessDecision> customFields = fieldPolicyResolver.resolve(actor, moduleCode, code, "PAGE", List.copyOf(parameters));
                if (parameters.stream().anyMatch(p -> !customFields.get(p).writable())) continue;
            }
            com.fasterxml.jackson.databind.node.ObjectNode presentation = objectMapper.createObjectNode();
            presentation.put("confirmation", config.path("confirmation").asBoolean(false));
            presentation.put("confirmationText", config.path("confirmationText").asText(""));
            result.add(new RuntimeRecordAction(action.path("id").asLong(), code, action.path("name").asText(),
                    action.path("actionType").asText(), action.path("location").asText(), action.path("status").asText(),
                    presentation.toString(), action.path("version").asInt()));
        }
        return result;
    }

    /** 有效记录边界已经确定，按当前操作者范围校验；ALL 范围无需重复读同一记录。 */
    private boolean recordActionAllowed(AuthenticatedContext actor, String moduleCode, String code, RecordAccess access) {
        if (!permissionChecker.allows(actor, "MODULE", moduleCode, code)
                || (access.share() != null && matchingShare(actor, moduleCode, access.record(), code, false) == null)) return false;
        if (access.share() != null) return true;
        if (matchingTerms(actor, moduleCode, code).stream().anyMatch(term -> "ALL".equals(term.type()))) return true;
        return withinSharedScope(actor, moduleCode, code, access.record());
    }

    /** 描述绑定实际来源配置；隐藏字段无描述，脱敏字段不再查字典或关联对象。 */
    private Map<String, RuntimeFieldPresentation> fieldPresentation(
            AuthenticatedContext actor, RecordAccess access, Map<String, JsonNode> visibleValues,
            Map<String, FieldAccessDecision> decisions) {
        Map<String, RuntimeFieldPresentation> result = new LinkedHashMap<>();
        Map<Long, List<JsonNode>> dictionaries = new HashMap<>();
        for (PublishedField field : access.published().fields()) {
            // 子表列由父字段的明细表格展示，避免在主记录上出现空的同名字段。
            if (field.parentFieldId() != null) continue;
            FieldAccessDecision decision = decisions.get(field.code());
            if (decision == null || !decision.readable()) continue;
            JsonNode value = visibleValues.get(field.code());
            String text = null;
            if (decision.maskStrategy() != null && !decision.maskStrategy().isBlank()) {
                text = value == null || value.isNull() ? "未填写" : value.asText();
            } else if ("SECRET".equals(field.type())) {
                // 具体字段的明文授权已在读取前校验；可信展示文本使公共详情不会再退回默认脱敏。
                text = value == null || value.isNull() ? "未填写" : value.asText();
            } else if ("MONEY".equals(field.type())) {
                text = moneyDisplay(field, value);
            } else if (Set.of("PERCENT", "PROGRESS").contains(field.type()) && value != null && value.isNumber()) {
                text = value.decimalValue().stripTrailingZeros().toPlainString() + "%";
            } else if ("RATING".equals(field.type()) && value != null && value.isNumber()) {
                text = value.decimalValue().stripTrailingZeros().toPlainString() + " / 5";
            } else if (Set.of("DATE_RANGE", "TIME_RANGE").contains(field.type()) && value != null
                    && value.isArray() && value.size() == 2) {
                text = value.get(0).asText() + " ～ " + value.get(1).asText();
            } else if ("ADDRESS".equals(field.type()) && value != null && value.isObject()) {
                text = String.join("", value.path("province").asText(), value.path("city").asText(),
                        value.path("district").asText(), value.path("detail").asText());
            } else if ("LOCATION".equals(field.type()) && value != null && value.isObject()) {
                String coordinate = value.path("latitude").asText() + ", " + value.path("longitude").asText();
                text = value.path("name").asText().isBlank() ? coordinate
                        : value.path("name").asText() + "（" + coordinate + "）";
            } else if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "TAG", "STATUS", "CASCADE").contains(field.type())) {
                if (value == null || value.isNull()) {
                    text = "未填写";
                } else {
                    List<JsonNode> items = field.dictionaryId() == null ? new ArrayList<>()
                            : dictionaries.computeIfAbsent(field.dictionaryId(), id -> {
                                try {
                                    return dictionaryConfigurationService.publishedDisplayItems(access.sourceContext(), id);
                                } catch (DomainException unavailable) {
                                    return List.of(); // 来源字典失效不阻止其他可读字段显示，也不回退到接收方字典。
                                }
                            });
                    if (field.dictionaryId() == null) field.config().path("options").forEach(items::add);
                    if (Set.of("MULTI_SELECT", "TAG").contains(field.type()) && value.isArray()) {
                        List<String> labels = new ArrayList<>();
                        for (JsonNode selected : value) labels.add(optionDisplay(items, selected));
                        text = labels.isEmpty() ? "未填写" : String.join("、", labels);
                    } else text = "CASCADE".equals(field.type()) ? cascadeDisplay(items, value) : optionDisplay(items, value);
                }
            } else if ("REFERENCE".equals(field.type())) {
                text = referenceDisplay(actor, access, field, value);
            } else if (isFileField(field.type())) {
                text = fileDisplay(actor, access.sourceContext(), field.type(), value);
            }
            JsonNode configured = null;
            for (JsonNode candidate : access.published().configuration().configuration().path("fields")) {
                if (field.code().equals(candidate.path("code").asText())) { configured = candidate; break; }
            }
            Long fieldId = configured != null && configured.path("id").asLong(0) > 0 ? configured.path("id").asLong() : null;
            Integer order = configured == null || !configured.path("sortOrder").isInt() ? null : configured.path("sortOrder").asInt();
            result.put(field.code(), new RuntimeFieldPresentation(field.name(), field.type(), text, fieldId, order));
        }
        return result;
    }

    /** 金额展示使用发布时的币种和精度，由服务端原始十进制生成，避免浏览器浮点转换。 */
    private String moneyDisplay(PublishedField field, JsonNode value) {
        if (value == null || value.isNull()) return "未填写";
        int precision = field.config().path("precision").asInt(2);
        String currency = field.config().path("currency").asText("").strip();
        BigDecimal number = new BigDecimal(value.asText());
        // 历史值高于当前展示精度时保留原数，不四舍五入也不中断整条记录的读取。
        String amount = number.stripTrailingZeros().scale() > precision
                ? number.toPlainString() : number.setScale(precision).toPlainString();
        return currency.isEmpty() ? amount : currency + " " + amount;
    }

    /** 文件展示只返回当前有权读取的业务文件名，永不把内部文件 ID 当业务值显示。 */
    private String fileDisplay(
            AuthenticatedContext actor, AuthenticatedContext sourceContext, String fieldType, JsonNode value) {
        if (value == null || value.isNull() || (value.isArray() && value.isEmpty())) return "未上传";
        boolean allowed = permissionChecker.allows(actor, "FILE", "OBJECT", "VIEW")
                || permissionChecker.allows(actor, "FILE", "*", "VIEW")
                || permissionChecker.allows(actor, "FILE", "OBJECT", "MANAGE")
                || permissionChecker.allows(actor, "FILE", "*", "MANAGE");
        if (!allowed) return "附件不可用";
        List<String> names = new ArrayList<>();
        for (Long fileId : fileIds(fieldType, value)) {
            FileObject file = fileObjectService.selectById(fileId);
            if (file == null || "DELETED".equals(file.getStatus())
                    || !Objects.equals(file.getPlatformId(), sourceContext.platformId())
                    || !Objects.equals(file.getSystemId(), sourceContext.systemId())
                    || !Objects.equals(file.getTenantId(), sourceContext.tenantId())) {
                names.add("文件已失效");
            } else names.add(file.getOriginalName());
        }
        if (names.isEmpty()) return "未上传";
        return names.size() == 1 ? names.getFirst() : names.size() + " 个文件：" + String.join("、", names);
    }

    /** 历史选项只解释当前已发布名称；未知值不显示内部代码。 */
    private String optionDisplay(List<JsonNode> items, JsonNode value) {
        for (JsonNode item : items) {
            if (value.asText().equals(item.path("value").asText(item.path("code").asText()))) {
                return item.path("label").asText("选项名称不可用")
                        + ("DISABLED".equals(item.path("status").asText()) ? "（已停用）" : "");
            }
        }
        return "选项已失效";
    }

    /** FINAL 保存方式补齐已发布父路径，PATH 保持路径顺序；异常路径不泄露内部 ID。 */
    private String cascadeDisplay(List<JsonNode> items, JsonNode value) {
        Map<Long, JsonNode> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.path("id").asLong(-1), item));
        List<Long> path = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode id : value) {
                if (!id.isIntegralNumber()) return "层级选项已失效";
                path.add(id.longValue());
            }
        } else if (value.isIntegralNumber()) {
            JsonNode cursor = byId.get(value.longValue());
            Set<Long> visited = new LinkedHashSet<>();
            while (cursor != null) {
                long id = cursor.path("id").asLong(-1);
                if (!visited.add(id)) return "层级选项已失效";
                path.addFirst(id);
                if (cursor.path("parentId").isNull()) break;
                cursor = byId.get(cursor.path("parentId").asLong(-1));
                if (cursor == null) return "层级选项已失效";
            }
        }
        if (path.isEmpty()) return "层级选项已失效";
        List<String> labels = new ArrayList<>();
        for (Long id : path) {
            JsonNode item = byId.get(id);
            if (item == null) return "层级选项已失效";
            labels.add(item.path("label").asText("层级名称不可用")
                    + ("DISABLED".equals(item.path("status").asText()) ? "（已停用）" : ""));
        }
        return String.join(" / ", labels);
    }

    /** 父记录可读不授予关联记录权限；原操作者仍须同时满足目标 LIST、DETAIL 及有效共享。 */
    private String referenceDisplay(AuthenticatedContext actor, RecordAccess parent, PublishedField field, JsonNode value) {
        if (value == null || value.isNull()) return "未填写";
        if (value.isArray()) {
            List<String> names = new ArrayList<>();
            for (JsonNode selected : value) names.add(referenceDisplay(actor, parent, field, selected));
            return names.isEmpty() ? "未填写" : String.join("、", names);
        }
        if (!value.isIntegralNumber() || field.referenceModuleId() == null) return "关联记录不可用";
        ConfiguredModule target = moduleService.selectById(field.referenceModuleId());
        if (target == null || !actor.systemId().equals(target.getSystemId()) || !"ACTIVE".equals(target.getStatus())
                || moduleIdentifierIntegrity.hasActiveConflict(target)) return "关联记录不可用";
        if (!permissionChecker.allows(actor, "MODULE", target.getCode(), "LIST")
                || !permissionChecker.allows(actor, "MODULE", target.getCode(), "DETAIL")) return "无权查看关联记录";
        BusinessRecord candidate = recordService.selectList(boundary(parent.sourceContext(), target.getId())
                .eq(BusinessRecord::getId, value.longValue())).stream().findFirst().orElse(null);
        if (candidate == null) return "关联记录不可用";
        if (actor.tenantId().equals(candidate.getTenantId())) {
            if (!withinScope(actor, target.getCode(), "LIST", candidate.getId(), target.getId())
                    || !withinScope(actor, target.getCode(), "DETAIL", candidate.getId(), target.getId())) {
                return "无权查看关联记录";
            }
        } else {
            boolean shared = sharedSourceMetadata.activeTenantPair(actor, candidate.getTenantId())
                    && matchingShare(actor, target.getCode(), candidate, "LIST", false) != null
                    && matchingShare(actor, target.getCode(), candidate, "DETAIL", false) != null;
            if (!shared) return "无权查看关联记录";
        }
        return candidate.getTitle() == null || candidate.getTitle().isBlank() ? "未命名记录" : candidate.getTitle();
    }

    /** 名称仅来自已裁剪的字段和固定归属信息，防止隐藏字段通过标签旁路泄露。 */
    private SystemPeopleDirectoryModels.Labels organizationLabels(
            RecordAccess access, Long ownerId, Long departmentId, List<Long> participantIds,
            Map<String, JsonNode> visibleValues) {
        Set<Long> members = new LinkedHashSet<>(participantIds);
        Set<Long> departments = new LinkedHashSet<>();
        if (ownerId != null) members.add(ownerId);
        if (departmentId != null) departments.add(departmentId);
        access.published().fields().forEach(field -> {
            JsonNode value = visibleValues.get(field.code());
            // 脱敏后文本、不可读字段和非法引用不会触发组织名称解析。
            if (value == null || !value.isIntegralNumber()) return;
            if (Set.of("MEMBER", "SYSTEM_CREATED_BY", "SYSTEM_UPDATED_BY").contains(field.type())) {
                members.add(value.longValue());
            } else if ("DEPARTMENT".equals(field.type())) {
                departments.add(value.longValue());
            }
        });
        return peopleDirectoryService.recordLabels(access.sourceContext(), members, departments);
    }

    /** 只能以已授权记录解析来源组织；不接受客户端指定来源租户或借用其权限。 */
    @Transactional(readOnly = true)
    public SystemPeopleDirectoryModels.Directory organizationOptions(
            AuthenticatedContext actor, String moduleCode, Long recordId, String actionCode,
            String fieldCode, String traceId) {
        String action = actionCode == null ? "UPDATE" : actionCode.strip().toUpperCase(Locale.ROOT);
        RecordAccess access = requireRecordAccess(actor, moduleCode, action, recordId, true, traceId);
        boolean standard = Set.of("UPDATE", "TRANSFER").contains(action);
        Set<String> parameters = new LinkedHashSet<>();
        if (!standard) {
            requireRecordAccess(actor, moduleCode, "UPDATE", recordId, true, traceId);
            JsonNode configuredAction = findPublishedAction(access.published(), action);
            JsonNode config = parseConfig(configuredAction.path("configJson").asText("{}"));
            if (!"CUSTOM".equals(configuredAction.path("actionType").asText())
                    || !"ACTIVE".equals(configuredAction.path("status").asText())
                    || !"UPDATE_FIELDS".equals(config.path("operation").asText())) {
                throw new DomainException("MANUAL_ACTION_NOT_AVAILABLE", "该动作不能选择记录组织字段", HttpStatus.CONFLICT);
            }
            config.path("parameterFieldCodes").forEach(value -> parameters.add(value.asText()));
        }
        boolean members = standard && fieldCode == null;
        boolean departments = standard && fieldCode == null;
        List<PublishedField> candidates = access.published().fields().stream()
                .filter(field -> fieldCode == null || field.code().equals(fieldCode))
                .filter(field -> standard || parameters.contains(field.code())).toList();
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolve(
                actor, moduleCode, "UPDATE", "PAGE", candidates.stream().map(PublishedField::code).toList());
        Map<String, FieldAccessDecision> actionDecisions = standard ? decisions : fieldPolicyResolver.resolve(
                actor, moduleCode, action, "PAGE", candidates.stream().map(PublishedField::code).toList());
        for (PublishedField field : candidates) {
            if (!Set.of("MEMBER", "DEPARTMENT").contains(field.type()) || field.config().path("readOnly").asBoolean(false)
                    || !decisions.get(field.code()).writable() || !actionDecisions.get(field.code()).writable()) continue;
            members |= "MEMBER".equals(field.type());
            departments |= "DEPARTMENT".equals(field.type());
        }
        if (!members && !departments) {
            deny(actor, moduleCode, action, traceId, Map.of("recordId", recordId, "reason", "ORGANIZATION_FIELD_NOT_WRITABLE"));
        }
        return peopleDirectoryService.recordOptions(access.sourceContext(), members, departments);
    }

    /** 编辑读取采用 UPDATE 字段策略；来源配置固定由已授权记录决定。 */
    @Transactional(readOnly = true)
    public RuntimeRecordEditContext editContext(AuthenticatedContext actor, String moduleCode, Long recordId, String traceId) {
        RecordAccess access = requireRecordAccess(actor, moduleCode, "UPDATE", recordId, true, traceId);
        return new RuntimeRecordEditContext(view(actor, moduleCode, "UPDATE", List.of("PAGE"), access),
                publicationService.recordEditConfiguration(actor, moduleCode, access.published().configuration()));
    }

    /** 审批候选表单使用真实来源记录和当前处理人权限；不将待生效值写成正式数据。 */
    @Transactional(readOnly = true)
    public RuntimeRecordEditContext approvalEditContext(AuthenticatedContext actor, BusinessAction.Input input,
            JsonNode originalConfiguration, Map<String, Object> nodePolicy, String traceId) {
        NodeFormAccess node = nodeFormAccess(actor, input, originalConfiguration, nodePolicy, traceId);
        ObjectNode configuration = objectMapper.createObjectNode();
        ArrayNode fields = objectMapper.createArrayNode();
        node.access().published().configuration().configuration().path("fields").forEach(field -> {
            String code = field.path("code").asText();
            if (node.fields().stream().noneMatch(candidate -> candidate.code().equals(code))) return;
            ObjectNode guarded = field.deepCopy();
            ObjectNode config = (ObjectNode) parseConfig(field.path("configJson").asText("{}"));
            config.remove("defaultValue");
            guarded.put("configJson", config.toString());
            guarded.set("access", objectMapper.valueToTree(node.decisions().get(code)));
            if (requiredAtNode(nodePolicy, code)) guarded.put("required", true);
            fields.add(guarded);
        });
        configuration.set("fields", fields);
        configuration.put("readOnlyAtNode", node.fields().stream().noneMatch(field -> node.decisions().get(field.code()).writable()));
        // 文件暂不通过数值补丁写入，待公共受保护上传输入接通后再开放，避免旁路附件权限。
        configuration.put("fileInputsPending", nodePolicy.get("editable") instanceof List<?> requested
                && node.access().published().fields().stream().anyMatch(field -> requested.contains(field.code())
                && Set.of("ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP").contains(field.type())));
        RuntimeModuleConfiguration source = node.access().published().configuration();
        Map<String, JsonNode> visible = new LinkedHashMap<>();
        for (PublishedField field : node.fields()) {
            FieldAccessDecision decision = node.decisions().get(field.code());
            // 保护文本不能作为编辑初值，否则未改动的脱敏值会覆盖真实值。
            JsonNode value = node.candidate().get(field.code());
            if ("SECRET".equals(field.type()) || decision.maskStrategy() != null && !decision.maskStrategy().isBlank()) {
                // 只读展示采用保护值；可替换输入仍不预填，不能把脱敏内容回传成修改。
                if (!decision.writable() && value != null) visible.put(field.code(), fieldPolicyResolver.protect(value, decision));
                continue;
            }
            if (value != null) visible.put(field.code(), value);
        }
        RuntimeRecordView candidate;
        if (input.recordId() == null) {
            candidate = proposedView(actor, input, node.access(), visible, node.decisions());
        } else {
            RuntimeRecordView current = view(actor, input.moduleCode(), node.permissionAction(), List.of("PAGE", "FLOW"),
                    node.access(), node.decisions());
            List<Long> participants = participantIds(input.recordId());
            candidate = current.withFields(visible,
                    organizationLabels(node.access(), current.ownerMemberId(), current.departmentId(), participants, visible),
                    fieldPresentation(actor, node.access(), visible, node.decisions()), List.of());
        }
        return new RuntimeRecordEditContext(candidate, new RuntimeModuleConfiguration(source.moduleId(), source.versionId(),
                source.versionNumber(), source.publicationVersion(), configuration));
    }

    /** 只验证显式修改的补丁；遗漏保留原候选值，清空保留 null，节点必填在合并后判断。 */
    @Transactional(readOnly = true)
    public Map<String, JsonNode> validateApprovalFields(AuthenticatedContext actor, BusinessAction.Input input,
            JsonNode originalConfiguration, Map<String, Object> nodePolicy, Map<String, JsonNode> submitted, String traceId) {
        NodeFormAccess node = nodeFormAccess(actor, input, originalConfiguration, nodePolicy, traceId);
        Set<String> eligible = node.fields().stream().filter(field -> node.decisions().get(field.code()).writable())
                .map(PublishedField::code).collect(java.util.stream.Collectors.toSet());
        if (!eligible.containsAll(submitted.keySet())) throw new DomainException("FLOW_NODE_FIELD_DENIED",
                "提交包含当前节点不可编辑、无权限或已失效的字段", HttpStatus.FORBIDDEN);
        List<PublishedField> changed = node.fields().stream().filter(field -> submitted.containsKey(field.code())).toList();
        Map<String, JsonNode> normalized = validateFields(actor, node.access().sourceContext(), input.moduleCode(),
                "UPDATE", changed, submitted, traceId, input.recordId());
        Map<String, JsonNode> patch = new LinkedHashMap<>();
        submitted.keySet().forEach(code -> patch.put(code, normalized.getOrDefault(code,
                com.fasterxml.jackson.databind.node.NullNode.instance)));
        Map<String, JsonNode> merged = new LinkedHashMap<>(node.candidate());
        merged.putAll(patch);
        for (PublishedField field : node.requiredFields()) {
            if (empty(merged.get(field.code()))) {
                FieldAccessDecision decision = node.decisions().get(field.code());
                String message = decision.readable() && decision.writable() ? "请填写节点必填字段：" + field.name()
                        : "当前节点仍有未补充的必填内容，当前权限不能编辑，请联系有权处理人或管理员调整配置";
                throw new DomainException("FLOW_NODE_FIELD_REQUIRED", message, HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        return patch;
    }

    /** 自动审批只核对既有候选，不创建补丁；原动作授权和全部必填约束不能随自动处理消失。 */
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public void validateAutomaticApproval(AuthenticatedContext actor, BusinessAction.Input candidate,
            JsonNode configuration, Map<String, Object> policy, String traceId) {
        authorizeActionProgress(actor, candidate, null, traceId);
        NodeFormAccess node = nodeFormAccess(actor, candidate, configuration, policy, traceId, true);
        for (PublishedField field : node.requiredFields()) {
            if (empty(node.candidate().get(field.code()))) {
                throw new DomainException("FLOW_NODE_FIELD_REQUIRED",
                        "超时自动同意未执行：候选内容仍有必填项，请由有权处理人补充后审批", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    /** 人工同意后的候选只以绑定实例的密文留作再次核验，不在动作响应或日志返回字段内容。 */
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public String sealApprovalCandidate(AuthenticatedContext actor, BusinessAction.Input candidate,
            JsonNode configuration, String binding, String traceId) {
        return secretCipher.encrypt(approvalCandidateState(actor, candidate, configuration, traceId).toString(), binding);
    }

    /** 全部字段及正式记录版本一致才允许沿用审批事实；密钥与数据归属异常不降级为相同。 */
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public boolean approvalCandidateUnchanged(AuthenticatedContext actor, BusinessAction.Input candidate,
            JsonNode configuration, String seal, String binding, String traceId) {
        try {
            return objectMapper.readTree(secretCipher.decrypt(seal, binding))
                    // JSON持久化往返会把小Long/Decimal还原为不同JsonNode类型，两边必须按同一传输协议核对。
                    .equals(objectMapper.readTree(approvalCandidateState(actor, candidate, configuration, traceId).toString()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new DomainException("FLOW_REPEAT_APPROVAL_EVIDENCE_INVALID", "原审批候选记录无法核验，请重新人工审批", HttpStatus.CONFLICT);
        }
    }

    /** 来源权限与发布版本仍走公共入口；实例锁等待之后核对正式记录当前版本，拒绝旧RR候选。 */
    private JsonNode approvalCandidateState(AuthenticatedContext actor, BusinessAction.Input candidate,
            JsonNode configuration, String traceId) {
        authorizeActionProgress(actor, candidate, null, traceId);
        NodeFormAccess node = nodeFormAccess(actor, candidate, configuration, Map.of(), traceId, true);
        ObjectNode state = objectMapper.createObjectNode();
        state.put("moduleId", candidate.moduleId());
        state.put("configVersionId", candidate.configVersionId());
        state.put("dataTenantId", candidate.dataTenantId());
        state.set("payload", objectMapper.valueToTree(candidate.payload()));
        state.set("fields", objectMapper.valueToTree(node.candidate()));
        if (candidate.recordId() != null) {
            BusinessRecord current = recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                    .eq(BusinessRecord::getId, candidate.recordId()).last("FOR SHARE")).stream().findFirst()
                    .orElseThrow(() -> new DomainException("RECORD_NOT_FOUND", "原业务记录不存在", HttpStatus.NOT_FOUND));
            if (!Objects.equals(current.getVersion(), node.access().record().getVersion())) {
                throw new DomainException("RECORD_VERSION_CONFLICT", "正式记录在等待审批期间已变化，请重新核对", HttpStatus.CONFLICT);
            }
            state.put("recordId", current.getId());
            state.put("recordVersion", current.getVersion());
        }
        return state;
    }

    /** 合法只读处理人可确认候选；节点配置不能授予编辑权限，必填约束也不能随失权消失。 */
    private NodeFormAccess nodeFormAccess(AuthenticatedContext actor, BusinessAction.Input input,
            JsonNode originalConfiguration, Map<String, Object> nodePolicy, String traceId) {
        return nodeFormAccess(actor, input, originalConfiguration, nodePolicy, traceId, false);
    }

    /** 自动核对采用原动作授权，不要求发起人另外拥有人工填写的 UPDATE 或 DETAIL 权限。 */
    private NodeFormAccess nodeFormAccess(AuthenticatedContext actor, BusinessAction.Input input,
            JsonNode originalConfiguration, Map<String, Object> nodePolicy, String traceId, boolean automatic) {
        RecordAccess access;
        String permissionAction = "UPDATE";
        if (automatic) {
            permissionAction = input.actionCode();
            access = input.recordId() == null ? proposedRecordAccess(actor, input, permissionAction, traceId)
                    : requireRecordAccess(actor, input.moduleCode(), permissionAction, input.recordId(), false, traceId);
        } else try {
            access = input.recordId() == null ? proposedRecordAccess(actor, input, "UPDATE", traceId)
                    : requireRecordAccess(actor, input.moduleCode(), "UPDATE", input.recordId(), true, traceId);
        } catch (DomainException exception) {
            if (exception.status() != HttpStatus.FORBIDDEN) throw exception;
            permissionAction = "DETAIL";
            access = input.recordId() == null ? proposedRecordAccess(actor, input, "DETAIL", traceId)
                    : requireRecordAccess(actor, input.moduleCode(), "DETAIL", input.recordId(), false, traceId);
        }
        if (!Objects.equals(input.moduleId(), access.published().configuration().moduleId())
                || !Objects.equals(input.configVersionId(), access.published().configuration().versionId())
                || !Objects.equals(input.dataTenantId(), access.record().getTenantId())) {
            throw new DomainException("ACTION_CONFIGURATION_VERSION_CONFLICT", "业务发布配置或记录来源已变化，不能提交旧节点表单", HttpStatus.CONFLICT);
        }
        Set<String> parameters = null;
        if (!Set.of("CREATE", "UPDATE").contains(input.actionCode())) {
            JsonNode action = null;
            for (JsonNode publishedAction : originalConfiguration.path("actions")) {
                if (input.actionCode().equals(publishedAction.path("code").asText())) action = publishedAction;
            }
            JsonNode config = action == null ? objectMapper.createObjectNode() : parseConfig(action.path("configJson").asText("{}"));
            if (action == null || !"CUSTOM".equals(action.path("actionType").asText())
                    || !supportedManualActionConfig(config)) {
                throw new DomainException("FLOW_NODE_FORM_NOT_AVAILABLE", "当前业务动作尚未接通节点表单提交", HttpStatus.CONFLICT);
            }
            parameters = new LinkedHashSet<>();
            for (JsonNode code : config.path("parameterFieldCodes")) parameters.add(code.asText());
        }
        Map<String, FieldAccessDecision> decisions = fieldPolicyResolver.resolveIntersection(actor, input.moduleCode(),
                permissionAction, List.of("PAGE", "FLOW"), access.published().fields().stream().map(PublishedField::code).toList());
        List<PublishedField> visibleFields = new ArrayList<>();
        List<PublishedField> candidateFields = new ArrayList<>();
        List<PublishedField> requiredFields = new ArrayList<>();
        for (PublishedField field : access.published().fields()) {
            FieldAccessDecision decision = decisions.get(field.code());
            boolean configured = com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.allowsEdit(nodePolicy, field.code())
                    && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.matchesBinding(nodePolicy, field.id(), field.code(),
                    field.type(), field.dictionaryId(), field.referenceModuleId())
                    && (parameters == null || parameters.contains(field.code()));
            // 内部必填候选只参与服务端检查，不向失权处理人返回未授权值。
            if (configured || automatic || requiredAtNode(nodePolicy, field.code())
                    && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.allowsEdit(nodePolicy, field.code())
                    && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.matchesBinding(nodePolicy, field.id(), field.code(),
                    field.type(), field.dictionaryId(), field.referenceModuleId())) {
                candidateFields.add(field);
                // CUSTOM 的参数仅限制可写补丁，不能删除冻结节点的必填约束；非参数候选只在服务端核对。
                if (field.required() || requiredAtNode(nodePolicy, field.code())) requiredFields.add(field);
            }
            boolean permitted = configured && decision.readable() && !field.type().startsWith("SYSTEM_")
                    && !field.config().path("readOnly").asBoolean(false)
                    && !Set.of("ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP").contains(field.type());
            if (permitted) {
                visibleFields.add(field);
                decisions.put(field.code(), new FieldAccessDecision(field.code(), "PAGE∩FLOW", true,
                        "UPDATE".equals(permissionAction) && decision.writable(), decision.maskStrategy(),
                        decision.contributingRoleIds(), "UPDATE".equals(permissionAction) && decision.writable()
                        ? decision.reason() : "当前节点仅可核对，未授予编辑权限"));
            }
            else decisions.put(field.code(), new FieldAccessDecision(field.code(), "PAGE∩FLOW", false, false,
                    null, decision.contributingRoleIds(), "当前节点不可编辑"));
            if (permitted && "SECRET".equals(field.type())) decisions.put(field.code(), new FieldAccessDecision(
                    field.code(), "PAGE∩FLOW", true, "UPDATE".equals(permissionAction) && decision.writable(),
                    "FULL", decision.contributingRoleIds(), "密钥不预填，仅允许有权处理人替换"));
        }
        Map<String, JsonNode> candidate = input.recordId() == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(readRawValues(input.recordId(), candidateFields));
        JsonNode payload = objectMapper.valueToTree(input.payload());
        JsonNode submitted = payload.path(Set.of("CREATE", "UPDATE").contains(input.actionCode()) ? "fields" : "values");
        if (submitted.isObject()) for (PublishedField field : candidateFields) {
            if (submitted.has(field.code())) candidate.put(field.code(), submitted.get(field.code()));
            else if ("CREATE".equals(input.actionCode())) {
                // 原新建已校验过的发布默认值也是待生效候选；批准的显式 null 不再进入此分支。
                JsonNode value = configuredDefault(field);
                if (!empty(value)) candidate.put(field.code(), normalize(access.sourceContext(), field, value));
            }
        }
        if (nodePolicy.get("required") instanceof List<?> required) for (Object code : required) {
            if (access.published().fields().stream().noneMatch(field -> field.code().equals(code)
                    && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.allowsEdit(nodePolicy, field.code())
                    && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.matchesBinding(nodePolicy, field.id(), field.code(),
                    field.type(), field.dictionaryId(), field.referenceModuleId()))) {
                throw new DomainException("ACTION_CONFIGURATION_VERSION_CONFLICT", "节点必填字段的冻结配置已失效，不能继续提交", HttpStatus.CONFLICT);
            }
        }
        if (automatic && nodePolicy.get("editable") instanceof List<?> editable) for (Object code : editable) {
            if (access.published().fields().stream().noneMatch(field -> field.code().equals(code)
                    && com.unique.unexamine.flow.manage.FlowNodeFieldPolicy.matchesBinding(nodePolicy, field.id(), field.code(),
                    field.type(), field.dictionaryId(), field.referenceModuleId()))) {
                throw new DomainException("ACTION_CONFIGURATION_VERSION_CONFLICT", "节点字段的冻结配置已失效，不能自动同意", HttpStatus.CONFLICT);
            }
        }
        return new NodeFormAccess(access, permissionAction, visibleFields, decisions, candidate, requiredFields);
    }

    private boolean requiredAtNode(Map<String, Object> policy, String code) {
        return policy.get("required") instanceof List<?> required && required.contains(code);
    }

    /** 候选值仅供服务端合并和必填校验；对外视图在返回前重新裁剪。 */
    private record NodeFormAccess(RecordAccess access, String permissionAction, List<PublishedField> fields,
                                  Map<String, FieldAccessDecision> decisions, Map<String, JsonNode> candidate,
                                  List<PublishedField> requiredFields) {}

    /** 任务字段已经按冻结身份与当前权限校验；目录仍由实际来源发布字段决定。 */
    @Transactional(readOnly = true)
    public DictionaryModels.RuntimeDictionary approvalFieldDictionary(AuthenticatedContext actor,
            BusinessAction.Input input, JsonNode configuration, Map<String, Object> policy,
            String fieldCode, Long parentId, String traceId) {
        NodeFormAccess node = nodeFormAccess(actor, input, configuration, policy, traceId);
        PublishedField field = requireNodeOptionField(node, fieldCode);
        if (!Set.of("SINGLE_SELECT", "MULTI_SELECT", "TAG", "STATUS", "CASCADE").contains(field.type()) || field.dictionaryId() == null) {
            throw new DomainException("FIELD_DICTIONARY_NOT_AVAILABLE", "该字段没有可选择的字典", HttpStatus.CONFLICT);
        }
        return dictionaryConfigurationService.runtime(node.access().sourceContext(), field.dictionaryId(), parentId);
    }

    /** 无正式 ID 的新建审批也走同一关联目标授权，不查询占位数据。 */
    @Transactional(readOnly = true)
    public List<RuntimeReferenceOption> approvalReferenceOptions(AuthenticatedContext actor,
            BusinessAction.Input input, JsonNode configuration, Map<String, Object> policy,
            String fieldCode, String search, String traceId) {
        NodeFormAccess node = nodeFormAccess(actor, input, configuration, policy, traceId);
        return referenceOptions(actor, node.access(), requireNodeOptionField(node, fieldCode), search, traceId);
    }

    /** 组织候选来自已核验原新建或记录租户，客户端不能指定其他成员目录。 */
    @Transactional(readOnly = true)
    public SystemPeopleDirectoryModels.Directory approvalOrganizationOptions(AuthenticatedContext actor,
            BusinessAction.Input input, JsonNode configuration, Map<String, Object> policy,
            String fieldCode, String traceId) {
        NodeFormAccess node = nodeFormAccess(actor, input, configuration, policy, traceId);
        PublishedField field = requireNodeOptionField(node, fieldCode);
        if (!Set.of("MEMBER", "DEPARTMENT").contains(field.type())) {
            throw new DomainException("FLOW_NODE_FIELD_DENIED", "该字段不能选择组织成员", HttpStatus.FORBIDDEN);
        }
        return peopleDirectoryService.recordOptions(node.access().sourceContext(), "MEMBER".equals(field.type()),
                "DEPARTMENT".equals(field.type()));
    }

    private PublishedField requireNodeOptionField(NodeFormAccess node, String code) {
        return node.fields().stream().filter(field -> field.code().equals(code) && node.decisions().get(code).writable()).findFirst()
                .orElseThrow(() -> new DomainException("FLOW_NODE_FIELD_DENIED", "该字段当前不可编辑，不能读取来源选项", HttpStatus.FORBIDDEN));
    }

    /** 单选、状态和级联只能读取该记录可写字段实际绑定的来源字典。 */
    @Transactional(readOnly = true)
    public DictionaryModels.RuntimeDictionary fieldDictionary(
            AuthenticatedContext actor, String moduleCode, Long recordId, String fieldCode, Long parentId, String traceId) {
        return fieldDictionary(actor, moduleCode, recordId, fieldCode, parentId, "UPDATE", traceId);
    }

    /** 手动动作选项还必须属于实际来源动作参数，同时满足自定义动作和标准编辑权限。 */
    @Transactional(readOnly = true)
    public DictionaryModels.RuntimeDictionary fieldDictionary(
            AuthenticatedContext actor, String moduleCode, Long recordId, String fieldCode, Long parentId,
            String actionCode, String traceId) {
        RecordAccess access = optionRecordAccess(actor, moduleCode, recordId, fieldCode, actionCode, traceId);
        PublishedField field = requireWritableOptionField(actor, moduleCode, fieldCode, access, traceId);
        if (!Set.of("SINGLE_SELECT", "MULTI_SELECT", "TAG", "STATUS", "CASCADE").contains(field.type()) || field.dictionaryId() == null) {
            throw new DomainException("FIELD_DICTIONARY_NOT_AVAILABLE", "该字段没有可选择的字典", HttpStatus.CONFLICT);
        }
        return dictionaryConfigurationService.runtime(access.sourceContext(), field.dictionaryId(), parentId);
    }

    /** 引用仍须来源同租户，并分别校验实际操作者的目标列表、详情、范围和共享限制。 */
    @Transactional(readOnly = true)
    public List<RuntimeReferenceOption> referenceOptions(
            AuthenticatedContext actor, String moduleCode, Long recordId, String fieldCode, String search, String traceId) {
        return referenceOptions(actor, moduleCode, recordId, fieldCode, search, "UPDATE", traceId);
    }

    /** 关联参数沿用实际来源绑定，动作参数限制不能被选项接口绕过。 */
    @Transactional(readOnly = true)
    public List<RuntimeReferenceOption> referenceOptions(
            AuthenticatedContext actor, String moduleCode, Long recordId, String fieldCode, String search,
            String actionCode, String traceId) {
        RecordAccess access = optionRecordAccess(actor, moduleCode, recordId, fieldCode, actionCode, traceId);
        PublishedField field = requireWritableOptionField(actor, moduleCode, fieldCode, access, traceId);
        return referenceOptions(actor, access, field, search, traceId);
    }

    /** 实际行和待审批新建行共用关联查询与目标范围校验。 */
    private List<RuntimeReferenceOption> referenceOptions(AuthenticatedContext actor, RecordAccess access,
            PublishedField field, String search, String traceId) {
        if (!"REFERENCE".equals(field.type()) || field.referenceModuleId() == null) {
            throw new DomainException("FIELD_REFERENCE_NOT_AVAILABLE", "该字段没有关联目标模块", HttpStatus.CONFLICT);
        }
        ConfiguredModule target = moduleService.selectById(field.referenceModuleId());
        if (target == null || !actor.systemId().equals(target.getSystemId()) || !"ACTIVE".equals(target.getStatus())) {
            throw new DomainException("FIELD_REFERENCE_NOT_AVAILABLE", "关联目标模块不可用", HttpStatus.CONFLICT);
        }
        if (moduleIdentifierIntegrity.hasActiveConflict(target)) return List.of();
        // 缺少目标权限时没有可选项，但不能阻止用户编辑父记录的其他合法字段。
        if (!permissionChecker.allows(actor, "MODULE", target.getCode(), "LIST")
                || !permissionChecker.allows(actor, "MODULE", target.getCode(), "DETAIL")) return List.of();
        String term = search == null ? "" : search.strip();
        if (term.length() > 200) throw new DomainException("SEARCH_INVALID", "搜索内容不能超过 200 字", HttpStatus.BAD_REQUEST);
        List<RuntimeReferenceOption> result = new ArrayList<>();
        for (BusinessRecord candidate : recordService.selectList(boundary(access.sourceContext(), field.referenceModuleId())
                .like(!term.isEmpty(), BusinessRecord::getTitle, term)
                .orderByDesc(BusinessRecord::getUpdateTime).orderByDesc(BusinessRecord::getId))) {
            try {
                validateActorReference(actor, field, LongNode.valueOf(candidate.getId()), traceId);
                result.add(new RuntimeReferenceOption(candidate.getId(), candidate.getTitle(), candidate.getRecordNumber()));
                if (result.size() == 50) break;
            } catch (IllegalArgumentException ignored) {
                // 被目标范围或共享限制排除的记录不回显名称，也不借来源成员权限填充选项。
            }
        }
        return result;
    }

    /** 来源参数目录只由已授权记录与发布动作决定，客户端不能指定其他目录。 */
    private RecordAccess optionRecordAccess(AuthenticatedContext actor, String moduleCode, Long recordId,
                                            String fieldCode, String actionCode, String traceId) {
        String code = actionCode == null ? "UPDATE" : actionCode.strip().toUpperCase(Locale.ROOT);
        if ("UPDATE".equals(code)) return requireRecordAccess(actor, moduleCode, code, recordId, true, traceId);
        ManualActionDefinition definition = manualActionDefinition(actor, moduleCode, recordId, code, traceId);
        if (definition.parameters().stream().noneMatch(field -> field.code().equals(fieldCode))) {
            deny(actor, moduleCode, code, traceId, Map.of("recordId", recordId, "reason", "OPTION_NOT_ACTION_PARAMETER"));
        }
        return definition.access();
    }

    /** 不可写、只读或不存在的字段不能用选择接口旁路读取来源目录。 */
    private PublishedField requireWritableOptionField(
            AuthenticatedContext actor, String moduleCode, String fieldCode, RecordAccess access, String traceId) {
        PublishedField field = access.published().fields().stream().filter(item -> item.code().equals(fieldCode)).findFirst().orElse(null);
        FieldAccessDecision decision = field == null ? null : fieldPolicyResolver.resolve(actor, moduleCode,
                "UPDATE", "PAGE", List.of(fieldCode)).get(fieldCode);
        if (field == null || field.type().startsWith("SYSTEM_") || field.config().path("readOnly").asBoolean(false)
                || decision == null || !decision.writable()) {
            deny(actor, moduleCode, "UPDATE", traceId, Map.of("recordId", access.record().getId(), "reason", "OPTION_FIELD_NOT_WRITABLE"));
        }
        return field;
    }

    /** 审计快照里的主字段和子表密钥列都必须脱敏。 */
    private Map<String, JsonNode> redactSecrets(Map<String, JsonNode> values, List<PublishedField> fields) {
        Map<String, PublishedField> byCode = new HashMap<>();
        fields.forEach(field -> byCode.put(field.code(), field));
        Map<String, JsonNode> protectedValues = new LinkedHashMap<>();
        values.forEach((code, value) -> protectedValues.put(code,
                redactSecretValue(byCode.get(code), value, fields)));
        return protectedValues;
    }

    private JsonNode redactSecretValue(PublishedField field, JsonNode value, List<PublishedField> fields) {
        if (field == null || value == null || value.isNull()) return value;
        if ("SECRET".equals(field.type())) return TextNode.valueOf("***");
        if (!"SUBTABLE".equals(field.type()) || !value.isArray()) return value;
        Set<String> secretColumns = fields.stream().filter(column -> field.id().equals(column.parentFieldId())
                && "SECRET".equals(column.type())).map(PublishedField::code)
                .collect(java.util.stream.Collectors.toSet());
        if (secretColumns.isEmpty()) return value;
        ArrayNode redacted = value.deepCopy();
        for (JsonNode row : redacted) {
            JsonNode rowFields = row.path("fields");
            if (!rowFields.isObject()) continue;
            for (String code : secretColumns) {
                if (rowFields.hasNonNull(code)) ((ObjectNode) rowFields).put(code, "***");
            }
        }
        return redacted;
    }

    /** 只有详情的每个适用渠道、每个贡献角色都明确授权该字段时才能解密；写回执始终脱敏。 */
    private Map<String, FieldAccessDecision> protectSecretDecisions(AuthenticatedContext context, String moduleCode,
            String actionCode, List<String> channels, List<PublishedField> fields,
            Map<String, FieldAccessDecision> decisions) {
        Map<String, FieldAccessDecision> protectedDecisions = new LinkedHashMap<>(decisions);
        for (PublishedField field : fields) {
            if (!"SECRET".equals(field.type())) continue;
            FieldAccessDecision decision = decisions.get(field.code());
            if (decision == null || !decision.readable()) continue;
            boolean explicitRead = "DETAIL".equals(actionCode) && channels.stream().allMatch(channel ->
                    fieldPolicyResolver.explicitSecretReadable(context, moduleCode, channel, decision));
            if (!explicitRead) protectedDecisions.put(field.code(), new FieldAccessDecision(field.code(), decision.channel(),
                    true, decision.writable(), "FULL", decision.contributingRoleIds(), "密码/密钥默认脱敏"));
        }
        return protectedDecisions;
    }

    private Map<String, JsonNode> readRawValues(Long recordId, List<PublishedField> fields) {
        Set<String> visibleCodes = fields.stream().map(PublishedField::code).collect(java.util.stream.Collectors.toSet());
        Map<String, JsonNode> result = new LinkedHashMap<>();
        valueService.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                        .eq(BusinessRecordValue::getRecordId, recordId).orderByAsc(BusinessRecordValue::getId))
                .stream().filter(value -> visibleCodes.contains(value.getFieldCode()))
                .forEach(value -> result.put(value.getFieldCode(), fromStoredValue(value)));
        for (PublishedField parent : fields) {
            if (!"SUBTABLE".equals(parent.type()) || parent.parentFieldId() != null) continue;
            Map<Long, PublishedField> columns = new LinkedHashMap<>();
            fields.stream().filter(field -> parent.id().equals(field.parentFieldId()))
                    .forEach(field -> columns.put(field.id(), field));
            ArrayNode rows = objectMapper.createArrayNode();
            List<BizRecordChildRow> savedRows = childRowService.selectList(Wrappers.<BizRecordChildRow>lambdaQuery()
                    .eq(BizRecordChildRow::getRecordId, recordId)
                    .eq(BizRecordChildRow::getChildFieldId, parent.id())
                    .eq(BizRecordChildRow::getDeleted, false)
                    .orderByAsc(BizRecordChildRow::getSortOrder, BizRecordChildRow::getId));
            for (BizRecordChildRow savedRow : savedRows) {
                ObjectNode row = objectMapper.createObjectNode();
                row.put("rowKey", savedRow.getRowKey());
                ObjectNode rowFields = objectMapper.createObjectNode();
                childValueService.selectList(Wrappers.<BizRecordChildValue>lambdaQuery()
                                .eq(BizRecordChildValue::getChildRowId, savedRow.getId()))
                        .forEach(savedValue -> {
                            PublishedField column = columns.get(savedValue.getFieldId());
                            if (column != null) rowFields.set(column.code(), fromChildValue(recordId, savedValue));
                        });
                row.set("fields", rowFields);
                rows.add(row);
            }
            result.put(parent.code(), rows);
        }
        return result;
    }

    private JsonNode fromChildValue(Long recordId, BizRecordChildValue saved) {
        BusinessRecordValue value = new BusinessRecordValue();
        value.setRecordId(recordId);
        value.setFieldId(saved.getFieldId());
        value.setValueType(saved.getValueType());
        value.setValueText(saved.getValueText());
        value.setValueNumber(saved.getValueNumber());
        value.setValueDate(saved.getValueDate());
        value.setValueTime(saved.getValueTime());
        value.setValueBoolean(saved.getValueBoolean());
        value.setValueReferenceId(saved.getValueReferenceId());
        value.setValueFileId(saved.getValueFileId());
        value.setValueJson(saved.getValueJson());
        return fromStoredValue(value);
    }

    private Map<String, JsonNode> readProtectedValues(AuthenticatedContext actor, RecordAccess access,
                                                     Map<String, FieldAccessDecision> decisions) {
        BusinessRecord record = access.record();
        List<PublishedField> fields = access.published().fields();
        // 默认脱敏读取不需要解密；失权字段也不能因其密文异常阻断其他正常数据读取。
        List<PublishedField> readableFields = fields.stream().filter(field -> decisions.get(field.code()) != null
                && decisions.get(field.code()).readable()).toList();
        List<PublishedField> decryptedFields = readableFields.stream().filter(field -> !"SECRET".equals(field.type())
                || decisions.get(field.code()).maskStrategy() == null || decisions.get(field.code()).maskStrategy().isBlank()).toList();
        Map<String, JsonNode> raw = readRawValues(record.getId(), decryptedFields);
        Set<Long> maskedSecrets = readableFields.stream().filter(field -> "SECRET".equals(field.type())
                && !decryptedFields.contains(field)).map(PublishedField::id).collect(java.util.stream.Collectors.toSet());
        if (!maskedSecrets.isEmpty()) valueService.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                        .eq(BusinessRecordValue::getRecordId, record.getId()).in(BusinessRecordValue::getFieldId, maskedSecrets))
                .forEach(value -> raw.put(value.getFieldCode(), TextNode.valueOf("***")));
        fields.stream().filter(field -> field.type().startsWith("SYSTEM_")).forEach(field -> {
            JsonNode value = systemFieldValue(record, field.type());
            if (value != null) raw.put(field.code(), value);
        });
        Map<String, JsonNode> result = new LinkedHashMap<>();
        raw.forEach((code, value) -> {
            JsonNode protectedValue = fieldPolicyResolver.protect(value, decisions.get(code));
            if (protectedValue != null) {
                result.put(code, protectedValue);
            }
        });
        // 子表每行保留可编辑的 ID 原值，同时提供当前查看者有权读取的业务展示文本。
        for (PublishedField parent : fields) {
            JsonNode rows = result.get(parent.code());
            if (!"SUBTABLE".equals(parent.type()) || parent.parentFieldId() != null || rows == null || !rows.isArray()) continue;
            ArrayNode displayedRows = rows.deepCopy();
            for (JsonNode row : displayedRows) {
                JsonNode rowFields = row.path("fields");
                ObjectNode displayFields = objectMapper.createObjectNode();
                for (PublishedField column : fields) {
                    if (!parent.id().equals(column.parentFieldId()) || !rowFields.has(column.code())) continue;
                    FieldAccessDecision decision = decisions.get(column.code());
                    if (decision == null || !decision.readable()) continue;
                    JsonNode value = rowFields.get(column.code());
                    String display = "REFERENCE".equals(column.type())
                            ? referenceDisplay(actor, access, column, value)
                            : isFileField(column.type()) ? fileDisplay(actor, access.sourceContext(), column.type(), value)
                            : "MONEY".equals(column.type()) && (decision.maskStrategy() == null || decision.maskStrategy().isBlank())
                                    ? moneyDisplay(column, value)
                            : Set.of("PERCENT", "PROGRESS").contains(column.type()) && value.isNumber()
                                    ? value.decimalValue().stripTrailingZeros().toPlainString() + "%"
                            : "RATING".equals(column.type()) && value.isNumber()
                                    ? value.decimalValue().stripTrailingZeros().toPlainString() + " / 5"
                            : value.isArray() ? value.toString() : value.asText();
                    displayFields.put(column.code(), display);
                }
                ((ObjectNode) row).set("displayFields", displayFields);
            }
            result.put(parent.code(), displayedRows);
        }
        return result;
    }

    private JsonNode systemFieldValue(BusinessRecord record, String fieldType) {
        return switch (fieldType) {
            case "SYSTEM_CREATED_BY" -> record.getCreatedByMemberId() == null
                    ? null : LongNode.valueOf(record.getCreatedByMemberId());
            case "SYSTEM_CREATED_AT" -> record.getCreateTime() == null
                    ? null : TextNode.valueOf(record.getCreateTime().toString());
            case "SYSTEM_UPDATED_BY" -> record.getUpdatedByMemberId() == null
                    ? null : LongNode.valueOf(record.getUpdatedByMemberId());
            case "SYSTEM_UPDATED_AT" -> record.getUpdateTime() == null
                    ? null : TextNode.valueOf(record.getUpdateTime().toString());
            default -> null;
        };
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
            // Jackson 数字节点的 Java 类型可不同；业务值的 JSON 相同就不生成虚假的字段变更。
            if (java.util.Objects.equals(beforeValue, afterValue)
                    || beforeValue != null && afterValue != null
                    && beforeValue.toString().equals(afterValue.toString())) {
                continue;
            }
            PublishedField field = byCode.get(code);
            AuditFieldChange change = new AuditFieldChange();
            change.setAuditEventId(auditEventId);
            change.setFieldCode(code);
            change.setValueType(field == null ? "UNKNOWN" : field.type());
            boolean secret = field != null && "SECRET".equals(field.type());
            change.setBeforeValueJson(toJsonValue(redactSecretValue(field, beforeValue, fields)));
            change.setAfterValueJson(toJsonValue(redactSecretValue(field, afterValue, fields)));
            boolean childSecret = field != null && "SUBTABLE".equals(field.type()) && fields.stream()
                    .anyMatch(column -> field.id().equals(column.parentFieldId()) && "SECRET".equals(column.type()));
            boolean sensitive = secret || childSecret || (field != null && field.config().path("sensitive").asBoolean(false))
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
        if ("SECRET".equals(value.getValueType())) {
            return TextNode.valueOf(secretCipher.decrypt(value.getValueText(),
                    "record:" + value.getRecordId() + ":field:" + value.getFieldId()));
        }
        if (value.getValueText() != null) {
            return TextNode.valueOf(value.getValueText());
        }
        if (value.getValueNumber() != null) {
            return DecimalNode.valueOf(value.getValueNumber());
        }
        if (value.getValueDate() != null) {
            return TextNode.valueOf(value.getValueDate().toString());
        }
        if (value.getValueTime() != null) {
            return TextNode.valueOf(value.getValueTime().toString());
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
        if (value.getValueFileId() != null) {
            return LongNode.valueOf(value.getValueFileId());
        }
        return objectMapper.nullNode();
    }

    private boolean empty(JsonNode value) {
        return value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank())
                || (value.isArray() && value.isEmpty());
    }

    /** 文件字段只接受当前安全域内已完成扫描的文件；编辑时允许继续使用该记录已经引用的文件。 */
    private void validateFileValue(
            AuthenticatedContext context, PublishedField field, Long fileId,
            String moduleCode, Long recordId) {
        // 保留当前记录已引用的文件不是新增引用；替换文件仍须单独取得文件引用权限。
        boolean alreadyReferenced = recordId != null && moduleCode != null && fileReferenceService.selectList(
                        Wrappers.<FileReference>lambdaQuery().eq(FileReference::getFileId, fileId)
                                .eq(FileReference::getOwnerType, "BUSINESS_RECORD")
                                .eq(FileReference::getOwnerId, moduleCode + ":" + recordId)
                                .eq(FileReference::getFieldCode, field.code()))
                .stream().anyMatch(reference -> Objects.equals(reference.getPlatformId(), context.platformId())
                        && Objects.equals(reference.getSystemId(), context.systemId())
                        && Objects.equals(reference.getTenantId(), context.tenantId()));
        boolean referencePermission = permissionChecker.allows(context, "FILE", "OBJECT", "REFERENCE")
                || permissionChecker.allows(context, "FILE", "*", "REFERENCE")
                || permissionChecker.allows(context, "FILE", "OBJECT", "MANAGE")
                || permissionChecker.allows(context, "FILE", "*", "MANAGE");
        if (!referencePermission && !alreadyReferenced) throw new IllegalArgumentException("当前账号没有文件引用权限");
        FileObject file = fileObjectService.selectById(fileId);
        boolean sameContext = file != null && Objects.equals(file.getPlatformId(), context.platformId())
                && Objects.equals(file.getSystemId(), context.systemId())
                && Objects.equals(file.getTenantId(), context.tenantId());
        if (!sameContext || !"ACTIVE".equals(file.getStatus()) || !"CLEAN".equals(file.getScanStatus())) {
            throw new IllegalArgumentException("文件不存在、未通过安全扫描或不属于当前业务空间");
        }
        if ("IMAGE".equals(field.type()) && !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("图片字段只能选择图片文件");
        }
        long maximumBytes = Math.max(1L, Math.min(1024L, field.config().path("maxSizeMb").asLong(50L)))
                * 1024L * 1024L;
        if (file.getSizeBytes() > maximumBytes) {
            throw new IllegalArgumentException("文件超过字段配置的 " + (maximumBytes / 1024L / 1024L) + " MB 限制");
        }
        JsonNode allowedTypes = field.config().path("allowedTypes");
        if (allowedTypes.isArray() && !allowedTypes.isEmpty()) {
            String contentType = file.getContentType().toLowerCase(Locale.ROOT);
            String fileName = file.getOriginalName().toLowerCase(Locale.ROOT);
            boolean accepted = false;
            for (JsonNode item : allowedTypes) {
                String rule = item.asText().strip().toLowerCase(Locale.ROOT);
                if (rule.isBlank()) continue;
                accepted = rule.startsWith(".") ? fileName.endsWith(rule)
                        : rule.endsWith("/*") ? contentType.startsWith(rule.substring(0, rule.length() - 1))
                        : contentType.equals(rule);
                if (accepted) break;
            }
            if (!accepted) throw new IllegalArgumentException("文件类型不符合字段允许的类型");
        }
        boolean uploadedByActor = Objects.equals(file.getUploadedByAccountId(), context.accountId());
        if (!uploadedByActor && !alreadyReferenced) {
            throw new IllegalArgumentException("只能选择本人上传或当前记录已经引用的文件");
        }
    }

    private boolean isFileField(String type) {
        return Set.of("ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP").contains(type);
    }

    private List<Long> fileIds(String type, JsonNode value) {
        if (value == null || value.isNull()) return List.of();
        if (!"FILE_GROUP".equals(type)) return List.of(value.longValue());
        List<Long> ids = new ArrayList<>();
        value.forEach(item -> ids.add(item.longValue()));
        return ids;
    }

    private void removeManagedFileReferences(
            String moduleCode, Long recordId, List<BusinessRecordValue> values,
            Map<Long, PublishedField> fieldsById) {
        for (BusinessRecordValue value : values) {
            PublishedField field = fieldsById.get(value.getFieldId());
            if (field == null) continue;
            JsonNode previous = fromStoredValue(value);
            for (Long fileId : fileIds(field.type(), previous)) {
                removeManagedFileReference(recordId, field.code(), fileId, moduleCode);
            }
        }
    }

    /** 字段值被替换时，同步移除其业务文件引用；新值会在同一事务内重新绑定。 */
    private void removeManagedFileReference(Long recordId, String fieldCode, Long fileId, String moduleCode) {
        fileReferenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getFileId, fileId)
                        .eq(FileReference::getOwnerType, "BUSINESS_RECORD")
                        .eq(FileReference::getOwnerId, moduleCode + ":" + recordId)
                        .eq(FileReference::getFieldCode, fieldCode))
                .forEach(reference -> fileReferenceService.deleteById(reference.getId()));
    }

    private void addManagedFileReference(
            AuthenticatedContext context, String moduleCode, Long recordId,
            PublishedField field, Long fileId) {
        String ownerId = moduleCode + ":" + recordId;
        boolean exists = !fileReferenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                .eq(FileReference::getFileId, fileId).eq(FileReference::getOwnerType, "BUSINESS_RECORD")
                .eq(FileReference::getOwnerId, ownerId).eq(FileReference::getFieldCode, field.code())).isEmpty();
        if (exists) return;
        FileReference reference = new FileReference();
        reference.setPlatformId(context.platformId());
        reference.setFileId(fileId);
        reference.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        reference.setSystemId(context.systemId());
        reference.setTenantId(context.tenantId());
        reference.setOwnerType("BUSINESS_RECORD");
        reference.setOwnerId(ownerId);
        reference.setFieldCode(field.code());
        reference.setReferenceType("IMAGE".equals(field.type()) ? "IMAGE" : "ATTACHMENT");
        reference.setCreatedByAccountId(context.accountId());
        fileReferenceService.insert(reference);
    }

    /** 记录字段保存的文件只能通过记录编辑替换，附件面板不能绕过必填校验直接删除。 */
    @Transactional(readOnly = true)
    public boolean isManagedAttachmentValue(
            String moduleCode, Long recordId, String fieldCode, Long fileId) {
        if (moduleCode == null || recordId == null || fieldCode == null || fileId == null) return false;
        boolean mainValue = valueService.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                        .eq(BusinessRecordValue::getRecordId, recordId)
                        .eq(BusinessRecordValue::getFieldCode, fieldCode))
                .stream().anyMatch(value -> fileIds(value.getValueType(), fromStoredValue(value)).contains(fileId));
        if (mainValue) return true;
        BusinessRecord record = recordService.selectById(recordId);
        if (record == null) return false;
        List<Long> fieldIds = moduleFieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .eq(ConfiguredModuleField::getModuleId, record.getModuleId())
                        .eq(ConfiguredModuleField::getCode, fieldCode)
                        .isNotNull(ConfiguredModuleField::getParentFieldId))
                .stream().map(ConfiguredModuleField::getId).toList();
        if (fieldIds.isEmpty()) return false;
        List<Long> rowIds = childRowService.selectList(Wrappers.<BizRecordChildRow>lambdaQuery()
                        .eq(BizRecordChildRow::getRecordId, recordId)
                        .eq(BizRecordChildRow::getDeleted, false))
                .stream().map(BizRecordChildRow::getId).toList();
        if (rowIds.isEmpty()) return false;
        return childValueService.selectList(Wrappers.<BizRecordChildValue>lambdaQuery()
                        .in(BizRecordChildValue::getChildRowId, rowIds)
                        .in(BizRecordChildValue::getFieldId, fieldIds))
                .stream().anyMatch(value -> fileIds(value.getValueType(), fromChildValue(recordId, value))
                        .contains(fileId));
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
            Long dictionaryId, Long referenceModuleId, Long parentFieldId, JsonNode config) {
    }

    private record QueryAccessPolicy(
            Set<String> queryableFields, Map<String, Set<String>> searchableFields, Set<String> builtInFields,
            List<RuntimeListQueryField> fields) {
    }

    private record ListQueryAccess(PublishedModule currentPublished, List<RecordAccess> records) {
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

    /** 手动动作在公共执行前解析出的不可变配置和完整更新请求。 */
    private record ManualActionPlan(RecordAccess access, Long actionId, UpdateRuntimeRecordRequest update) {
    }

    @FunctionalInterface
    private interface ScopeClause {
        void apply(LambdaQueryWrapper<BusinessRecord> wrapper);
    }
}
