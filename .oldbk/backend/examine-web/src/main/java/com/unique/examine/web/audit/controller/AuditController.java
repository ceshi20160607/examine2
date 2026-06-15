package com.unique.examine.web.audit.controller;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.web.audit.bo.AuditLogQueryBO;
import com.unique.examine.web.audit.service.AuditOpsService;
import com.unique.examine.web.audit.vo.AuditLogDetailVO;
import com.unique.examine.web.audit.vo.AuditLogListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志只读接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
public class AuditController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuditOpsService auditOpsService;

    private final AuthSessionService authSessionService;

    /**
     * 查询系统操作审计日志。
     */
    @Operation(summary = "查询系统操作审计日志")
    @GetMapping("/api/v1/systems/{systemId}/audit/operation-logs")
    public PageResult<AuditLogListItemVO> systemOperationLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            AuditLogQueryBO queryBO) {
        validateLogin(authorization);
        return auditOpsService.systemOperationLogs(systemId, queryBO);
    }

    /**
     * 查询系统请求日志。
     */
    @Operation(summary = "查询系统请求日志")
    @GetMapping("/api/v1/systems/{systemId}/audit/request-logs")
    public PageResult<AuditLogListItemVO> systemRequestLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            AuditLogQueryBO queryBO) {
        validateLogin(authorization);
        return auditOpsService.systemRequestLogs(systemId, queryBO);
    }

    /**
     * 查询系统错误日志。
     */
    @Operation(summary = "查询系统错误日志")
    @GetMapping("/api/v1/systems/{systemId}/audit/error-logs")
    public PageResult<AuditLogListItemVO> systemErrorLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            AuditLogQueryBO queryBO) {
        validateLogin(authorization);
        return auditOpsService.systemErrorLogs(systemId, queryBO);
    }

    /**
     * 查询系统记录变更日志。
     */
    @Operation(summary = "查询系统记录变更日志")
    @GetMapping("/api/v1/systems/{systemId}/audit/record-changes")
    public PageResult<AuditLogListItemVO> systemRecordChanges(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            AuditLogQueryBO queryBO) {
        validateLogin(authorization);
        return auditOpsService.systemRecordChanges(systemId, queryBO);
    }

    /**
     * 查询系统 OpenAPI 调用日志。
     */
    @Operation(summary = "查询系统 OpenAPI 调用日志")
    @GetMapping("/api/v1/systems/{systemId}/audit/openapi-logs")
    public PageResult<AuditLogListItemVO> systemOpenApiLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long systemId,
            AuditLogQueryBO queryBO) {
        validateLogin(authorization);
        return auditOpsService.systemOpenApiLogs(systemId, queryBO);
    }

    /**
     * 查询系统审计详情。
     */
    @Operation(summary = "查询系统审计详情")
    @GetMapping("/api/v1/systems/{systemId}/audit/logs/{logId}")
    public AuditLogDetailVO systemAuditDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long logId) {
        validateLogin(authorization);
        return auditOpsService.systemAuditDetail(systemId, logId);
    }

    /**
     * 查询平台操作审计日志。
     */
    @Operation(summary = "查询平台操作审计日志")
    @GetMapping("/api/v1/platform/audit/operation-logs")
    public PageResult<AuditLogListItemVO> platformOperationLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, AuditLogQueryBO queryBO) {
        validateLogin(authorization);
        return auditOpsService.platformOperationLogs(queryBO);
    }

    /**
     * 查询平台审计详情。
     */
    @Operation(summary = "查询平台审计详情")
    @GetMapping("/api/v1/platform/audit/logs/{logId}")
    public AuditLogDetailVO platformAuditDetail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long logId) {
        validateLogin(authorization);
        return auditOpsService.platformAuditDetail(logId);
    }

    private void validateLogin(String authorization) {
        authSessionService.me(resolveBearer(authorization));
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
