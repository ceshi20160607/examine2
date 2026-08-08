package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.service.SessionService;
import com.unique.examine.plat.manage.vo.AuthResultVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final SessionService sessionService;

    public MeController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/context")
    public ApiResponse<AuthResultVo> context(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.require(sessionValue);
        return ApiResponse.success(
                sessionService.currentResult(session),
                ControllerSupport.requestId(request),
                ControllerSupport.traceId(request)
        );
    }
}
