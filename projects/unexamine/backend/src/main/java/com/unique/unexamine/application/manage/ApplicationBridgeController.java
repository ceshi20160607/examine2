package com.unique.unexamine.application.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/application-access/v1")
public class ApplicationBridgeController {
    private final ApplicationBridgeService service;

    public ApplicationBridgeController(ApplicationBridgeService service) {
        this.service = service;
    }

    @PostMapping("/calls")
    public ApiResult<ApplicationBridgeModels.CallResult> call(
            @RequestHeader("X-App-Client-Id") String clientId,
            @RequestHeader("X-App-Timestamp") String timestamp,
            @RequestHeader("X-App-Nonce") String nonce,
            @RequestHeader("X-App-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-App-Signature") String signature,
            @RequestBody String rawBody,
            HttpServletRequest request) {
        return ApiResult.ok(service.call(clientId, timestamp, nonce, idempotencyKey, signature,
                rawBody, request.getRemoteAddr(), TraceIdFilter.current(request)));
    }
}
