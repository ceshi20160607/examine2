package com.unique.examine.web.audit.service.impl;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.ClientCredential;
import com.unique.examine.app.base.service.IAccessLogService;
import com.unique.examine.app.base.service.IClientCredentialService;
import com.unique.examine.app.base.service.IClientService;
import com.unique.examine.core.base.entity.ErrorLog;
import com.unique.examine.core.base.entity.MigrationStatus;
import com.unique.examine.core.base.entity.OperationLog;
import com.unique.examine.core.base.entity.RecordChange;
import com.unique.examine.core.base.entity.RequestLog;
import com.unique.examine.core.base.entity.RuntimeConfigCheck;
import com.unique.examine.core.base.service.IErrorLogService;
import com.unique.examine.core.base.service.IMigrationStatusService;
import com.unique.examine.core.base.service.IOperationLogService;
import com.unique.examine.core.base.service.IRecordChangeService;
import com.unique.examine.core.base.service.IRequestLogService;
import com.unique.examine.core.base.service.IRuntimeConfigCheckService;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.upload.base.entity.StorageConfig;
import com.unique.examine.upload.base.service.IStorageConfigService;
import com.unique.examine.web.audit.bo.AuditLogQueryBO;
import com.unique.examine.web.audit.service.AuditOpsService;
import com.unique.examine.web.audit.vo.AuditLogDetailVO;
import com.unique.examine.web.audit.vo.AuditLogListItemVO;
import com.unique.examine.web.audit.vo.BeforeAfterSnapshotVO;
import com.unique.examine.web.ops.vo.HealthCheckResultVO;
import com.unique.examine.web.ops.vo.MigrationStatusVO;
import com.unique.examine.web.ops.vo.OpsComponentStatusVO;
import com.unique.examine.web.ops.vo.OpsVersionVO;
import com.unique.examine.web.ops.vo.RuntimeConfigCheckVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 审计与运维只读服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuditOpsServiceImpl implements AuditOpsService {

    private static final int MAX_QUERY_DAYS = 31;

    private final IOperationLogService operationLogService;

    private final IRequestLogService requestLogService;

    private final IErrorLogService errorLogService;

    private final IRecordChangeService recordChangeService;

    private final IAccessLogService accessLogService;

    private final IRuntimeConfigCheckService runtimeConfigCheckService;

    private final IMigrationStatusService migrationStatusService;

    private final IStorageConfigService storageConfigService;

    private final IClientService clientService;

    private final IClientCredentialService credentialService;

    private final DataSource dataSource;

    private final ApplicationContext applicationContext;

    private final Environment environment;

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    @Override
    public PageResult<AuditLogListItemVO> systemOperationLogs(Long systemId, AuditLogQueryBO queryBO) {
        validateRange(queryBO);
        IPage<OperationLog> result = operationLogService.lambdaQuery()
                .eq(OperationLog::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getTenantId()), OperationLog::getTenantId, queryBO.getTenantId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), OperationLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getBizType()), OperationLog::getBizType, queryBO.getBizType())
                .eq(StringUtils.hasText(queryBO.getBizId()), OperationLog::getBizId, queryBO.getBizId())
                .eq(StringUtils.hasText(queryBO.getAction()), OperationLog::getAction, queryBO.getAction())
                .eq(StringUtils.hasText(queryBO.getResult()), OperationLog::getResult, queryBO.getResult())
                .ge(Objects.nonNull(queryBO.getStartAt()), OperationLog::getCreatedAt, queryBO.getStartAt())
                .le(Objects.nonNull(queryBO.getEndAt()), OperationLog::getCreatedAt, queryBO.getEndAt())
                .orderByDesc(OperationLog::getCreatedAt)
                .page(page(queryBO));
        return pageResult(result, result.getRecords().stream().map(this::operationItem).toList());
    }

    @Override
    public PageResult<AuditLogListItemVO> systemRequestLogs(Long systemId, AuditLogQueryBO queryBO) {
        validateRange(queryBO);
        IPage<RequestLog> result = requestLogService.lambdaQuery()
                .eq(RequestLog::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getTenantId()), RequestLog::getTenantId, queryBO.getTenantId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), RequestLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getResult()), RequestLog::getResult, queryBO.getResult())
                .ge(Objects.nonNull(queryBO.getStartAt()), RequestLog::getCreatedAt, queryBO.getStartAt())
                .le(Objects.nonNull(queryBO.getEndAt()), RequestLog::getCreatedAt, queryBO.getEndAt())
                .orderByDesc(RequestLog::getCreatedAt)
                .page(page(queryBO));
        return pageResult(result, result.getRecords().stream().map(this::requestItem).toList());
    }

    @Override
    public PageResult<AuditLogListItemVO> systemErrorLogs(Long systemId, AuditLogQueryBO queryBO) {
        validateRange(queryBO);
        IPage<ErrorLog> result = errorLogService.lambdaQuery()
                .eq(ErrorLog::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getTenantId()), ErrorLog::getTenantId, queryBO.getTenantId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), ErrorLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getErrorCode()), ErrorLog::getErrorCode, queryBO.getErrorCode())
                .ge(Objects.nonNull(queryBO.getStartAt()), ErrorLog::getCreatedAt, queryBO.getStartAt())
                .le(Objects.nonNull(queryBO.getEndAt()), ErrorLog::getCreatedAt, queryBO.getEndAt())
                .orderByDesc(ErrorLog::getCreatedAt)
                .page(page(queryBO));
        return pageResult(result, result.getRecords().stream().map(this::errorItem).toList());
    }

    @Override
    public PageResult<AuditLogListItemVO> systemRecordChanges(Long systemId, AuditLogQueryBO queryBO) {
        validateRange(queryBO);
        IPage<RecordChange> result = recordChangeService.lambdaQuery()
                .eq(RecordChange::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getTenantId()), RecordChange::getTenantId, queryBO.getTenantId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), RecordChange::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getBizId()), RecordChange::getRecordId, queryBO.getBizId())
                .ge(Objects.nonNull(queryBO.getStartAt()), RecordChange::getCreatedAt, queryBO.getStartAt())
                .le(Objects.nonNull(queryBO.getEndAt()), RecordChange::getCreatedAt, queryBO.getEndAt())
                .orderByDesc(RecordChange::getCreatedAt)
                .page(page(queryBO));
        return pageResult(result, result.getRecords().stream().map(this::recordChangeItem).toList());
    }

    @Override
    public PageResult<AuditLogListItemVO> systemOpenApiLogs(Long systemId, AuditLogQueryBO queryBO) {
        validateRange(queryBO);
        IPage<AccessLog> result = accessLogService.lambdaQuery()
                .eq(AccessLog::getSystemId, systemId)
                .eq(Objects.nonNull(queryBO.getTenantId()), AccessLog::getTenantId, queryBO.getTenantId())
                .eq(StringUtils.hasText(queryBO.getRequestId()), AccessLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getErrorCode()), AccessLog::getErrorCode, queryBO.getErrorCode())
                .ge(Objects.nonNull(queryBO.getStartAt()), AccessLog::getCreatedAt, queryBO.getStartAt())
                .le(Objects.nonNull(queryBO.getEndAt()), AccessLog::getCreatedAt, queryBO.getEndAt())
                .orderByDesc(AccessLog::getCreatedAt)
                .page(page(queryBO));
        return pageResult(result, result.getRecords().stream().map(this::openApiItem).toList());
    }

    @Override
    public PageResult<AuditLogListItemVO> platformOperationLogs(AuditLogQueryBO queryBO) {
        validateRange(queryBO);
        IPage<OperationLog> result = operationLogService.lambdaQuery()
                .isNull(OperationLog::getSystemId)
                .eq(StringUtils.hasText(queryBO.getRequestId()), OperationLog::getRequestId, queryBO.getRequestId())
                .eq(StringUtils.hasText(queryBO.getBizType()), OperationLog::getBizType, queryBO.getBizType())
                .eq(StringUtils.hasText(queryBO.getBizId()), OperationLog::getBizId, queryBO.getBizId())
                .eq(StringUtils.hasText(queryBO.getAction()), OperationLog::getAction, queryBO.getAction())
                .eq(StringUtils.hasText(queryBO.getResult()), OperationLog::getResult, queryBO.getResult())
                .ge(Objects.nonNull(queryBO.getStartAt()), OperationLog::getCreatedAt, queryBO.getStartAt())
                .le(Objects.nonNull(queryBO.getEndAt()), OperationLog::getCreatedAt, queryBO.getEndAt())
                .orderByDesc(OperationLog::getCreatedAt)
                .page(page(queryBO));
        return pageResult(result, result.getRecords().stream().map(this::operationItem).toList());
    }

    @Override
    public AuditLogDetailVO systemAuditDetail(Long systemId, Long logId) {
        OperationLog log = operationLogService.getById(logId);
        if (Objects.isNull(log) || !Objects.equals(log.getSystemId(), systemId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return operationDetail(log);
    }

    @Override
    public AuditLogDetailVO platformAuditDetail(Long logId) {
        OperationLog log = operationLogService.getById(logId);
        if (Objects.isNull(log) || Objects.nonNull(log.getSystemId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return operationDetail(log);
    }

    @Override
    public HealthCheckResultVO health(String requestId) {
        List<OpsComponentStatusVO> checks = healthComponents(requestId);
        String status = checks.stream().anyMatch(check -> "DOWN".equals(check.getStatus())) ? "DOWN"
                : checks.stream().anyMatch(check -> "WARN".equals(check.getStatus())) ? "WARN" : "UP";
        return HealthCheckResultVO.builder().requestId(requestId).status(status).checks(checks).build();
    }

    @Override
    public List<OpsComponentStatusVO> healthComponents(String requestId) {
        LocalDateTime now = LocalDateTime.now();
        List<OpsComponentStatusVO> checks = new ArrayList<>();
        checks.add(databaseCheck(now));
        checks.add(redisCheck(now));
        checks.add(uploadStorageCheck(now));
        checks.add(openApiPolicyCheck(now));
        checks.add(migrationCheck(now));
        checks.add(secretCheck(now));
        return checks;
    }

    @Override
    public List<RuntimeConfigCheckVO> configCheck() {
        return runtimeConfigCheckService.lambdaQuery()
                .orderByDesc(RuntimeConfigCheck::getCheckedAt)
                .last("limit 100")
                .list()
                .stream()
                .map(check -> RuntimeConfigCheckVO.builder()
                        .configKey(check.getConfigKey())
                        .component(check.getComponent())
                        .status(check.getStatus())
                        .message(check.getMessage())
                        .suggestion(check.getSuggestion())
                        .checkedAt(check.getCheckedAt())
                        .build())
                .toList();
    }

    @Override
    public OpsVersionVO version(String requestId) {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        return OpsVersionVO.builder()
                .application(environment.getProperty("spring.application.name", "unexamine"))
                .version(Objects.nonNull(buildProperties) ? buildProperties.getVersion() : "0.0.1-SNAPSHOT")
                .buildTime(Objects.nonNull(buildProperties) ? buildProperties.getTime().toString() : null)
                .requestId(requestId)
                .build();
    }

    @Override
    public List<MigrationStatusVO> migrationStatus() {
        return migrationStatusService.lambdaQuery()
                .orderByDesc(MigrationStatus::getInstalledAt)
                .list()
                .stream()
                .map(this::migrationVO)
                .toList();
    }

    private AuditLogDetailVO operationDetail(OperationLog log) {
        List<BeforeAfterSnapshotVO> snapshots = recordChangeService.lambdaQuery()
                .eq(RecordChange::getRequestId, log.getRequestId())
                .list()
                .stream()
                .map(change -> BeforeAfterSnapshotVO.builder()
                        .beforeSnapshotJson(change.getBeforeSnapshotJson())
                        .afterSnapshotJson(change.getAfterSnapshotJson())
                        .build())
                .toList();
        return AuditLogDetailVO.builder()
                .logId(log.getId())
                .logType("OPERATION")
                .requestId(log.getRequestId())
                .traceId(log.getTraceId())
                .systemId(log.getSystemId())
                .tenantId(log.getTenantId())
                .operatorType(log.getOperatorType())
                .operatorId(log.getOperatorId())
                .operatorName(log.getOperatorName())
                .module(log.getModule())
                .bizType(log.getBizType())
                .bizId(log.getBizId())
                .action(log.getAction())
                .result(log.getResult())
                .errorCode(log.getErrorCode())
                .summary(log.getSummary())
                .snapshots(snapshots)
                .createdAt(log.getCreatedAt())
                .build();
    }

    private OpsComponentStatusVO databaseCheck(LocalDateTime now) {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return component("DB", valid ? "UP" : "DOWN", valid ? "SUCCESS" : "FAILED",
                    valid ? "数据库连接正常" : "数据库连接不可用", valid ? null : "检查数据库地址、账号、连接池和网络", now);
        } catch (Exception e) {
            return component("DB", "DOWN", "FAILED", "数据库连接失败: " + e.getMessage(),
                    "检查数据库地址、账号、连接池和网络", now);
        }
    }

    private OpsComponentStatusVO redisCheck(LocalDateTime now) {
        boolean configured = applicationContext.containsBean("redisConnectionFactory");
        return component("REDIS", configured ? "UP" : "WARN", configured ? "SUCCESS" : "FAILED",
                configured ? "Redis 连接工厂已配置" : "当前未配置 Redis 连接工厂",
                configured ? null : "如后续启用分布式缓存或限流，请补充 Redis 配置", now);
    }

    private OpsComponentStatusVO uploadStorageCheck(LocalDateTime now) {
        StorageConfig config = storageConfigService.lambdaQuery()
                .eq(StorageConfig::getDefaultFlag, (byte) 1)
                .eq(StorageConfig::getDeleted, (byte) 0)
                .last("limit 1")
                .one();
        boolean ok = Objects.nonNull(config) && "ENABLED".equals(config.getStatus())
                && StringUtils.hasText(config.getRootPath());
        return component("UPLOAD_STORAGE", ok ? "UP" : "WARN", ok ? "SUCCESS" : "FAILED",
                ok ? "默认文件存储配置可用" : "默认文件存储配置缺失或未启用",
                ok ? null : "检查 un_upload_storage_config 默认配置、状态和 root_path", now);
    }

    private OpsComponentStatusVO openApiPolicyCheck(LocalDateTime now) {
        long enabledClients = clientService.lambdaQuery().eq(Client::getStatus, "ENABLED")
                .eq(Client::getDeleted, (byte) 0).count();
        long activeCredentials = credentialService.lambdaQuery().eq(ClientCredential::getStatus, "ACTIVE").count();
        boolean ok = enabledClients == 0 || activeCredentials >= enabledClients;
        return component("OPENAPI_POLICY", ok ? "UP" : "WARN", ok ? "SUCCESS" : "FAILED",
                ok ? "OpenAPI 客户端与有效凭证数量匹配" : "存在启用客户端缺少有效凭证",
                ok ? null : "检查客户端凭证轮换状态和 ACTIVE 凭证", now);
    }

    private OpsComponentStatusVO migrationCheck(LocalDateTime now) {
        List<MigrationStatus> statuses = migrationStatusService.lambdaQuery().list();
        if (statuses.isEmpty()) {
            return component("MIGRATION", "WARN", "FAILED", "未查询到 migration 状态记录",
                    "确认 init.sql 或 migration 初始化流程已写入 un_sys_migration_status", now);
        }
        boolean failed = statuses.stream().anyMatch(status -> "FAILED".equals(status.getStatus()));
        return component("MIGRATION", failed ? "DOWN" : "UP", failed ? "FAILED" : "SUCCESS",
                failed ? "存在失败的 migration" : "migration 状态正常",
                failed ? "检查失败 migration 的 error_message 并重新执行修复流程" : null, now);
    }

    private OpsComponentStatusVO secretCheck(LocalDateTime now) {
        long missingSecret = credentialService.lambdaQuery()
                .eq(ClientCredential::getStatus, "ACTIVE")
                .isNull(ClientCredential::getSignSecretEnc)
                .count();
        return component("SECRET", missingSecret == 0 ? "UP" : "DOWN", missingSecret == 0 ? "SUCCESS" : "FAILED",
                missingSecret == 0 ? "OpenAPI 活跃凭证密钥引用完整" : "存在活跃凭证缺少签名密钥密文",
                missingSecret == 0 ? null : "轮换缺失密钥的 OpenAPI 凭证", now);
    }

    private AuditLogListItemVO operationItem(OperationLog log) {
        return AuditLogListItemVO.builder().logId(log.getId()).logType("OPERATION").requestId(log.getRequestId())
                .systemId(log.getSystemId()).tenantId(log.getTenantId()).operatorId(log.getOperatorId())
                .module(log.getModule()).bizType(log.getBizType()).bizId(log.getBizId()).action(log.getAction())
                .result(log.getResult()).errorCode(log.getErrorCode()).createdAt(log.getCreatedAt()).build();
    }

    private AuditLogListItemVO requestItem(RequestLog log) {
        return AuditLogListItemVO.builder().logId(log.getId()).logType("REQUEST").requestId(log.getRequestId())
                .systemId(log.getSystemId()).tenantId(log.getTenantId()).operatorId(log.getOperatorId())
                .module(log.getModule()).action(log.getMethod() + " " + log.getPath()).result(log.getResult())
                .statusCode(log.getHttpStatus()).durationMs(log.getDurationMs()).createdAt(log.getCreatedAt()).build();
    }

    private AuditLogListItemVO errorItem(ErrorLog log) {
        return AuditLogListItemVO.builder().logId(log.getId()).logType("ERROR").requestId(log.getRequestId())
                .systemId(log.getSystemId()).tenantId(log.getTenantId()).bizType(log.getBizType())
                .bizId(log.getBizId()).result("FAILED").errorCode(log.getErrorCode()).createdAt(log.getCreatedAt())
                .build();
    }

    private AuditLogListItemVO recordChangeItem(RecordChange log) {
        return AuditLogListItemVO.builder().logId(log.getId()).logType("RECORD_CHANGE").requestId(log.getRequestId())
                .systemId(log.getSystemId()).tenantId(log.getTenantId()).bizType("RECORD")
                .bizId(String.valueOf(log.getRecordId())).action(log.getChangeType()).result("SUCCESS")
                .createdAt(log.getCreatedAt()).build();
    }

    private AuditLogListItemVO openApiItem(AccessLog log) {
        return AuditLogListItemVO.builder().logId(log.getId()).logType("OPENAPI").requestId(log.getRequestId())
                .systemId(log.getSystemId()).tenantId(log.getTenantId()).operatorId(String.valueOf(log.getClientId()))
                .module("OPENAPI").bizType(log.getBizType()).bizId(log.getBizId()).action(log.getApiId())
                .result(log.getResult()).statusCode(log.getHttpStatus()).errorCode(log.getErrorCode())
                .durationMs(log.getDurationMs()).createdAt(log.getCreatedAt()).build();
    }

    private MigrationStatusVO migrationVO(MigrationStatus status) {
        return MigrationStatusVO.builder().version(status.getVersion()).description(status.getDescription())
                .status(status.getStatus()).checksum(status.getChecksum()).installedAt(status.getInstalledAt())
                .executionTimeMs(status.getExecutionTimeMs()).errorMessage(status.getErrorMessage()).build();
    }

    private static OpsComponentStatusVO component(String component, String status, String result, String message,
            String suggestion, LocalDateTime checkedAt) {
        return OpsComponentStatusVO.builder().component(component).status(status).result(result).message(message)
                .suggestion(suggestion).checkedAt(checkedAt).build();
    }

    private static <T> PageResult<T> pageResult(IPage<?> page, List<T> records) {
        return PageResult.<T>builder().records(records).total(page.getTotal()).pageNo(page.getCurrent())
                .pageSize(page.getSize()).hasNext(page.getCurrent() * page.getSize() < page.getTotal()).build();
    }

    private static <T> Page<T> page(AuditLogQueryBO queryBO) {
        return new Page<>(safePageNo(queryBO.getPageNo()), safePageSize(queryBO.getPageSize()));
    }

    private static long safePageNo(Long pageNo) {
        return Objects.isNull(pageNo) || pageNo < 1 ? 1L : pageNo;
    }

    private static long safePageSize(Long pageSize) {
        if (Objects.isNull(pageSize) || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200L);
    }

    private static void validateRange(AuditLogQueryBO queryBO) {
        if (Objects.nonNull(queryBO.getStartAt()) && Objects.nonNull(queryBO.getEndAt())
                && Duration.between(queryBO.getStartAt(), queryBO.getEndAt()).toDays() > MAX_QUERY_DAYS) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "审计查询时间范围不能超过 31 天");
        }
    }
}
