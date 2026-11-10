package com.unique.unexamine.plat.manage.service;

import com.unique.unexamine.core.ops.OperationsHealthService;
import org.springframework.stereotype.Service;

/** 平台运维体检只允许平台管理员读取基础设施状态。 */
@Service
public class PlatformOperationsService {
    private final AuthManageService auth;
    private final OperationsHealthService health;
    public PlatformOperationsService(AuthManageService auth,OperationsHealthService health) {
        this.auth=auth; this.health=health;
    }

    public OperationsHealthService.Snapshot inspect(String token) {
        auth.requirePlatformAdmin(token);
        return health.inspect();
    }
}
