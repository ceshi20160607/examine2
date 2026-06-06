package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.*;

public interface OpenApiManageService {
    PageResult<SimpleVO> clients(long pageNo, long pageSize, Long systemId, Long tenantId);
    SimpleVO saveClient(OpenApiClientSaveBO bo);
    CredentialVO createCredential(OpenApiCredentialCreateBO bo);
    SimpleVO saveScope(OpenApiScopeSaveBO bo);
    SimpleVO saveIp(OpenApiIpSaveBO bo);
}
