package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.*;

public interface PlatformManageService {
    PageResult<SimpleVO> listTenants(long pageNo, long pageSize, String keyword);
    SimpleVO createTenant(TenantSaveBO bo);
    PageResult<SimpleVO> listSystems(long pageNo, long pageSize, Long tenantId, String keyword);
    SimpleVO createSystem(SystemSaveBO bo);
    SimpleVO updateSystemStatus(Long systemId, StatusUpdateBO bo);
    AuthTokenVO enterSystem(ContextEnterBO bo);
}
