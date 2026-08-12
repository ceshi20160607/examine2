package com.unique.examine.module.runtime.search;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/global-search")
public class GlobalSearchController {
    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<GlobalSearchViews.SearchPage> search(
            @PathVariable long systemId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.search(RuntimeSession.require(value, systemId), q, page, size),
                String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID)));
    }
}
