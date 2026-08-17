package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/runtime/modules")
public class RuntimeModuleController {
    private final ModulePublicationService publicationService;
    private final RuntimeModuleCatalogService catalogService;

    public RuntimeModuleController(
            ModulePublicationService publicationService,
            RuntimeModuleCatalogService catalogService) {
        this.publicationService = publicationService;
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResult<List<RuntimeModuleCatalogItem>> modules() {
        return ApiResult.ok(catalogService.list(AuthenticationContextHolder.require()));
    }

    @GetMapping("/{moduleCode}/configuration")
    public ApiResult<RuntimeModuleConfiguration> configuration(
            @PathVariable String moduleCode,
            HttpServletRequest request) {
        return ApiResult.ok(publicationService.runtime(
                AuthenticationContextHolder.require(), moduleCode, TraceIdFilter.current(request)));
    }
}
