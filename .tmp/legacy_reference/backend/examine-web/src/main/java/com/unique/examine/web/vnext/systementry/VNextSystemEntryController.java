package com.unique.examine.web.vnext.systementry;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.plat.vnext.manage.auth.AccountContext;
import com.unique.examine.plat.vnext.manage.auth.SystemSummary;
import com.unique.examine.plat.vnext.manage.systementry.VNextSystemEntryService;
import com.unique.examine.web.vnext.auth.VNextSessionCookieSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("vnext")
@RequestMapping("/api/v1/context/systems")
public class VNextSystemEntryController {
    private final VNextSystemEntryService systemEntryService;
    private final VNextSessionCookieSupport cookieSupport;

    public VNextSystemEntryController(
            VNextSystemEntryService systemEntryService,
            VNextSessionCookieSupport cookieSupport
    ) {
        this.systemEntryService = systemEntryService;
        this.cookieSupport = cookieSupport;
    }

    @GetMapping
    public ApiResponse<List<SystemSummary>> list(
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object authenticated,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                systemEntryService.list(authenticated), requestId(request), traceId(request)
        );
    }

    @PostMapping("/{systemId}:switch")
    public ApiResponse<AccountContext> switchSystem(
            @PathVariable String systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object authenticated,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var issued = systemEntryService.switchSystem(authenticated, systemId);
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.accountContext(), requestId(request), traceId(request));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }
}
