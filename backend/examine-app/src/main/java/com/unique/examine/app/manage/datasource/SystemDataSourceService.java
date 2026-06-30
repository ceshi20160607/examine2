package com.unique.examine.app.manage.datasource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.app.base.entity.SystemDataSource;
import com.unique.examine.app.base.service.SystemDataSourceBaseService;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceCheckItem;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceCheckResult;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceQuery;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceSaveRequest;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceVO;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.OperationMetadata;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemDataSourceService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SystemDataSourceBaseService dataSourceBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final ObjectMapper objectMapper;

    public SystemDataSourceService(SystemDataSourceBaseService dataSourceBaseService,
                                   PlatTenantBaseService tenantBaseService,
                                   ObjectMapper objectMapper) {
        this.dataSourceBaseService = dataSourceBaseService;
        this.tenantBaseService = tenantBaseService;
        this.objectMapper = objectMapper;
    }

    public PageResult<DataSourceVO> list(String systemId, PageRequest pageRequest, DataSourceQuery query) {
        Long resolvedSystemId = parseRequiredId(systemId, "Invalid system id.");
        Long tenantId = Objects.isNull(query) ? null : parseNullableId(query.tenantId());
        LambdaQueryWrapper<SystemDataSource> wrapper = new LambdaQueryWrapper<SystemDataSource>()
                .eq(SystemDataSource::getSystemId, resolvedSystemId)
                .eq(Objects.nonNull(tenantId), SystemDataSource::getTenantId, tenantId)
                .eq(SystemDataSource::getDeleted, DELETED_NO)
                .orderByDesc(SystemDataSource::getUpdatedAt);
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.sourceType())) {
                wrapper.eq(SystemDataSource::getSourceType, query.sourceType().toUpperCase());
            }
            Integer status = parseStatus(query.status());
            if (Objects.nonNull(status)) {
                wrapper.eq(SystemDataSource::getStatus, status);
            }
        }
        String keyword = Objects.isNull(query) ? null : query.keyword();
        List<DataSourceVO> matched = dataSourceBaseService.list(wrapper).stream()
                .map(entity -> toVO(entity, null))
                .filter(item -> matchesKeyword(List.of(item.sourceCode(), item.sourceName(), item.sourceType()), keyword))
                .toList();
        return page(matched, pageRequest);
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceVO create(String systemId, String headerIdempotencyKey, DataSourceSaveRequest request) {
        Long resolvedSystemId = parseRequiredId(systemId, "Invalid system id.");
        Long tenantId = resolveTenantId(resolvedSystemId, Objects.isNull(request) ? null : request.tenantId());
        String sourceCode = normalizeCode(Objects.isNull(request) ? null : request.sourceCode(),
                "ds_" + shortTrace(RequestContext.current().traceId()));
        SystemDataSource existing = dataSourceBaseService.getOne(new LambdaQueryWrapper<SystemDataSource>()
                .eq(SystemDataSource::getSystemId, resolvedSystemId)
                .eq(SystemDataSource::getTenantId, tenantId)
                .eq(SystemDataSource::getSourceCode, sourceCode)
                .eq(SystemDataSource::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "data_source_create");
        if (Objects.nonNull(existing)) {
            return toVO(existing, operation("CREATE_DATA_SOURCE", idempotencyKey, "REUSED_EXISTING", null));
        }
        LocalDateTime now = LocalDateTime.now();
        SystemDataSource entity = new SystemDataSource();
        entity.setSystemId(resolvedSystemId);
        entity.setTenantId(tenantId);
        entity.setSourceCode(sourceCode);
        entity.setSourceName(requiredText(Objects.isNull(request) ? null : request.sourceName(), "sourceName"));
        entity.setSourceType(normalizeSourceType(Objects.isNull(request) ? null : request.sourceType()));
        entity.setConnectionConfig(toJson(defaultConnectionConfig(entity.getSourceType(),
                Objects.isNull(request) ? null : request.connectionConfig())));
        entity.setAuthConfig(toJson(safeMap(Objects.isNull(request) ? null : request.authConfig())));
        entity.setSyncConfig(toJson(safeMap(Objects.isNull(request) ? null : request.syncConfig())));
        entity.setDesensitizeConfig(toJson(safeMap(Objects.isNull(request) ? null : request.desensitizeConfig())));
        entity.setStatus(Objects.isNull(request) || Objects.isNull(request.status()) ? ENABLED : request.status());
        entity.setPublishStatus("DRAFT");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(DELETED_NO);
        dataSourceBaseService.saveEntity(entity);
        return toVO(entity, operation("CREATE_DATA_SOURCE", idempotencyKey, "CREATED", null));
    }

    public DataSourceVO detail(String systemId, String dataSourceId) {
        return toVO(requireDataSource(systemId, dataSourceId), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceVO update(String systemId, String dataSourceId, String headerIdempotencyKey,
                               DataSourceSaveRequest request) {
        SystemDataSource entity = requireDataSource(systemId, dataSourceId);
        if (Objects.nonNull(request)) {
            if (StringUtils.hasText(request.sourceName())) {
                entity.setSourceName(request.sourceName());
            }
            if (StringUtils.hasText(request.sourceType())) {
                entity.setSourceType(normalizeSourceType(request.sourceType()));
            }
            if (Objects.nonNull(request.connectionConfig())) {
                entity.setConnectionConfig(toJson(defaultConnectionConfig(entity.getSourceType(),
                        request.connectionConfig())));
            }
            if (Objects.nonNull(request.authConfig())) {
                entity.setAuthConfig(toJson(safeMap(request.authConfig())));
            }
            if (Objects.nonNull(request.syncConfig())) {
                entity.setSyncConfig(toJson(safeMap(request.syncConfig())));
            }
            if (Objects.nonNull(request.desensitizeConfig())) {
                entity.setDesensitizeConfig(toJson(safeMap(request.desensitizeConfig())));
            }
            if (Objects.nonNull(request.status())) {
                entity.setStatus(request.status());
            }
        }
        entity.setPublishStatus("DRAFT");
        entity.setUpdatedAt(LocalDateTime.now());
        dataSourceBaseService.updateById(entity);
        String idempotencyKey = resolveIdempotencyKey(headerIdempotencyKey,
                Objects.isNull(request) ? null : request.idempotencyKey(), "data_source_update");
        return toVO(entity, operation("UPDATE_DATA_SOURCE", idempotencyKey, "UPDATED", null));
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceCheckResult connectionCheck(String systemId, String dataSourceId) {
        SystemDataSource entity = requireDataSource(systemId, dataSourceId);
        LocalDateTime checkedAt = LocalDateTime.now();
        List<DataSourceCheckItem> items = validate(entity, false);
        boolean passed = items.stream().allMatch(DataSourceCheckItem::passed);
        entity.setLastCheckStatus(passed ? "PASSED" : "FAILED");
        entity.setLastCheckTraceId(RequestContext.current().traceId());
        entity.setLastCheckedAt(checkedAt);
        entity.setUpdatedAt(checkedAt);
        dataSourceBaseService.updateById(entity);
        return new DataSourceCheckResult(String.valueOf(entity.getId()), passed, items,
                RequestContext.current().traceId(), auditLogId(RequestContext.current()), checkedAt, null);
    }

    public DataSourceCheckResult publishCheck(String systemId, String dataSourceId) {
        SystemDataSource entity = requireDataSource(systemId, dataSourceId);
        LocalDateTime checkedAt = LocalDateTime.now();
        List<DataSourceCheckItem> items = validate(entity, true);
        boolean passed = items.stream().allMatch(DataSourceCheckItem::passed);
        String targetVersion = passed ? "ds_" + checkedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) : null;
        return new DataSourceCheckResult(String.valueOf(entity.getId()), passed, items,
                RequestContext.current().traceId(), auditLogId(RequestContext.current()), checkedAt, targetVersion);
    }

    private List<DataSourceCheckItem> validate(SystemDataSource entity, boolean forPublish) {
        List<DataSourceCheckItem> items = new ArrayList<>();
        items.add(item("IDENTITY", "Data source identity", StringUtils.hasText(entity.getSourceCode())
                && StringUtils.hasText(entity.getSourceName()), "sourceCode and sourceName are required."));
        items.add(item("TYPE", "Data source type", List.of("SYSTEM_INTERNAL", "EXTERNAL_API", "DATABASE_DIRECT")
                .contains(entity.getSourceType()), "sourceType must be supported."));
        items.add(item("STATUS", "Enabled status", Objects.equals(entity.getStatus(), ENABLED),
                "Only enabled data sources can pass checks."));
        Map<String, Object> connection = readMap(entity.getConnectionConfig());
        if ("SYSTEM_INTERNAL".equals(entity.getSourceType())) {
            items.add(item("SYSTEM_INTERNAL_SCOPE", "Internal module scope",
                    connection.containsKey("moduleScope") || connection.containsKey("sourceModuleCodes"),
                    "SYSTEM_INTERNAL requires moduleScope or sourceModuleCodes."));
        } else if ("EXTERNAL_API".equals(entity.getSourceType())) {
            items.add(item("EXTERNAL_API_ENDPOINT", "External API endpoint",
                    StringUtils.hasText(valueAsString(connection.get("endpointUrl"))),
                    "EXTERNAL_API requires endpointUrl. No outbound request is made by this check."));
        } else if ("DATABASE_DIRECT".equals(entity.getSourceType())) {
            items.add(item("DATABASE_SECRET_REF", "Database secret reference",
                    StringUtils.hasText(valueAsString(connection.get("connectionSecretRef")))
                            || StringUtils.hasText(valueAsString(connection.get("jdbcUrlSecretRef"))),
                    "DATABASE_DIRECT requires connectionSecretRef or jdbcUrlSecretRef. No direct database login is made."));
        }
        if (forPublish) {
            items.add(item("LAST_CONNECTION_CHECK", "Last connection check",
                    "PASSED".equals(entity.getLastCheckStatus()),
                    "Run a successful connection check before publish check."));
        }
        return items;
    }

    private DataSourceCheckItem item(String code, String name, boolean passed, String message) {
        return new DataSourceCheckItem(code, name, passed ? "INFO" : "ERROR", passed,
                passed ? "OK" : message);
    }

    private SystemDataSource requireDataSource(String systemId, String dataSourceId) {
        Long resolvedSystemId = parseRequiredId(systemId, "Invalid system id.");
        Long resolvedDataSourceId = parseRequiredId(dataSourceId, "Invalid data source id.");
        SystemDataSource entity = dataSourceBaseService.getOne(new LambdaQueryWrapper<SystemDataSource>()
                .eq(SystemDataSource::getId, resolvedDataSourceId)
                .eq(SystemDataSource::getSystemId, resolvedSystemId)
                .eq(SystemDataSource::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(entity)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Data source not found.");
        }
        return entity;
    }

    private DataSourceVO toVO(SystemDataSource entity, OperationMetadata operation) {
        return new DataSourceVO(String.valueOf(entity.getId()), String.valueOf(entity.getSystemId()),
                String.valueOf(entity.getTenantId()), entity.getSourceCode(), entity.getSourceName(),
                entity.getSourceType(), readMap(entity.getConnectionConfig()), readMap(entity.getAuthConfig()),
                readMap(entity.getSyncConfig()), readMap(entity.getDesensitizeConfig()), entity.getStatus(),
                entity.getPublishStatus(), entity.getPublishedVersion(), entity.getLastCheckStatus(),
                entity.getLastCheckTraceId(), entity.getLastCheckedAt(), operation,
                entity.getCreatedAt(), entity.getUpdatedAt());
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
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "System has no enabled tenant.");
        }
        return tenant.getId();
    }

    private Map<String, Object> defaultConnectionConfig(String sourceType, Map<String, Object> input) {
        Map<String, Object> value = new LinkedHashMap<>(safeMap(input));
        if ("SYSTEM_INTERNAL".equals(sourceType) && value.isEmpty()) {
            value.put("moduleScope", List.of("*"));
        }
        return value;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return Objects.isNull(value) ? Map.of() : new LinkedHashMap<>(value);
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private <T> PageResult<T> page(List<T> records, PageRequest pageRequest) {
        int pageNo = Objects.isNull(pageRequest) || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = Objects.isNull(pageRequest) || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int from = Math.min((pageNo - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        return new PageResult<>(records.subList(from, to), pageNo, pageSize, records.size(), to < records.size());
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

    private OperationMetadata operation(String operation, String idempotencyKey, String result,
                                        String disabledReason) {
        RequestContext context = RequestContext.current();
        return new OperationMetadata(operation, idempotencyKey, context.traceId(), auditLogId(context),
                result, disabledReason, LocalDateTime.now());
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

    private String normalizeSourceType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "SYSTEM_INTERNAL";
        if (!List.of("SYSTEM_INTERNAL", "EXTERNAL_API", "DATABASE_DIRECT").contains(normalized)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Unsupported data source type.");
        }
        return normalized;
    }

    private String normalizeCode(String value, String fallback) {
        String candidate = StringUtils.hasText(value) ? value : fallback;
        String normalized = candidate.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String requiredText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, field + " is required.");
        }
        return value.trim();
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "Data source config serialization failed.");
        }
    }

    private String valueAsString(Object value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace(String traceId) {
        return Objects.isNull(traceId) || traceId.length() <= 8 ? "trace" : traceId.substring(traceId.length() - 8);
    }
}
