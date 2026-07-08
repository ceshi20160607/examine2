package com.unique.examine.app.manage.openapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.app.base.entity.OpenapiApp;
import com.unique.examine.app.base.entity.OpenapiCallLog;
import com.unique.examine.app.base.service.OpenapiAppBaseService;
import com.unique.examine.app.base.service.OpenapiCallLogBaseService;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiAppQuery;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiAppSaveRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiAppVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordMutationRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordMutationResult;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordRow;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordSearchRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiCallLogQuery;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiCallLogVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiDeleteResultVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiRateLimitUpdateRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiScopeUpdateRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiScopeVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiSecretRefVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiSecretRotationJobVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiSecretRotationPhaseVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiSecretRotationRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OperationMetadata;
import com.unique.examine.app.manage.openapi.OpenApiModels.RateLimitMetadata;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.base.entity.SysSecretRef;
import com.unique.examine.core.base.entity.SysSecretRotationJob;
import com.unique.examine.core.base.service.SysSecretRefBaseService;
import com.unique.examine.core.base.service.SysSecretRotationJobBaseService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.manage.task.PersistedAsyncTaskService;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.module.base.entity.ModuleDefinition;
import com.unique.examine.module.base.entity.ModuleDynamicRecord;
import com.unique.examine.module.base.entity.ModuleDynamicValue;
import com.unique.examine.module.base.entity.ModuleFieldDefinition;
import com.unique.examine.module.base.service.ModuleDefinitionBaseService;
import com.unique.examine.module.base.service.ModuleDynamicRecordBaseService;
import com.unique.examine.module.base.service.ModuleDynamicValueBaseService;
import com.unique.examine.module.base.service.ModuleFieldDefinitionBaseService;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * OpenAPI external application, SecretRef, rate-limit and call-log service.
 */
@Service
public class OpenApiService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final OpenapiAppBaseService appBaseService;
    private final OpenapiCallLogBaseService callLogBaseService;
    private final SysSecretRefBaseService secretRefBaseService;
    private final SysSecretRotationJobBaseService rotationJobBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final ModuleDefinitionBaseService moduleBaseService;
    private final ModuleFieldDefinitionBaseService fieldBaseService;
    private final ModuleDynamicRecordBaseService recordBaseService;
    private final ModuleDynamicValueBaseService valueBaseService;
    private final PersistedAsyncTaskService asyncTaskService;
    private final ObjectMapper objectMapper;

    public OpenApiService(OpenapiAppBaseService appBaseService,
                          OpenapiCallLogBaseService callLogBaseService,
                          SysSecretRefBaseService secretRefBaseService,
                          SysSecretRotationJobBaseService rotationJobBaseService,
                          PlatTenantBaseService tenantBaseService,
                          ModuleDefinitionBaseService moduleBaseService,
                          ModuleFieldDefinitionBaseService fieldBaseService,
                          ModuleDynamicRecordBaseService recordBaseService,
                          ModuleDynamicValueBaseService valueBaseService,
                          PersistedAsyncTaskService asyncTaskService,
                          ObjectMapper objectMapper) {
        this.appBaseService = appBaseService;
        this.callLogBaseService = callLogBaseService;
        this.secretRefBaseService = secretRefBaseService;
        this.rotationJobBaseService = rotationJobBaseService;
        this.tenantBaseService = tenantBaseService;
        this.moduleBaseService = moduleBaseService;
        this.fieldBaseService = fieldBaseService;
        this.recordBaseService = recordBaseService;
        this.valueBaseService = valueBaseService;
        this.asyncTaskService = asyncTaskService;
        this.objectMapper = objectMapper;
    }

    /**
     * Query external applications in one system.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query object
     * @return application page
     */
    public PageResult<OpenApiAppVO> apps(String systemId, PageRequest pageRequest, OpenApiAppQuery query) {
        Long resolvedSystemId = parseRequiredId(systemId, "系统ID格式不正确");
        List<OpenApiAppVO> matched = appBaseService.list(appWrapper(resolvedSystemId, query)
                        .orderByDesc(OpenapiApp::getUpdatedAt))
                .stream()
                .map(app -> toVO(app, null))
                .filter(app -> matchesScope(app.scopes(), Objects.isNull(query) ? null : query.scope()))
                .filter(app -> matchesKeyword(List.of(app.externalAppCode(), app.appName()),
                        Objects.isNull(query) ? null : query.keyword()))
                .toList();
        return page(matched, pageRequest);
    }

    /**
     * Create one external application with a persisted SecretRef.
     *
     * @param systemId system id
     * @param headerIdempotencyKey idempotency key from header
     * @param request request body
     * @return created application
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiAppVO create(String systemId, String headerIdempotencyKey, OpenApiAppSaveRequest request) {
        Long resolvedSystemId = parseRequiredId(systemId, "系统ID格式不正确");
        Long tenantId = resolveTenantId(resolvedSystemId, Objects.isNull(request) ? null : request.tenantId());
        String externalAppCode = normalizeCode(Objects.isNull(request) ? null : request.externalAppCode(),
                "external_app_" + shortTrace(RequestContext.current().traceId()));
        OpenapiApp existing = appBaseService.getOne(new LambdaQueryWrapper<OpenapiApp>()
                .eq(OpenapiApp::getSystemId, resolvedSystemId)
                .eq(OpenapiApp::getTenantId, tenantId)
                .eq(OpenapiApp::getExternalAppCode, externalAppCode)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(existing)) {
            return toVO(existing, operation("CREATE_OPENAPI_APP", resolveIdempotencyKey(headerIdempotencyKey,
                    Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_app_create"),
                    "REUSED_EXISTING", null));
        }
        LocalDateTime now = LocalDateTime.now();
        String secretRefId = "sec_openapi_" + externalAppCode + "_" + shortTrace(RequestContext.current().traceId());
        saveSecretRef(secretRefId, "v1", "ACTIVE",
                Objects.isNull(request) ? null : request.secretMaterialRef(), now);
        OpenapiApp entity = new OpenapiApp();
        entity.setSystemId(resolvedSystemId);
        entity.setTenantId(tenantId);
        entity.setExternalAppCode(externalAppCode);
        entity.setAppName(safeText(Objects.isNull(request) ? null : request.appName(), externalAppCode));
        entity.setStatus(Objects.isNull(request) || Objects.isNull(request.status()) ? ENABLED : request.status());
        entity.setOpenapiSecretRefId(secretRefId);
        entity.setScopes(toJson(safeList(Objects.isNull(request) ? null : request.scopes(),
                List.of("record.data.read"))));
        entity.setCallbackUrl(Objects.isNull(request) ? null : request.callbackUrl());
        entity.setRateLimit(toJson(safeRateLimit(Objects.isNull(request) ? null : request.rateLimit())));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        appBaseService.saveEntity(entity);
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_app_create");
        writeCallLog(entity, "openapi.app.manage", "POST",
                "/api/v1/systems/" + systemId + "/openapi/apps", "SUCCESS", idempotencyKey, null);
        return toVO(entity, operation("CREATE_OPENAPI_APP", idempotencyKey, "CREATED", null));
    }

    /**
     * Return one external application detail.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @return application detail
     */
    public OpenApiAppVO detail(String systemId, String externalAppId) {
        return toVO(requireApp(systemId, externalAppId), null);
    }

    /**
     * Update external application basic configuration.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @param headerIdempotencyKey idempotency key from header
     * @param request request body
     * @return updated application
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiAppVO update(String systemId, String externalAppId, String headerIdempotencyKey,
                               OpenApiAppSaveRequest request) {
        OpenapiApp entity = requireApp(systemId, externalAppId);
        if (Objects.nonNull(request)) {
            if (StringUtils.hasText(request.appName())) {
                entity.setAppName(request.appName());
            }
            if (Objects.nonNull(request.status())) {
                entity.setStatus(request.status());
            }
            if (StringUtils.hasText(request.callbackUrl())) {
                entity.setCallbackUrl(request.callbackUrl());
            }
            if (Objects.nonNull(request.scopes())) {
                entity.setScopes(toJson(request.scopes()));
            }
            if (Objects.nonNull(request.rateLimit())) {
                entity.setRateLimit(toJson(safeRateLimit(request.rateLimit())));
            }
        }
        entity.setUpdatedAt(LocalDateTime.now());
        appBaseService.updateById(entity);
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_app_update");
        writeCallLog(entity, "openapi.app.manage", "PATCH",
                "/api/v1/systems/" + systemId + "/openapi/apps/" + externalAppId,
                "SUCCESS", idempotencyKey, null);
        return toVO(entity, operation("UPDATE_OPENAPI_APP", idempotencyKey, "UPDATED", null));
    }

    /**
     * Delete one external application.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @param headerIdempotencyKey idempotency key from header
     * @param request optional request body
     * @return delete result
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiDeleteResultVO delete(String systemId, String externalAppId, String headerIdempotencyKey,
                                        OpenApiAppSaveRequest request) {
        OpenapiApp entity = requireApp(systemId, externalAppId);
        appBaseService.removeById(entity.getId());
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_app_delete");
        writeCallLog(entity, "openapi.app.manage", "DELETE",
                "/api/v1/systems/" + systemId + "/openapi/apps/" + externalAppId,
                "SUCCESS", idempotencyKey, null);
        return new OpenApiDeleteResultVO(externalAppId, "DELETED", true,
                operation("DELETE_OPENAPI_APP", idempotencyKey, "DELETED", null));
    }

    /**
     * Return configured scopes.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @return scopes
     */
    public List<OpenApiScopeVO> scopes(String systemId, String externalAppId) {
        return scopeMetadata(readScopes(requireApp(systemId, externalAppId).getScopes()));
    }

    /**
     * Update application scopes.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @param headerIdempotencyKey idempotency key from header
     * @param request request body
     * @return updated application
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiAppVO updateScopes(String systemId, String externalAppId, String headerIdempotencyKey,
                                     OpenApiScopeUpdateRequest request) {
        OpenapiApp entity = requireApp(systemId, externalAppId);
        entity.setScopes(toJson(safeList(Objects.isNull(request) ? null : request.scopes(),
                List.of("record.data.read"))));
        entity.setUpdatedAt(LocalDateTime.now());
        appBaseService.updateById(entity);
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_scope_update");
        writeCallLog(entity, "openapi.scope.manage", "PUT",
                "/api/v1/systems/" + systemId + "/openapi/apps/" + externalAppId + "/scopes",
                "SUCCESS", idempotencyKey, null);
        return toVO(entity, operation("UPDATE_OPENAPI_SCOPES", idempotencyKey, "UPDATED", null));
    }

    /**
     * Return rate-limit metadata.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @return rate limit
     */
    public RateLimitMetadata rateLimit(String systemId, String externalAppId) {
        return readRateLimit(requireApp(systemId, externalAppId).getRateLimit());
    }

    /**
     * Update rate-limit metadata.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @param headerIdempotencyKey idempotency key from header
     * @param request request body
     * @return updated application
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiAppVO updateRateLimit(String systemId, String externalAppId, String headerIdempotencyKey,
                                        OpenApiRateLimitUpdateRequest request) {
        OpenapiApp entity = requireApp(systemId, externalAppId);
        entity.setRateLimit(toJson(safeRateLimit(Objects.isNull(request) ? null : request.rateLimit())));
        entity.setUpdatedAt(LocalDateTime.now());
        appBaseService.updateById(entity);
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_rate_limit_update");
        writeCallLog(entity, "openapi.rate_limit.manage", "PATCH",
                "/api/v1/systems/" + systemId + "/openapi/apps/" + externalAppId + "/rate-limit",
                "SUCCESS", idempotencyKey, null);
        return toVO(entity, operation("UPDATE_OPENAPI_RATE_LIMIT", idempotencyKey, "UPDATED", null));
    }

    /**
     * Start an OpenAPI SecretRef rotation job.
     *
     * @param systemId system id
     * @param externalAppId app id
     * @param headerIdempotencyKey idempotency key from header
     * @param request request body
     * @return rotation job
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenApiSecretRotationJobVO rotateSecret(String systemId, String externalAppId,
                                                   String headerIdempotencyKey,
                                                   OpenApiSecretRotationRequest request) {
        OpenapiApp app = requireApp(systemId, externalAppId);
        LocalDateTime now = LocalDateTime.now();
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "openapi_secret_rotation");
        String newVersion = safeText(Objects.isNull(request) ? null : request.requestedVersion(),
                "v" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String previousSecretRefId = app.getOpenapiSecretRefId();
        String newSecretRefId = "sec_openapi_" + app.getExternalAppCode() + "_rot_"
                + shortTrace(RequestContext.current().traceId());
        SysSecretRef secretRef = saveSecretRef(newSecretRefId, newVersion, "ACTIVE",
                Objects.isNull(request) ? null : request.newMaterialRef(), now);
        disableSecretRef(previousSecretRefId, now);
        app.setOpenapiSecretRefId(secretRef.getSecretRefId());
        app.setUpdatedAt(now);
        appBaseService.updateById(app);
        String jobId = "srj_openapi_" + app.getId() + "_" + shortTrace(RequestContext.current().traceId());
        String rollbackPlanText = safeText(Objects.isNull(request) ? null : request.rollbackPlan(),
                "restore previous active OpenAPI secret version");
        SysSecretRotationJob job = new SysSecretRotationJob();
        job.setJobId(jobId);
        job.setSecretRefId(secretRef.getSecretRefId());
        job.setStatus("QUEUED");
        job.setNewVersion(newVersion);
        job.setRollbackPlan(toJson(Map.of("plan", rollbackPlanText, "restoreVersion", "previous_active")));
        job.setTraceId(RequestContext.current().traceId());
        job.setAuditLogId(auditLogId(RequestContext.current()));
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        rotationJobBaseService.saveEntity(job);
        AsyncTaskView task = asyncTaskService.upsert("task_openapi_secret_" + jobId,
                "OPENAPI_SECRET_ROTATION", idempotencyKey, AsyncTaskStatus.QUEUED, 0,
                true, false, true, null, null, null, 0, 0, 0L,
                Map.of("jobId", jobId, "secretRefId", secretRef.getSecretRefId(), "newVersion", newVersion));
        writeCallLog(app, "openapi.secret.rotate", "POST",
                "/api/v1/systems/" + systemId + "/openapi/apps/" + externalAppId + "/rotate-secret",
                "SUCCESS", idempotencyKey, null);
        OperationMetadata operation = operation("ROTATE_OPENAPI_SECRET", idempotencyKey, "QUEUED", null);
        return new OpenApiSecretRotationJobVO(jobId, toSecretRefVO(secretRef), "QUEUED", newVersion,
                rotationPhases(request, now), rollbackPlanText, task, operation, null, now);
    }

    /**
     * Query persisted OpenAPI call logs.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query object
     * @return call log page
     */
    public PageResult<OpenApiCallLogVO> callLogs(String systemId, PageRequest pageRequest,
                                                 OpenApiCallLogQuery query) {
        Long resolvedSystemId = parseRequiredId(systemId, "系统ID格式不正确");
        List<OpenApiCallLogVO> matched = callLogBaseService.list(new LambdaQueryWrapper<OpenapiCallLog>()
                        .eq(OpenapiCallLog::getSystemId, resolvedSystemId)
                        .orderByDesc(OpenapiCallLog::getCreatedAt))
                .stream()
                .map(this::toCallLogVO)
                .filter(log -> matchesCallLog(log, query))
                .toList();
        return page(matched, pageRequest);
    }

    /**
     * Search dynamic module records through the external OpenAPI ingress.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param appCode external app code
     * @param secretRefId secret reference id
     * @param request search request
     * @return record page
     */
    public PageResult<ExternalRecordRow> externalRecordSearch(String systemId, String moduleId, String appCode,
                                                              String secretRefId,
                                                              ExternalRecordSearchRequest request) {
        OpenapiApp app = authenticateExternalApp(systemId, appCode, secretRefId, "record.data.read");
        ModuleDefinition module = requireExternalModule(app, moduleId);
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        List<String> requestedFields = externalFields(request, fields);
        List<ExternalRecordRow> matched = recordBaseService.list(new LambdaQueryWrapper<ModuleDynamicRecord>()
                        .eq(ModuleDynamicRecord::getSystemId, app.getSystemId())
                        .eq(ModuleDynamicRecord::getTenantId, app.getTenantId())
                        .eq(ModuleDynamicRecord::getModuleId, module.getId())
                        .eq(ModuleDynamicRecord::getDeleted, DELETED_NO)
                        .orderByDesc(ModuleDynamicRecord::getUpdatedAt))
                .stream()
                .map(record -> toExternalRecordRow(record, fields, requestedFields))
                .filter(row -> externalRecordMatches(row, Objects.isNull(request) ? null : request.keyword()))
                .toList();
        writeCallLog(app, "record.data.read", "POST",
                "/openapi/v1/systems/" + systemId + "/modules/" + moduleId + "/records/search",
                "SUCCESS", null, null);
        return page(matched, new PageRequest(externalPageNo(request), externalPageSize(request),
                null, List.of(), List.of()));
    }

    /**
     * Return one dynamic module record through the external OpenAPI ingress.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param recordId record id
     * @param appCode external app code
     * @param secretRefId secret reference id
     * @return record row
     */
    public ExternalRecordRow externalRecordDetail(String systemId, String moduleId, String recordId,
                                                  String appCode, String secretRefId) {
        OpenapiApp app = authenticateExternalApp(systemId, appCode, secretRefId, "record.data.read");
        ModuleDefinition module = requireExternalModule(app, moduleId);
        ModuleDynamicRecord record = requireExternalRecord(app, module, recordId);
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        ExternalRecordRow row = toExternalRecordRow(record, fields, fields.stream()
                .map(ModuleFieldDefinition::getFieldCode).toList());
        writeCallLog(app, "record.data.read", "GET",
                "/openapi/v1/systems/" + systemId + "/modules/" + moduleId + "/records/" + recordId,
                "SUCCESS", null, null);
        return row;
    }

    /**
     * Create one dynamic module record through the external OpenAPI ingress.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param appCode external app code
     * @param secretRefId secret reference id
     * @param idempotencyKey idempotency key
     * @param request mutation request
     * @return mutation result
     */
    @Transactional(rollbackFor = Exception.class)
    public ExternalRecordMutationResult externalRecordCreate(String systemId, String moduleId, String appCode,
                                                             String secretRefId, String idempotencyKey,
                                                             ExternalRecordMutationRequest request) {
        OpenapiApp app = authenticateExternalApp(systemId, appCode, secretRefId, "record.data.write");
        ModuleDefinition module = requireExternalModule(app, moduleId);
        Map<String, Object> values = Objects.isNull(request) || Objects.isNull(request.fieldValues())
                ? Map.of() : request.fieldValues();
        LocalDateTime now = LocalDateTime.now();
        ModuleDynamicRecord record = new ModuleDynamicRecord();
        record.setSystemId(app.getSystemId());
        record.setTenantId(app.getTenantId());
        record.setModuleId(module.getId());
        record.setRecordNo("API" + System.currentTimeMillis());
        record.setTitle(safeText(valueAsString(values.get("title")), module.getModuleName()));
        record.setStatus(safeText(valueAsString(values.get("status")), "DRAFT"));
        record.setOwnerMemberId(null);
        record.setPermissionSnapshotId("openapi_scope_" + app.getId());
        record.setCreatedBy(0L);
        record.setUpdatedBy(0L);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(DELETED_NO);
        recordBaseService.saveEntity(record);
        saveExternalValues(record, fieldsForModule(module.getId()), values);
        String resolvedIdempotencyKey = safeText(idempotencyKey,
                "idem_openapi_create_" + shortTrace(RequestContext.current().traceId()));
        writeCallLog(app, "record.data.write", "POST",
                "/openapi/v1/systems/" + systemId + "/modules/" + moduleId + "/records",
                "SUCCESS", resolvedIdempotencyKey, null);
        return new ExternalRecordMutationResult(String.valueOf(record.getId()), "CREATED",
                resolvedIdempotencyKey, RequestContext.current().traceId(), now);
    }

    private LambdaQueryWrapper<OpenapiApp> appWrapper(Long systemId, OpenApiAppQuery query) {
        LambdaQueryWrapper<OpenapiApp> wrapper = new LambdaQueryWrapper<OpenapiApp>()
                .eq(OpenapiApp::getSystemId, systemId);
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.externalAppId())) {
                Long id = parseNullableId(query.externalAppId());
                if (Objects.nonNull(id)) {
                    wrapper.eq(OpenapiApp::getId, id);
                }
            }
            if (StringUtils.hasText(query.externalAppCode())) {
                wrapper.eq(OpenapiApp::getExternalAppCode, query.externalAppCode());
            }
            Integer status = parseStatus(query.status());
            if (Objects.nonNull(status)) {
                wrapper.eq(OpenapiApp::getStatus, status);
            }
            Long tenantId = parseNullableId(query.tenantId());
            if (Objects.nonNull(tenantId)) {
                wrapper.eq(OpenapiApp::getTenantId, tenantId);
            }
        }
        return wrapper;
    }

    private OpenapiApp requireApp(String systemId, String externalAppId) {
        Long resolvedSystemId = parseRequiredId(systemId, "系统ID格式不正确");
        Long id = parseRequiredId(externalAppId, "外部应用ID格式不正确");
        OpenapiApp app = appBaseService.getOne(new LambdaQueryWrapper<OpenapiApp>()
                .eq(OpenapiApp::getSystemId, resolvedSystemId)
                .eq(OpenapiApp::getId, id)
                .last("LIMIT 1"), false);
        if (Objects.isNull(app)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "外部应用不存在");
        }
        return app;
    }

    private OpenapiApp authenticateExternalApp(String systemId, String appCode, String secretRefId,
                                               String requiredScope) {
        Long resolvedSystemId = parseRequiredId(systemId, "系统ID格式不正确");
        if (!StringUtils.hasText(appCode) || !StringUtils.hasText(secretRefId)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED, "OpenAPI 应用编码和密钥引用不能为空");
        }
        OpenapiApp app = appBaseService.getOne(new LambdaQueryWrapper<OpenapiApp>()
                .eq(OpenapiApp::getSystemId, resolvedSystemId)
                .eq(OpenapiApp::getExternalAppCode, appCode)
                .eq(OpenapiApp::getStatus, ENABLED)
                .last("LIMIT 1"), false);
        if (Objects.isNull(app)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED, "OpenAPI 应用不存在或已停用");
        }
        if (!secretRefId.equals(app.getOpenapiSecretRefId())) {
            writeCallLog(app, requiredScope, "AUTH", "/openapi/v1/auth",
                    "FAILED", null, "SecretRef 不匹配");
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED, "OpenAPI 密钥引用不匹配");
        }
        SysSecretRef secretRef = secretRefBaseService.getOne(new LambdaQueryWrapper<SysSecretRef>()
                .eq(SysSecretRef::getSecretRefId, secretRefId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(secretRef) || "DISABLED".equalsIgnoreCase(secretRef.getRotationStatus())) {
            writeCallLog(app, requiredScope, "AUTH", "/openapi/v1/auth",
                    "FAILED", null, "SecretRef 不可用");
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED, "OpenAPI 密钥引用不可用");
        }
        if (!readScopes(app.getScopes()).contains(requiredScope)) {
            writeCallLog(app, requiredScope, "AUTH", "/openapi/v1/scope",
                    "FAILED", null, "未授予 scope: " + requiredScope);
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "OpenAPI 应用未授予该 scope");
        }
        return app;
    }

    private ModuleDefinition requireExternalModule(OpenapiApp app, String moduleId) {
        Long id = parseRequiredId(moduleId, "模块ID格式不正确");
        ModuleDefinition module = moduleBaseService.getOne(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getId, id)
                .eq(ModuleDefinition::getSystemId, app.getSystemId())
                .eq(ModuleDefinition::getTenantId, app.getTenantId())
                .eq(ModuleDefinition::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(module)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "模块不存在");
        }
        return module;
    }

    private ModuleDynamicRecord requireExternalRecord(OpenapiApp app, ModuleDefinition module, String recordId) {
        Long id = parseRequiredId(recordId, "记录ID格式不正确");
        ModuleDynamicRecord record = recordBaseService.getOne(new LambdaQueryWrapper<ModuleDynamicRecord>()
                .eq(ModuleDynamicRecord::getId, id)
                .eq(ModuleDynamicRecord::getSystemId, app.getSystemId())
                .eq(ModuleDynamicRecord::getTenantId, app.getTenantId())
                .eq(ModuleDynamicRecord::getModuleId, module.getId())
                .eq(ModuleDynamicRecord::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(record)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "记录不存在");
        }
        return record;
    }

    private List<ModuleFieldDefinition> fieldsForModule(Long moduleId) {
        return fieldBaseService.list(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)
                .eq(ModuleFieldDefinition::getStatus, ENABLED)
                .orderByAsc(ModuleFieldDefinition::getSortOrder)
                .orderByAsc(ModuleFieldDefinition::getId));
    }

    private List<String> externalFields(ExternalRecordSearchRequest request, List<ModuleFieldDefinition> fields) {
        if (Objects.isNull(request) || Objects.isNull(request.fields()) || request.fields().isEmpty()) {
            return fields.stream().map(ModuleFieldDefinition::getFieldCode).toList();
        }
        return request.fields();
    }

    private ExternalRecordRow toExternalRecordRow(ModuleDynamicRecord record, List<ModuleFieldDefinition> fields,
                                                  List<String> requestedFields) {
        Map<String, Object> fieldValues = new LinkedHashMap<>();
        Map<Long, ModuleDynamicValue> values = valuesByField(record.getId());
        for (ModuleFieldDefinition field : fields) {
            if (requestedFields.contains(field.getFieldCode())) {
                fieldValues.put(field.getFieldCode(), valueOf(values.get(field.getId())));
            }
        }
        return new ExternalRecordRow(String.valueOf(record.getId()), record.getRecordNo(), record.getTitle(),
                record.getStatus(), fieldValues, record.getUpdatedAt());
    }

    private boolean externalRecordMatches(ExternalRecordRow row, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        if (matchesContains(row.title(), lowerKeyword) || matchesContains(row.recordNo(), lowerKeyword)) {
            return true;
        }
        return row.fields().values().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .anyMatch(value -> value.toLowerCase().contains(lowerKeyword));
    }

    private boolean matchesContains(String value, String lowerKeyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(lowerKeyword);
    }

    private Map<Long, ModuleDynamicValue> valuesByField(Long recordId) {
        Map<Long, ModuleDynamicValue> values = new LinkedHashMap<>();
        for (ModuleDynamicValue value : valueBaseService.list(new LambdaQueryWrapper<ModuleDynamicValue>()
                .eq(ModuleDynamicValue::getRecordId, recordId))) {
            values.put(value.getFieldId(), value);
        }
        return values;
    }

    private Object valueOf(ModuleDynamicValue value) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (StringUtils.hasText(value.getValueJson())) {
            try {
                return objectMapper.readValue(value.getValueJson(), Object.class);
            } catch (Exception ex) {
                return value.getValueJson();
            }
        }
        if (Objects.nonNull(value.getValueNumber())) {
            return value.getValueNumber();
        }
        if (Objects.nonNull(value.getValueDatetime())) {
            return value.getValueDatetime();
        }
        return value.getValueText();
    }

    private void saveExternalValues(ModuleDynamicRecord record, List<ModuleFieldDefinition> fields,
                                    Map<String, Object> values) {
        LocalDateTime now = LocalDateTime.now();
        for (ModuleFieldDefinition field : fields) {
            if (!values.containsKey(field.getFieldCode())) {
                continue;
            }
            ModuleDynamicValue value = new ModuleDynamicValue();
            value.setRecordId(record.getId());
            value.setModuleId(record.getModuleId());
            value.setFieldId(field.getId());
            setExternalTypedValue(value, field, values.get(field.getFieldCode()));
            value.setCreatedAt(now);
            value.setUpdatedAt(now);
            valueBaseService.saveEntity(value);
        }
    }

    private void setExternalTypedValue(ModuleDynamicValue value, ModuleFieldDefinition field, Object rawValue) {
        if (Objects.isNull(rawValue)) {
            return;
        }
        String storageType = safeText(field.getStorageType(), "TEXT").toUpperCase();
        if ("NUMBER".equals(storageType)) {
            value.setValueNumber(toBigDecimal(rawValue));
            return;
        }
        if ("DATETIME".equals(storageType)) {
            value.setValueDatetime(toDateTime(rawValue));
            return;
        }
        if ("JSON".equals(storageType) || rawValue instanceof Map<?, ?> || rawValue instanceof List<?>) {
            value.setValueJson(toJson(rawValue));
            return;
        }
        value.setValueText(String.valueOf(rawValue));
    }

    private BigDecimal toBigDecimal(Object rawValue) {
        try {
            return new BigDecimal(String.valueOf(rawValue));
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "OpenAPI 数字字段格式不正确");
        }
    }

    private LocalDateTime toDateTime(Object rawValue) {
        String text = String.valueOf(rawValue);
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(text).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                        "OpenAPI 日期时间字段格式不正确");
            }
        }
    }

    private int externalPageNo(ExternalRecordSearchRequest request) {
        return Objects.isNull(request) || Objects.isNull(request.pageNo()) || request.pageNo() <= 0
                ? 1 : request.pageNo();
    }

    private int externalPageSize(ExternalRecordSearchRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.pageSize()) || request.pageSize() <= 0) {
            return 20;
        }
        return Math.min(request.pageSize(), 200);
    }

    private String valueAsString(Object value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private OpenApiAppVO toVO(OpenapiApp entity, OperationMetadata operation) {
        return new OpenApiAppVO(String.valueOf(entity.getId()), String.valueOf(entity.getSystemId()),
                String.valueOf(entity.getTenantId()), entity.getExternalAppCode(), entity.getAppName(),
                entity.getStatus(), secretRef(entity.getOpenapiSecretRefId()),
                scopeMetadata(readScopes(entity.getScopes())), entity.getCallbackUrl(),
                readRateLimit(entity.getRateLimit()), entity.getLastUsedAt(), operation,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private OpenApiCallLogVO toCallLogVO(OpenapiCallLog log) {
        OpenapiApp app = appBaseService.getById(log.getExternalAppId());
        return new OpenApiCallLogVO(String.valueOf(log.getId()), String.valueOf(log.getExternalAppId()),
                Objects.isNull(app) ? null : app.getExternalAppCode(), Objects.isNull(app) ? null : app.getAppName(),
                String.valueOf(log.getSystemId()), String.valueOf(log.getTenantId()), log.getScopeCode(),
                log.getRequestMethod(), log.getRequestPath(), log.getResult(), log.getIdempotencyKey(),
                log.getTraceId(), log.getFailureReason(), rateLimitSnapshot(0, 0), log.getCreatedAt());
    }

    private void writeCallLog(OpenapiApp app, String scope, String method, String path, String result,
                              String idempotencyKey, String failureReason) {
        OpenapiCallLog log = new OpenapiCallLog();
        log.setExternalAppId(app.getId());
        log.setSystemId(app.getSystemId());
        log.setTenantId(app.getTenantId());
        log.setScopeCode(scope);
        log.setRequestMethod(method);
        log.setRequestPath(path);
        log.setResult(result);
        log.setIdempotencyKey(idempotencyKey);
        log.setTraceId(RequestContext.current().traceId());
        log.setFailureReason(failureReason);
        log.setCreatedAt(LocalDateTime.now());
        callLogBaseService.saveEntity(log);
        app.setLastUsedAt(log.getCreatedAt());
        app.setUpdatedAt(log.getCreatedAt());
        appBaseService.updateById(app);
    }

    private SysSecretRef saveSecretRef(String secretRefId, String version, String status,
                                       String materialRef, LocalDateTime now) {
        SysSecretRef secretRef = secretRefBaseService.getOne(new LambdaQueryWrapper<SysSecretRef>()
                .eq(SysSecretRef::getSecretRefId, secretRefId)
                .last("LIMIT 1"), false);
        boolean created = Objects.isNull(secretRef);
        if (created) {
            secretRef = new SysSecretRef();
            secretRef.setSecretRefId(secretRefId);
            secretRef.setRefType("OPENAPI");
            secretRef.setDisplayName("OpenAPI application credential");
            secretRef.setCreatedAt(now);
        }
        secretRef.setVersionNo(version);
        secretRef.setExpiresAt(now.plusDays(90));
        secretRef.setRotationStatus(status);
        secretRef.setStorageRef(safeText(materialRef, "secret://openapi/" + secretRefId + "/" + version));
        secretRef.setUpdatedAt(now);
        if (created) {
            secretRefBaseService.saveEntity(secretRef);
        } else {
            secretRefBaseService.updateById(secretRef);
        }
        return secretRef;
    }

    private void disableSecretRef(String secretRefId, LocalDateTime now) {
        if (!StringUtils.hasText(secretRefId)) {
            return;
        }
        SysSecretRef secretRef = secretRefBaseService.getOne(new LambdaQueryWrapper<SysSecretRef>()
                .eq(SysSecretRef::getSecretRefId, secretRefId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(secretRef)) {
            return;
        }
        secretRef.setRotationStatus("DISABLED");
        secretRef.setUpdatedAt(now);
        secretRefBaseService.updateById(secretRef);
    }

    private OpenApiSecretRefVO secretRef(String secretRefId) {
        SysSecretRef secretRef = secretRefBaseService.getOne(new LambdaQueryWrapper<SysSecretRef>()
                .eq(SysSecretRef::getSecretRefId, secretRefId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(secretRef)) {
            return new OpenApiSecretRefVO(secretRefId, "OPENAPI", "v1", LocalDateTime.now().plusDays(90),
                    "MISSING", null, "OpenAPI application credential", "v1", LocalDateTime.now().plusDays(30));
        }
        return toSecretRefVO(secretRef);
    }

    private OpenApiSecretRefVO toSecretRefVO(SysSecretRef secretRef) {
        return new OpenApiSecretRefVO(secretRef.getSecretRefId(), secretRef.getRefType(), secretRef.getVersionNo(),
                secretRef.getExpiresAt(), secretRef.getRotationStatus(), secretRef.getLastUsedAt(),
                secretRef.getDisplayName(), secretRef.getVersionNo(),
                Objects.isNull(secretRef.getExpiresAt()) ? null : secretRef.getExpiresAt().minusDays(60));
    }

    private List<OpenApiSecretRotationPhaseVO> rotationPhases(OpenApiSecretRotationRequest request,
                                                              LocalDateTime now) {
        return List.of(
                new OpenApiSecretRotationPhaseVO("CREATE_NEW_VERSION", "QUEUED", null, now),
                new OpenApiSecretRotationPhaseVO("DUAL_WRITE_VALIDATE",
                        enabledStatus(Objects.isNull(request) || !Boolean.FALSE.equals(request.dualWriteValidation())),
                        null, now),
                new OpenApiSecretRotationPhaseVO("SWITCH_ACTIVE_VERSION",
                        enabledStatus(Objects.isNull(request) || !Boolean.FALSE.equals(request.switchAfterValidation())),
                        null, now),
                new OpenApiSecretRotationPhaseVO("DISABLE_OLD_VERSION", "PENDING", null, now),
                new OpenApiSecretRotationPhaseVO("ROLLBACK_ON_FAILURE", "READY", null, now));
    }

    private List<OpenApiScopeVO> scopeMetadata(List<String> scopeCodes) {
        LocalDateTime now = LocalDateTime.now();
        return safeList(scopeCodes, List.of("record.data.read")).stream()
                .map(scopeCode -> new OpenApiScopeVO(scopeCode, scopeName(scopeCode), scopeDescription(scopeCode),
                        true, "openapi." + scopeCode, "scope_v20260623_001",
                        now.minusHours(4), null))
                .toList();
    }

    private RateLimitMetadata defaultRateLimit() {
        return new RateLimitMetadata("APP_KEY", 60, 600, 120,
                "REJECT_WITH_CODE", ENABLED, "rl_v20260623_001", LocalDateTime.now());
    }

    private RateLimitMetadata safeRateLimit(RateLimitMetadata value) {
        if (Objects.isNull(value)) {
            return defaultRateLimit();
        }
        return new RateLimitMetadata(safeText(value.dimension(), "APP_KEY"),
                Objects.isNull(value.windowSeconds()) ? 60 : value.windowSeconds(),
                Objects.isNull(value.limit()) ? 600 : value.limit(),
                Objects.isNull(value.burstLimit()) ? 120 : value.burstLimit(),
                safeText(value.overflowPolicy(), "REJECT_WITH_CODE"),
                Objects.isNull(value.status()) ? ENABLED : value.status(),
                safeText(value.version(), "rl_v20260623_001"), LocalDateTime.now());
    }

    private RateLimitMetadata readRateLimit(String json) {
        if (!StringUtils.hasText(json)) {
            return defaultRateLimit();
        }
        try {
            return objectMapper.readValue(json, RateLimitMetadata.class);
        } catch (Exception ex) {
            return defaultRateLimit();
        }
    }

    private List<String> readScopes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> rateLimitSnapshot(int used, int limit) {
        return Map.of("dimension", "APP_KEY", "windowSeconds", 60, "used", used,
                "limit", limit, "status", limit > 0 && used >= limit ? "LIMITED" : "NORMAL");
    }

    private boolean matchesCallLog(OpenApiCallLogVO log, OpenApiCallLogQuery query) {
        if (Objects.isNull(query)) {
            return true;
        }
        return matchesText(log.externalAppId(), query.externalAppId())
                && matchesText(log.externalAppCode(), query.externalAppCode())
                && matchesText(log.scope(), query.scope())
                && matchesText(log.result(), query.result())
                && matchesText(log.traceId(), query.traceId())
                && matchesStartTime(log.createdAt(), query.startTime())
                && matchesEndTime(log.createdAt(), query.endTime());
    }

    private boolean matchesScope(List<OpenApiScopeVO> scopes, String scope) {
        if (!StringUtils.hasText(scope)) {
            return true;
        }
        return scopes.stream().anyMatch(item -> scope.equals(item.scopeCode()));
    }

    private boolean matchesKeyword(List<String> values, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        return values.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> value.toLowerCase().contains(lowerKeyword));
    }

    private boolean matchesText(String value, String expected) {
        return !StringUtils.hasText(expected) || expected.equals(value);
    }

    private boolean matchesStartTime(LocalDateTime value, String startTime) {
        if (!StringUtils.hasText(startTime)) {
            return true;
        }
        LocalDateTime parsed = parseTime(startTime, LocalDateTime.MIN);
        return !value.isBefore(parsed);
    }

    private boolean matchesEndTime(LocalDateTime value, String endTime) {
        if (!StringUtils.hasText(endTime)) {
            return true;
        }
        LocalDateTime parsed = parseTime(endTime, LocalDateTime.MAX);
        return !value.isAfter(parsed);
    }

    private <T> PageResult<T> page(List<T> records, PageRequest pageRequest) {
        int pageNo = Objects.isNull(pageRequest) || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = Objects.isNull(pageRequest) || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int from = Math.min((pageNo - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        return new PageResult<>(records.subList(from, to), pageNo, pageSize, records.size(), to < records.size());
    }

    private OperationMetadata operation(String operation, String idempotencyKey, String result,
                                        String disabledReason) {
        RequestContext context = RequestContext.current();
        return new OperationMetadata(operation, idempotencyKey, context.traceId(), auditLogId(context),
                result, disabledReason, LocalDateTime.now());
    }

    private Long resolveTenantId(Long systemId, String requestedTenantId) {
        Long tenantId = parseNullableId(requestedTenantId);
        if (Objects.nonNull(tenantId)) {
            return tenantId;
        }
        PlatTenant tenant = tenantBaseService.getOne(new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getSystemId, systemId)
                .eq(PlatTenant::getStatus, ENABLED)
                .eq(PlatTenant::getDeleted, DELETED_NO)
                .orderByAsc(PlatTenant::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(tenant)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "系统没有可用租户");
        }
        return tenant.getId();
    }

    private String resolveIdempotencyKey(String headerIdempotencyKey, String bodyIdempotencyKey, String operation) {
        if (StringUtils.hasText(headerIdempotencyKey)) {
            return headerIdempotencyKey;
        }
        if (StringUtils.hasText(bodyIdempotencyKey)) {
            return bodyIdempotencyKey;
        }
        return "idem_" + operation + "_" + shortTrace(RequestContext.current().traceId());
    }

    private Long parseRequiredId(String value, String message) {
        Long parsed = parseNullableId(value);
        if (Objects.isNull(parsed)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
        return parsed;
    }

    private Long parseNullableId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return Integer.valueOf(status);
        } catch (NumberFormatException ex) {
            return switch (status.toUpperCase()) {
                case "ENABLED", "ACTIVE" -> ENABLED;
                case "DISABLED", "INACTIVE" -> 0;
                default -> null;
            };
        }
    }

    private String normalizeCode(String value, String fallback) {
        String candidate = safeText(value, fallback);
        String normalized = candidate.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String scopeName(String scopeCode) {
        if ("record.data.write".equals(scopeCode)) {
            return "业务数据写入";
        }
        if ("workflow.approval.callback".equals(scopeCode)) {
            return "审批回调";
        }
        if ("message.delivery.callback".equals(scopeCode)) {
            return "消息投递回调";
        }
        return "业务数据读取";
    }

    private String scopeDescription(String scopeCode) {
        if ("record.data.write".equals(scopeCode)) {
            return "允许通过 OpenAPI 新增或更新授权模块记录，写入必须带幂等键和审计链路。";
        }
        if ("workflow.approval.callback".equals(scopeCode)) {
            return "允许外部系统接收审批状态回调，不暴露内部审批字段权限。";
        }
        if ("message.delivery.callback".equals(scopeCode)) {
            return "允许消息投递回调确认，记录 traceId 和投递结果。";
        }
        return "允许读取授权模块记录字段，字段脱敏由权限快照决定。";
    }

    private String enabledStatus(boolean enabled) {
        return enabled ? "PENDING" : "SKIPPED";
    }

    private LocalDateTime parseTime(String value, LocalDateTime fallback) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return parseOffsetTime(value, fallback);
        }
    }

    private LocalDateTime parseOffsetTime(String value, LocalDateTime fallback) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return parseDate(value, fallback);
        }
    }

    private LocalDateTime parseDate(String value, LocalDateTime fallback) {
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace(String traceId) {
        return Objects.isNull(traceId) || traceId.length() <= 8 ? "trace" : traceId.substring(traceId.length() - 8);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private <T> List<T> safeList(List<T> value, List<T> fallback) {
        return Objects.isNull(value) || value.isEmpty() ? fallback : List.copyOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "OpenAPI配置序列化失败");
        }
    }
}
