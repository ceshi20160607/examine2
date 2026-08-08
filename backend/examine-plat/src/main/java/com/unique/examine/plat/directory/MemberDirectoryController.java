package com.unique.examine.plat.directory;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/directory")
public class MemberDirectoryController {
    private final MemberDirectoryService service;

    public MemberDirectoryController(MemberDirectoryService service) {
        this.service = service;
    }

    @GetMapping("/members")
    public ApiResponse<MemberDirectoryApi.Page> members(
            @PathVariable long systemId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestAttribute(
                    value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.runtime.access");
        var tenantId = requireTenant(session);
        requireMember(session);
        return ApiResponse.success(
                service.members(systemId, tenantId, keyword, page, size),
                requestAttribute(request, WebRequestAttributes.REQUEST_ID),
                requestAttribute(request, WebRequestAttributes.TRACE_ID)
        );
    }

    private static long requireTenant(AuthenticatedSession session) {
        if (session.tenantId() == null) {
            throw new BusinessException(
                    "CONTEXT_TENANT_REQUIRED",
                    "Tenant context is required",
                    HttpStatus.FORBIDDEN
            );
        }
        return session.tenantId();
    }

    private static void requireMember(AuthenticatedSession session) {
        if (session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_MEMBER_REQUIRED",
                    "Member context is required",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private static String requestAttribute(HttpServletRequest request, String name) {
        return String.valueOf(request.getAttribute(name));
    }
}
