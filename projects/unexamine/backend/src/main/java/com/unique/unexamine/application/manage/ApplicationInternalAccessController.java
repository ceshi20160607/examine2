package com.unique.unexamine.application.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/application-internal-access/v1")
public class ApplicationInternalAccessController {
    private final ApplicationBridgeService service;

    public ApplicationInternalAccessController(ApplicationBridgeService service) {
        this.service = service;
    }

    @PostMapping("/applications/{applicationId}/calls")
    public ApiResult<ApplicationBridgeModels.CallResult> call(
            @PathVariable Long applicationId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody ApplicationBridgeModels.CallRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.callInternal(AuthenticationContextHolder.require(), applicationId,
                idempotencyKey, body, TraceIdFilter.current(request)));
    }
}
