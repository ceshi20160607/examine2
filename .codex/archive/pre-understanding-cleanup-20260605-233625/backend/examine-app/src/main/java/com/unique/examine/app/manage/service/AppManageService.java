package com.unique.examine.app.manage.service;

import com.unique.examine.app.manage.bo.AppApplicationSaveBO;
import com.unique.examine.app.manage.bo.AppPublishBO;
import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.bo.OpenApiCredentialCreateBO;
import com.unique.examine.app.manage.bo.OpenApiIdempotentSaveBO;
import com.unique.examine.app.manage.bo.OpenApiIpWhitelistSaveBO;
import com.unique.examine.app.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.app.manage.dto.OpenApiAccessLogQueryDTO;
import com.unique.examine.app.manage.vo.AppManageVO;

import java.util.List;

/**
 * 应用与 OpenAPI 管理服务。
 */
public interface AppManageService {

    /**
     * 查询应用列表。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 应用列表
     */
    List<AppManageVO> listApplications(Long tenantId, Long systemId);

    /**
     * 创建应用。
     *
     * @param bo 应用入参
     * @return 应用信息
     */
    AppManageVO createApplication(AppApplicationSaveBO bo);

    /**
     * 发布应用版本。
     *
     * @param bo 发布入参
     * @return 版本信息
     */
    AppManageVO publishApplication(AppPublishBO bo);

    /**
     * 查询 OpenAPI 客户端。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 客户端列表
     */
    List<AppManageVO> listClients(Long tenantId, Long systemId);

    /**
     * 创建 OpenAPI 客户端。
     *
     * @param bo 客户端入参
     * @return 客户端信息
     */
    AppManageVO createClient(OpenApiClientSaveBO bo);

    /**
     * 创建 OpenAPI 凭证。
     *
     * @param bo 凭证入参
     * @return 凭证信息
     */
    AppManageVO createCredential(OpenApiCredentialCreateBO bo);

    /**
     * 创建 OpenAPI scope。
     *
     * @param bo scope 入参
     * @return scope 信息
     */
    AppManageVO createScope(OpenApiScopeSaveBO bo);

    /**
     * 创建 OpenAPI IP 白名单。
     *
     * @param bo 白名单入参
     * @return 白名单信息
     */
    AppManageVO createIpWhitelist(OpenApiIpWhitelistSaveBO bo);

    /**
     * 创建 OpenAPI 幂等记录。
     *
     * @param bo 幂等记录入参
     * @return 幂等记录信息
     */
    AppManageVO createIdempotent(OpenApiIdempotentSaveBO bo);

    /**
     * 查询 OpenAPI 访问日志。
     *
     * @param dto 查询 DTO
     * @return 访问日志列表
     */
    List<AppManageVO> listAccessLogs(OpenApiAccessLogQueryDTO dto);
}
