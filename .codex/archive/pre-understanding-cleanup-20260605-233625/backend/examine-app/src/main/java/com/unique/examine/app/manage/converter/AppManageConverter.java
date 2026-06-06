package com.unique.examine.app.manage.converter;

import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.entity.Application;
import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.Credential;
import com.unique.examine.app.base.entity.Idempotent;
import com.unique.examine.app.base.entity.IpWhitelist;
import com.unique.examine.app.base.entity.Scope;
import com.unique.examine.app.base.entity.Version;
import com.unique.examine.app.manage.vo.AppManageVO;

/**
 * 应用与 OpenAPI 实体转换器。
 */
public final class AppManageConverter {

    private AppManageConverter() {
    }

    public static AppManageVO fromApplication(Application entity) {
        AppManageVO vo = base(entity.getId(), entity.getTenantId(), entity.getSystemId(), entity.getStatus());
        vo.setAppId(entity.getId());
        vo.setCode(entity.getAppCode());
        vo.setName(entity.getAppName());
        vo.setType(entity.getVisibleScope());
        vo.setVersionNo(entity.getPublishedVersionId() == null ? null : entity.getPublishedVersionId().intValue());
        vo.setDetail(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromVersion(Version entity) {
        AppManageVO vo = new AppManageVO();
        vo.setId(entity.getId());
        vo.setAppId(entity.getAppId());
        vo.setCode(String.valueOf(entity.getVersionNo()));
        vo.setName(entity.getVersionName());
        vo.setStatus(entity.getStatus());
        vo.setVersionNo(entity.getVersionNo());
        vo.setDetail(entity.getSnapshotJson());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromClient(Client entity) {
        AppManageVO vo = base(entity.getId(), entity.getTenantId(), entity.getSystemId(), entity.getStatus());
        vo.setClientId(entity.getId());
        vo.setCode(entity.getClientCode());
        vo.setName(entity.getClientName());
        vo.setVersionNo(entity.getRateLimitPerMinute());
        vo.setExpiredAt(entity.getExpiredAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromCredential(Credential entity) {
        AppManageVO vo = new AppManageVO();
        vo.setId(entity.getId());
        vo.setClientId(entity.getClientId());
        vo.setCode(entity.getAccessKey());
        vo.setStatus(entity.getStatus());
        vo.setType(entity.getSignAlgorithm());
        vo.setVersionNo(entity.getSecretVersion());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromScope(Scope entity) {
        AppManageVO vo = new AppManageVO();
        vo.setId(entity.getId());
        vo.setClientId(entity.getClientId());
        vo.setAppId(entity.getAppId());
        vo.setModuleId(entity.getModuleId());
        vo.setCode(entity.getScopeCode());
        vo.setType(entity.getActions());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromIp(IpWhitelist entity) {
        AppManageVO vo = new AppManageVO();
        vo.setId(entity.getId());
        vo.setClientId(entity.getClientId());
        vo.setValue(entity.getIpValue());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromIdempotent(Idempotent entity) {
        AppManageVO vo = new AppManageVO();
        vo.setId(entity.getId());
        vo.setClientId(entity.getClientId());
        vo.setCode(entity.getIdempotentKey());
        vo.setStatus(entity.getStatus());
        vo.setValue(entity.getRequestHash());
        vo.setDetail(entity.getResponseHash());
        vo.setExpiredAt(entity.getExpiredAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static AppManageVO fromAccessLog(AccessLog entity) {
        AppManageVO vo = new AppManageVO();
        vo.setId(entity.getId());
        vo.setClientId(entity.getClientId());
        vo.setCode(entity.getRequestId());
        vo.setStatus(entity.getStatus());
        vo.setType(entity.getHttpMethod());
        vo.setValue(entity.getRequestPath());
        vo.setDetail(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private static AppManageVO base(Long id, Long tenantId, Long systemId, String status) {
        AppManageVO vo = new AppManageVO();
        vo.setId(id);
        vo.setTenantId(tenantId);
        vo.setSystemId(systemId);
        vo.setStatus(status);
        return vo;
    }
}
