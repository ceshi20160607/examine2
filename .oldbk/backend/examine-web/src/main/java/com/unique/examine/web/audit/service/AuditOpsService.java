package com.unique.examine.web.audit.service;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.web.audit.bo.AuditLogQueryBO;
import com.unique.examine.web.audit.vo.AuditLogDetailVO;
import com.unique.examine.web.audit.vo.AuditLogListItemVO;
import com.unique.examine.web.ops.vo.HealthCheckResultVO;
import com.unique.examine.web.ops.vo.MigrationStatusVO;
import com.unique.examine.web.ops.vo.OpsComponentStatusVO;
import com.unique.examine.web.ops.vo.OpsVersionVO;
import com.unique.examine.web.ops.vo.RuntimeConfigCheckVO;

/**
 * 审计与运维只读服务。
 */
public interface AuditOpsService {

    /**
     * 查询系统操作审计日志。
     */
    PageResult<AuditLogListItemVO> systemOperationLogs(Long systemId, AuditLogQueryBO queryBO);

    /**
     * 查询系统请求日志。
     */
    PageResult<AuditLogListItemVO> systemRequestLogs(Long systemId, AuditLogQueryBO queryBO);

    /**
     * 查询系统错误日志。
     */
    PageResult<AuditLogListItemVO> systemErrorLogs(Long systemId, AuditLogQueryBO queryBO);

    /**
     * 查询系统记录变更日志。
     */
    PageResult<AuditLogListItemVO> systemRecordChanges(Long systemId, AuditLogQueryBO queryBO);

    /**
     * 查询系统 OpenAPI 调用日志。
     */
    PageResult<AuditLogListItemVO> systemOpenApiLogs(Long systemId, AuditLogQueryBO queryBO);

    /**
     * 查询平台操作审计日志。
     */
    PageResult<AuditLogListItemVO> platformOperationLogs(AuditLogQueryBO queryBO);

    /**
     * 查询系统审计详情。
     */
    AuditLogDetailVO systemAuditDetail(Long systemId, Long logId);

    /**
     * 查询平台审计详情。
     */
    AuditLogDetailVO platformAuditDetail(Long logId);

    /**
     * 查询总体健康状态。
     */
    HealthCheckResultVO health(String requestId);

    /**
     * 查询组件健康状态。
     */
    List<OpsComponentStatusVO> healthComponents(String requestId);

    /**
     * 查询运行配置检查结果。
     */
    List<RuntimeConfigCheckVO> configCheck();

    /**
     * 查询版本信息。
     */
    OpsVersionVO version(String requestId);

    /**
     * 查询 migration 状态。
     */
    List<MigrationStatusVO> migrationStatus();
}
