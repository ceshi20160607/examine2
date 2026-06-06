package com.unique.examine.app.manage.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.entity.Application;
import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.Credential;
import com.unique.examine.app.base.entity.Idempotent;
import com.unique.examine.app.base.entity.IpWhitelist;
import com.unique.examine.app.base.entity.Scope;
import com.unique.examine.app.base.entity.Version;
import com.unique.examine.app.base.service.IAccessLogService;
import com.unique.examine.app.base.service.IApplicationService;
import com.unique.examine.app.base.service.IClientService;
import com.unique.examine.app.base.service.ICredentialService;
import com.unique.examine.app.base.service.IIdempotentService;
import com.unique.examine.app.base.service.IIpWhitelistService;
import com.unique.examine.app.base.service.IScopeService;
import com.unique.examine.app.base.service.IVersionService;
import com.unique.examine.app.manage.bo.AppApplicationSaveBO;
import com.unique.examine.app.manage.bo.AppPublishBO;
import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.bo.OpenApiCredentialCreateBO;
import com.unique.examine.app.manage.bo.OpenApiIdempotentSaveBO;
import com.unique.examine.app.manage.bo.OpenApiIpWhitelistSaveBO;
import com.unique.examine.app.manage.bo.OpenApiScopeSaveBO;
import com.unique.examine.app.manage.converter.AppManageConverter;
import com.unique.examine.app.manage.dto.OpenApiAccessLogQueryDTO;
import com.unique.examine.app.manage.enums.AppManageErrorCode;
import com.unique.examine.app.manage.service.AppManageService;
import com.unique.examine.app.manage.vo.AppManageVO;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContext;
import com.unique.examine.core.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用与 OpenAPI 管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class AppManageServiceImpl implements AppManageService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String ENABLED = "ENABLED";
    private static final String PROCESSING = "PROCESSING";

    private final IApplicationService applicationService;
    private final IVersionService versionService;
    private final IClientService clientService;
    private final ICredentialService credentialService;
    private final IScopeService scopeService;
    private final IIpWhitelistService ipWhitelistService;
    private final IIdempotentService idempotentService;
    private final IAccessLogService accessLogService;

    @Override
    public List<AppManageVO> listApplications(Long tenantId, Long systemId) {
        return applicationService.list(Wrappers.<Application>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(tenantId), Application::getTenantId, tenantId)
                        .eq(ObjectUtil.isNotNull(systemId), Application::getSystemId, systemId))
                .stream().map(AppManageConverter::fromApplication).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO createApplication(AppApplicationSaveBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getSystemId(), "systemId");
        requireText(bo.getAppCode(), "appCode");
        requireText(bo.getAppName(), "appName");
        Application app = new Application();
        app.setTenantId(bo.getTenantId());
        app.setSystemId(bo.getSystemId());
        app.setAppCode(bo.getAppCode());
        app.setAppName(bo.getAppName());
        app.setVisibleScope(StrUtil.blankToDefault(bo.getVisibleScope(), "TENANT"));
        app.setDescription(bo.getDescription());
        app.setStatus(DRAFT);
        fillAudit(app::setCreatedBy, app::setUpdatedBy);
        applicationService.save(app);
        return AppManageConverter.fromApplication(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO publishApplication(AppPublishBO bo) {
        requireId(bo.getAppId(), "appId");
        if (ObjectUtil.isNull(bo.getVersionNo())) {
            throwError(AppManageErrorCode.PARAM_REQUIRED);
        }
        Application app = requireApplication(bo.getAppId());
        Version version = new Version();
        version.setAppId(app.getId());
        version.setVersionNo(bo.getVersionNo());
        version.setVersionName(StrUtil.blankToDefault(bo.getVersionName(), "v" + bo.getVersionNo()));
        version.setStatus(PUBLISHED);
        version.setSnapshotJson(bo.getSnapshotJson());
        version.setPublishedAt(LocalDateTime.now());
        fillAudit(version::setCreatedBy, version::setUpdatedBy);
        versionService.save(version);
        app.setStatus(PUBLISHED);
        app.setPublishedVersionId(version.getId());
        fillUpdatedBy(app::setUpdatedBy);
        applicationService.updateById(app);
        return AppManageConverter.fromVersion(version);
    }

    @Override
    public List<AppManageVO> listClients(Long tenantId, Long systemId) {
        return clientService.list(Wrappers.<Client>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(tenantId), Client::getTenantId, tenantId)
                        .eq(ObjectUtil.isNotNull(systemId), Client::getSystemId, systemId))
                .stream().map(AppManageConverter::fromClient).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO createClient(OpenApiClientSaveBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getSystemId(), "systemId");
        requireText(bo.getClientCode(), "clientCode");
        requireText(bo.getClientName(), "clientName");
        Client client = new Client();
        client.setTenantId(bo.getTenantId());
        client.setSystemId(bo.getSystemId());
        client.setClientCode(bo.getClientCode());
        client.setClientName(bo.getClientName());
        client.setRateLimitPerMinute(ObjectUtil.defaultIfNull(bo.getRateLimitPerMinute(), 600));
        client.setExpiredAt(bo.getExpiredAt());
        client.setStatus(ENABLED);
        fillAudit(client::setCreatedBy, client::setUpdatedBy);
        clientService.save(client);
        return AppManageConverter.fromClient(client);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO createCredential(OpenApiCredentialCreateBO bo) {
        requireId(bo.getClientId(), "clientId");
        requireText(bo.getAccessKey(), "accessKey");
        requireText(bo.getSecret(), "secret");
        Credential credential = new Credential();
        credential.setClientId(bo.getClientId());
        credential.setAccessKey(bo.getAccessKey());
        credential.setSecretHash(bo.getSecret());
        credential.setSecretVersion(1);
        credential.setSignAlgorithm(StrUtil.blankToDefault(bo.getSignAlgorithm(), "HMAC_SHA256"));
        credential.setStatus(ENABLED);
        fillAudit(credential::setCreatedBy, credential::setUpdatedBy);
        credentialService.save(credential);
        return AppManageConverter.fromCredential(credential);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO createScope(OpenApiScopeSaveBO bo) {
        requireId(bo.getClientId(), "clientId");
        requireText(bo.getScopeCode(), "scopeCode");
        requireText(bo.getActions(), "actions");
        Scope scope = new Scope();
        scope.setClientId(bo.getClientId());
        scope.setAppId(bo.getAppId());
        scope.setModuleId(bo.getModuleId());
        scope.setScopeCode(bo.getScopeCode());
        scope.setActions(bo.getActions());
        scope.setStatus(ENABLED);
        fillAudit(scope::setCreatedBy, scope::setUpdatedBy);
        scopeService.save(scope);
        return AppManageConverter.fromScope(scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO createIpWhitelist(OpenApiIpWhitelistSaveBO bo) {
        requireId(bo.getClientId(), "clientId");
        requireText(bo.getIpValue(), "ipValue");
        IpWhitelist ip = new IpWhitelist();
        ip.setClientId(bo.getClientId());
        ip.setIpValue(bo.getIpValue());
        ip.setStatus(ENABLED);
        fillAudit(ip::setCreatedBy, ip::setUpdatedBy);
        ipWhitelistService.save(ip);
        return AppManageConverter.fromIp(ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppManageVO createIdempotent(OpenApiIdempotentSaveBO bo) {
        requireId(bo.getClientId(), "clientId");
        requireText(bo.getIdempotentKey(), "idempotentKey");
        requireText(bo.getRequestHash(), "requestHash");
        Idempotent idempotent = new Idempotent();
        idempotent.setClientId(bo.getClientId());
        idempotent.setIdempotentKey(bo.getIdempotentKey());
        idempotent.setRequestHash(bo.getRequestHash());
        idempotent.setResponseHash(bo.getResponseHash());
        idempotent.setStatus(PROCESSING);
        idempotent.setExpiredAt(ObjectUtil.defaultIfNull(bo.getExpiredAt(), LocalDateTime.now().plusHours(2)));
        idempotentService.save(idempotent);
        return AppManageConverter.fromIdempotent(idempotent);
    }

    @Override
    public List<AppManageVO> listAccessLogs(OpenApiAccessLogQueryDTO dto) {
        return accessLogService.list(Wrappers.<AccessLog>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(dto.getClientId()), AccessLog::getClientId, dto.getClientId())
                        .eq(StrUtil.isNotBlank(dto.getStatus()), AccessLog::getStatus, dto.getStatus()))
                .stream().map(AppManageConverter::fromAccessLog).toList();
    }

    /**
     * 查询并校验应用存在。
     *
     * @param id 应用 ID
     * @return 应用实体
     */
    private Application requireApplication(Long id) {
        Application app = applicationService.getById(id);
        if (ObjectUtil.isNull(app)) {
            throwError(AppManageErrorCode.DATA_NOT_FOUND);
        }
        return app;
    }

    /**
     * 校验文本必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireText(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(AppManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 校验 ID 必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireId(Long value, String field) {
        if (ObjectUtil.isNull(value)) {
            throw new BusinessException(AppManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 写入创建与更新人。
     *
     * @param createdSetter 创建人写入器
     * @param updatedSetter 更新人写入器
     */
    private void fillAudit(java.util.function.Consumer<Long> createdSetter, java.util.function.Consumer<Long> updatedSetter) {
        Long accountId = currentAccountId();
        createdSetter.accept(accountId);
        updatedSetter.accept(accountId);
    }

    /**
     * 写入更新人。
     *
     * @param updatedSetter 更新人写入器
     */
    private void fillUpdatedBy(java.util.function.Consumer<Long> updatedSetter) {
        updatedSetter.accept(currentAccountId());
    }

    /**
     * 获取当前账号 ID。
     *
     * @return 当前账号 ID
     */
    private Long currentAccountId() {
        AuthContext context = AuthContextHolder.get();
        return ObjectUtil.isNull(context) ? null : context.getAccountId();
    }

    /**
     * 抛出应用异常。
     *
     * @param errorCode 错误码
     */
    private void throwError(AppManageErrorCode errorCode) {
        throw new BusinessException(errorCode.getCode(), errorCode.getMessage());
    }
}
