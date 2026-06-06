package com.unique.examine.app.manage.controller;

import com.unique.examine.app.manage.bo.AppApplicationSaveBO;
import com.unique.examine.app.manage.bo.AppPublishBO;
import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.bo.OpenApiCredentialCreateBO;
import com.unique.examine.app.manage.bo.OpenApiIdempotentSaveBO;
import com.unique.examine.app.manage.bo.OpenApiIpWhitelistSaveBO;
import com.unique.examine.app.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.app.manage.dto.OpenApiAccessLogQueryDTO;
import com.unique.examine.app.manage.service.AppManageService;
import com.unique.examine.app.manage.vo.AppManageVO;
import com.unique.examine.core.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用与 OpenAPI 管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/apps")
@Tag(name = "应用与 OpenAPI")
public class AppManageController {

    private final AppManageService appManageService;

    /**
     * 查询应用列表。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 应用列表
     */
    @GetMapping
    @Operation(summary = "查询应用列表")
    public ApiResult<List<AppManageVO>> listApplications(@RequestParam(required = false) Long tenantId,
                                                         @RequestParam(required = false) Long systemId) {
        return ApiResult.success(appManageService.listApplications(tenantId, systemId));
    }

    /**
     * 创建应用。
     *
     * @param bo 应用入参
     * @return 应用信息
     */
    @PostMapping
    @Operation(summary = "创建应用")
    public ApiResult<AppManageVO> createApplication(@RequestBody AppApplicationSaveBO bo) {
        return ApiResult.success(appManageService.createApplication(bo));
    }

    /**
     * 发布应用。
     *
     * @param bo 发布入参
     * @return 版本信息
     */
    @PostMapping("/publish")
    @Operation(summary = "发布应用")
    public ApiResult<AppManageVO> publishApplication(@RequestBody AppPublishBO bo) {
        return ApiResult.success(appManageService.publishApplication(bo));
    }

    /**
     * 查询 OpenAPI 客户端。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 客户端列表
     */
    @GetMapping("/openapi/clients")
    @Operation(summary = "查询 OpenAPI 客户端")
    public ApiResult<List<AppManageVO>> listClients(@RequestParam(required = false) Long tenantId,
                                                    @RequestParam(required = false) Long systemId) {
        return ApiResult.success(appManageService.listClients(tenantId, systemId));
    }

    /**
     * 创建 OpenAPI 客户端。
     *
     * @param bo 客户端入参
     * @return 客户端信息
     */
    @PostMapping("/openapi/clients")
    @Operation(summary = "创建 OpenAPI 客户端")
    public ApiResult<AppManageVO> createClient(@RequestBody OpenApiClientSaveBO bo) {
        return ApiResult.success(appManageService.createClient(bo));
    }

    /**
     * 创建 OpenAPI 凭证。
     *
     * @param bo 凭证入参
     * @return 凭证信息
     */
    @PostMapping("/openapi/credentials")
    @Operation(summary = "创建 OpenAPI 凭证")
    public ApiResult<AppManageVO> createCredential(@RequestBody OpenApiCredentialCreateBO bo) {
        return ApiResult.success(appManageService.createCredential(bo));
    }

    /**
     * 创建 OpenAPI scope。
     *
     * @param bo scope 入参
     * @return scope 信息
     */
    @PostMapping("/openapi/scopes")
    @Operation(summary = "创建 OpenAPI scope")
    public ApiResult<AppManageVO> createScope(@RequestBody OpenApiScopeSaveBO bo) {
        return ApiResult.success(appManageService.createScope(bo));
    }

    /**
     * 创建 OpenAPI IP 白名单。
     *
     * @param bo 白名单入参
     * @return 白名单信息
     */
    @PostMapping("/openapi/ip-whitelist")
    @Operation(summary = "创建 OpenAPI IP 白名单")
    public ApiResult<AppManageVO> createIpWhitelist(@RequestBody OpenApiIpWhitelistSaveBO bo) {
        return ApiResult.success(appManageService.createIpWhitelist(bo));
    }

    /**
     * 创建 OpenAPI 幂等记录。
     *
     * @param bo 幂等入参
     * @return 幂等记录
     */
    @PostMapping("/openapi/idempotents")
    @Operation(summary = "创建 OpenAPI 幂等记录")
    public ApiResult<AppManageVO> createIdempotent(@RequestBody OpenApiIdempotentSaveBO bo) {
        return ApiResult.success(appManageService.createIdempotent(bo));
    }

    /**
     * 查询 OpenAPI 访问日志。
     *
     * @param clientId 客户端 ID
     * @param status 状态
     * @return 访问日志列表
     */
    @GetMapping("/openapi/access-logs")
    @Operation(summary = "查询 OpenAPI 访问日志")
    public ApiResult<List<AppManageVO>> listAccessLogs(@RequestParam(required = false) Long clientId,
                                                       @RequestParam(required = false) String status) {
        OpenApiAccessLogQueryDTO dto = new OpenApiAccessLogQueryDTO();
        dto.setClientId(clientId);
        dto.setStatus(status);
        return ApiResult.success(appManageService.listAccessLogs(dto));
    }
}
