package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.GlobalConfigSaveBO;
import com.unique.examine.manage.vo.*;

public interface OpsManageService {
    HealthVO health();
    PageResult<SimpleVO> auditLogs(long pageNo, long pageSize, Long systemId, Long tenantId, String actionType);
    PageResult<SimpleVO> configs(long pageNo, long pageSize, Long systemId, Long tenantId);
    SimpleVO saveConfig(GlobalConfigSaveBO bo);
}
