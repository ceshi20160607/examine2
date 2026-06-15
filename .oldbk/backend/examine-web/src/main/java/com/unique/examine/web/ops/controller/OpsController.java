package com.unique.examine.web.ops.controller;

import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.web.audit.service.AuditOpsService;
import com.unique.examine.web.ops.vo.HealthCheckResultVO;
import com.unique.examine.web.ops.vo.MigrationStatusVO;
import com.unique.examine.web.ops.vo.OpsComponentStatusVO;
import com.unique.examine.web.ops.vo.OpsVersionVO;
import com.unique.examine.web.ops.vo.RuntimeConfigCheckVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维只读接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops")
public class OpsController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuditOpsService auditOpsService;

    private final AuthSessionService authSessionService;

    /**
     * 查询总体健康状态。
     */
    @Operation(summary = "查询总体健康状态")
    @GetMapping("/health")
    public HealthCheckResultVO health(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        validateLogin(authorization);
        return auditOpsService.health(requestId);
    }

    /**
     * 查询运行配置检查结果。
     */
    @Operation(summary = "查询运行配置检查结果")
    @GetMapping("/config-check")
    public List<RuntimeConfigCheckVO> configCheck(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return auditOpsService.configCheck();
    }

    /**
     * 查询版本信息。
     */
    @Operation(summary = "查询版本信息")
    @GetMapping("/version")
    public OpsVersionVO version(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        validateLogin(authorization);
        return auditOpsService.version(requestId);
    }

    /**
     * 查询 migration 状态。
     */
    @Operation(summary = "查询 migration 状态")
    @GetMapping("/migration/status")
    public List<MigrationStatusVO> migrationStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return auditOpsService.migrationStatus();
    }

    /**
     * 查询组件级健康状态。
     */
    @Operation(summary = "查询组件级健康状态")
    @GetMapping("/health/components")
    public List<OpsComponentStatusVO> healthComponents(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        validateLogin(authorization);
        return auditOpsService.healthComponents(requestId);
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
