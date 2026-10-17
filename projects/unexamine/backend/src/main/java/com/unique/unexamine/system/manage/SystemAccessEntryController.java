package com.unique.unexamine.system.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 未登录时解析专属路径；登录后的系统进入仍由受保护的进入接口完成。 */
@RestController
@RequestMapping("/api/auth/access")
public class SystemAccessEntryController {
    private final SystemEntryService entryService;
    private final SystemDomainService domainService;

    public SystemAccessEntryController(SystemEntryService entryService, SystemDomainService domainService) {
        this.entryService = entryService;
        this.domainService = domainService;
    }

    @GetMapping("/domain")
    public ApiResult<SystemAccessEntryView> resolveDomain(@RequestParam String host, @RequestParam String path,
            @RequestParam(defaultValue = "https") String scheme) {
        return ApiResult.ok(domainService.resolvePublished(host, path, scheme));
    }

    @GetMapping("/id/{systemId}/{tenantId}")
    public ApiResult<SystemAccessEntryView> resolveById(@PathVariable Long systemId, @PathVariable Long tenantId) {
        return ApiResult.ok(entryService.accessEntry(systemId, tenantId));
    }

    @GetMapping("/{systemCode}/{tenantCode}")
    public ApiResult<SystemAccessEntryView> resolve(@PathVariable String systemCode, @PathVariable String tenantCode) {
        return ApiResult.ok(entryService.accessEntry(systemCode, tenantCode));
    }
}
