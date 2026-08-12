package com.unique.examine.module.runtime.quickcreate;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/quick-create-modules")
public class QuickCreateController {
    private final QuickCreateService service;

    public QuickCreateController(QuickCreateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<QuickCreateViews.ModuleList> modules(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.modules(RuntimeSession.require(value, systemId)),
                String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID)));
    }
}
