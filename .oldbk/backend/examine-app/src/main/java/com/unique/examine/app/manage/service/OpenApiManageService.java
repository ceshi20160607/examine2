package com.unique.examine.app.manage.service;

import java.util.List;

import com.unique.examine.app.manage.bo.OpenApiAccessLogQueryBO;
import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.bo.OpenApiClientStatusBO;
import com.unique.examine.app.manage.bo.OpenApiIpWhitelistBO;
import com.unique.examine.app.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.app.manage.vo.OpenApiAccessLogVO;
import com.unique.examine.app.manage.vo.OpenApiClientDetailVO;
import com.unique.examine.app.manage.vo.OpenApiCredentialOnceVO;
import com.unique.examine.app.manage.vo.OpenApiScopeCatalogVO;
import com.unique.examine.core.common.response.PageResult;

/**
 * OpenAPI 管理服务。
 */
public interface OpenApiManageService {

    /**
     * 查询客户端列表。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param keyword 关键字
     * @param status 状态
     * @return 客户端列表
     */
    List<OpenApiClientDetailVO> listClients(Long systemId, Long tenantId, String keyword, String status);

    /**
     * 创建客户端并返回一次性 secret。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 客户端详情
     */
    OpenApiClientDetailVO createClient(Long systemId, OpenApiClientSaveBO saveBO);

    /**
     * 更新客户端基础配置。
     *
     * @param systemId 系统 ID
     * @param clientId 客户端 ID
     * @param saveBO 保存入参
     * @return 客户端详情
     */
    OpenApiClientDetailVO updateClient(Long systemId, Long clientId, OpenApiClientSaveBO saveBO);

    /**
     * 变更客户端状态。
     *
     * @param systemId 系统 ID
     * @param clientId 客户端 ID
     * @param statusBO 状态入参
     * @return 客户端详情
     */
    OpenApiClientDetailVO changeStatus(Long systemId, Long clientId, OpenApiClientStatusBO statusBO);

    /**
     * 轮换客户端凭证。
     *
     * @param systemId 系统 ID
     * @param clientId 客户端 ID
     * @return 一次性凭证
     */
    OpenApiCredentialOnceVO rotateCredential(Long systemId, Long clientId);

    /**
     * 更新 scope 授权。
     *
     * @param systemId 系统 ID
     * @param clientId 客户端 ID
     * @param scopes scope 列表
     * @return 客户端详情
     */
    OpenApiClientDetailVO saveScopes(Long systemId, Long clientId, List<OpenApiScopeSaveBO> scopes);

    /**
     * 更新 IP 白名单。
     *
     * @param systemId 系统 ID
     * @param clientId 客户端 ID
     * @param ipWhitelist IP 白名单
     * @return 客户端详情
     */
    OpenApiClientDetailVO saveIpWhitelist(Long systemId, Long clientId, List<OpenApiIpWhitelistBO> ipWhitelist);

    /**
     * 查询 OpenAPI 调用日志。
     *
     * @param systemId 系统 ID
     * @param queryBO 查询入参
     * @return 调用日志分页
     */
    PageResult<OpenApiAccessLogVO> listAccessLogs(Long systemId, OpenApiAccessLogQueryBO queryBO);

    /**
     * 查询可授权 scope 目录。
     *
     * @param systemId 系统 ID
     * @return scope 目录
     */
    OpenApiScopeCatalogVO scopeCatalog(Long systemId);
}
