package com.unique.examine.manage.service;

import com.unique.examine.base.entity.OpenapiClient;
import jakarta.servlet.http.HttpServletRequest;

public interface OpenApiAuthService {
    Long authenticate(HttpServletRequest request, String body);

    OpenapiClient authenticateClient(HttpServletRequest request, String body);

    void requireScope(Long clientPk, String scopeType, String scopeValue);

    void requireRequestScope(OpenapiClient client, Long systemId, Long tenantId, Long appId, Long moduleId, String actionCode);
}
