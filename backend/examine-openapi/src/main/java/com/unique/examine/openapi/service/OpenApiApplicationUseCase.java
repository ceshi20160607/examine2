package com.unique.examine.openapi.service;

import com.unique.examine.openapi.api.OpenApiAdminSession;
import com.unique.examine.openapi.api.OpenApiRequests;
import com.unique.examine.openapi.api.OpenApiViews;

public interface OpenApiApplicationUseCase {
    OpenApiViews.Application create(
            OpenApiAdminSession session,
            OpenApiRequests.CreateApplication request,
            String idempotencyKey,
            String requestId,
            String traceId
    );

    OpenApiViews.ApplicationPage list(OpenApiAdminSession session, int page, int size);

    OpenApiViews.Application detail(OpenApiAdminSession session, long applicationId);

    OpenApiViews.CallLogPage callLogs(
            OpenApiAdminSession session,
            long applicationId,
            String resultCategory,
            String requestMethod,
            int page,
            int size);

    OpenApiViews.Application updatePolicy(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.UpdatePolicy request,
            String idempotencyKey,
            String requestId,
            String traceId
    );

    OpenApiViews.Application rotateSecretRef(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.RotateSecretRef request,
            String idempotencyKey,
            String requestId,
            String traceId
    );

    OpenApiViews.Application enable(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.ChangeStatus request,
            String idempotencyKey,
            String requestId,
            String traceId
    );

    OpenApiViews.Application disable(
            OpenApiAdminSession session,
            long applicationId,
            OpenApiRequests.ChangeStatus request,
            String idempotencyKey,
            String requestId,
            String traceId
    );
}
