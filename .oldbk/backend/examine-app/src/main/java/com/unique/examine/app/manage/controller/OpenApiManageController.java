package com.unique.examine.app.manage.controller;

import java.util.List;

import com.unique.examine.app.manage.bo.OpenApiAccessLogQueryBO;
import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.bo.OpenApiClientStatusBO;
import com.unique.examine.app.manage.bo.OpenApiIpWhitelistBO;
import com.unique.examine.app.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.app.manage.service.OpenApiManageService;
import com.unique.examine.app.manage.vo.OpenApiAccessLogVO;
import com.unique.examine.app.manage.vo.OpenApiClientDetailVO;
import com.unique.examine.app.manage.vo.OpenApiCredentialOnceVO;
import com.unique.examine.app.manage.vo.OpenApiScopeCatalogVO;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAPI 客户端管理接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/openapi")
public class OpenApiManageController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OpenApiManageService openApiManageService;

    private final AuthSessionService authSessionService;

    /**
     * 查询 OpenAPI 客户端。
     */
    @Operation(summary = "查询 OpenAPI 客户端")
    @GetMapping("/clients")
    public List<OpenApiClientDetailVO> listClients(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        validateLogin(authorization);
        return openApiManageService.listClients(systemId, tenantId, keyword, status);
    }

    /**
     * 创建 OpenAPI 客户端。
     */
    @Operation(summary = "创建 OpenAPI 客户端")
    @PostMapping("/clients")
    public OpenApiClientDetailVO createClient(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody OpenApiClientSaveBO saveBO) {
        validateLogin(authorization);
        return openApiManageService.createClient(systemId, saveBO);
    }

    /**
     * 更新 OpenAPI 客户端。
     */
    @Operation(summary = "更新 OpenAPI 客户端")
    @PutMapping("/clients/{clientId}")
    public OpenApiClientDetailVO updateClient(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long clientId, @Valid @RequestBody OpenApiClientSaveBO saveBO) {
        validateLogin(authorization);
        return openApiManageService.updateClient(systemId, clientId, saveBO);
    }

    /**
     * 变更 OpenAPI 客户端状态。
     */
    @Operation(summary = "变更 OpenAPI 客户端状态")
    @PatchMapping("/clients/{clientId}/status")
    public OpenApiClientDetailVO changeStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long clientId,
            @Valid @RequestBody OpenApiClientStatusBO statusBO) {
        validateLogin(authorization);
        return openApiManageService.changeStatus(systemId, clientId, statusBO);
    }

    /**
     * 轮换 OpenAPI 凭证。
     */
    @Operation(summary = "轮换 OpenAPI 凭证")
    @PostMapping("/clients/{clientId}/credentials/rotate")
    public OpenApiCredentialOnceVO rotateCredential(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long clientId) {
        validateLogin(authorization);
        return openApiManageService.rotateCredential(systemId, clientId);
    }

    /**
     * 保存 OpenAPI scope 授权。
     */
    @Operation(summary = "保存 OpenAPI scope 授权")
    @PutMapping("/clients/{clientId}/scopes")
    public OpenApiClientDetailVO saveScopes(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long clientId,
            @Valid @RequestBody List<OpenApiScopeSaveBO> scopes) {
        validateLogin(authorization);
        return openApiManageService.saveScopes(systemId, clientId, scopes);
    }

    /**
     * 保存 OpenAPI IP 白名单。
     */
    @Operation(summary = "保存 OpenAPI IP 白名单")
    @PutMapping("/clients/{clientId}/ip-whitelist")
    public OpenApiClientDetailVO saveIpWhitelist(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long clientId,
            @Valid @RequestBody List<OpenApiIpWhitelistBO> ipWhitelist) {
        validateLogin(authorization);
        return openApiManageService.saveIpWhitelist(systemId, clientId, ipWhitelist);
    }

    /**
     * 查询 OpenAPI 调用日志。
     */
    @Operation(summary = "查询 OpenAPI 调用日志")
    @GetMapping("/access-logs")
    public PageResult<OpenApiAccessLogVO> listAccessLogs(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, OpenApiAccessLogQueryBO queryBO) {
        validateLogin(authorization);
        return openApiManageService.listAccessLogs(systemId, queryBO);
    }

    /**
     * 查询可授权 scope 目录。
     */
    @Operation(summary = "查询可授权 scope 目录")
    @GetMapping("/scope-catalog")
    public OpenApiScopeCatalogVO scopeCatalog(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return openApiManageService.scopeCatalog(systemId);
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
