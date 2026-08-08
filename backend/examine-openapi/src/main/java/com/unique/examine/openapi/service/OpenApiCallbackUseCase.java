package com.unique.examine.openapi.service;

import com.unique.examine.openapi.api.OpenApiAdminSession;
import com.unique.examine.openapi.api.OpenApiCallbackRequests;
import com.unique.examine.openapi.api.OpenApiCallbackViews;

import java.util.List;

public interface OpenApiCallbackUseCase {
    OpenApiCallbackViews.Subscription create(OpenApiAdminSession session, long applicationId,
                                             OpenApiCallbackRequests.Create request,
                                             String requestId, String traceId);
    List<OpenApiCallbackViews.Subscription> list(OpenApiAdminSession session, long applicationId);
    OpenApiCallbackViews.Subscription detail(OpenApiAdminSession session, long applicationId,
                                             long subscriptionId);
    OpenApiCallbackViews.Subscription replace(OpenApiAdminSession session, long applicationId,
                                              long subscriptionId,
                                              OpenApiCallbackRequests.ReplaceConfiguration request,
                                              String requestId, String traceId);
    OpenApiCallbackViews.Subscription rotateSecret(OpenApiAdminSession session, long applicationId,
                                                   long subscriptionId,
                                                   OpenApiCallbackRequests.RotateSigningSecret request,
                                                   String requestId, String traceId);
    OpenApiCallbackViews.Subscription enable(OpenApiAdminSession session, long applicationId,
                                             long subscriptionId,
                                             OpenApiCallbackRequests.ChangeStatus request,
                                             String requestId, String traceId);
    OpenApiCallbackViews.Subscription disable(OpenApiAdminSession session, long applicationId,
                                              long subscriptionId,
                                              OpenApiCallbackRequests.ChangeStatus request,
                                              String requestId, String traceId);
    OpenApiCallbackViews.DeliveryPage deliveries(OpenApiAdminSession session, long applicationId,
                                                 long subscriptionId, int page, int size);
}
