package com.unique.examine.plat.manage.system;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.plat.manage.system.SystemModels.LifecycleResult;
import com.unique.examine.plat.manage.system.SystemModels.PlatformHealthVO;
import com.unique.examine.plat.manage.system.SystemModels.SystemCreateRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemLifecycleRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemQueryRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemUpdateRequest;
import com.unique.examine.plat.manage.system.SystemModels.SystemVO;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform system lifecycle API controller.
 */
@RestController
public class PlatformSystemController {

    private final PlatformSystemService systemService;

    public PlatformSystemController(PlatformSystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/api/v1/platform/systems")
    public ApiResponse<PageResult<SystemVO>> list(@RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  SystemQueryRequest query) {
        return ApiResponse.success(systemService.search(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/platform/systems")
    public ApiResponse<SystemVO> create(@RequestBody SystemCreateRequest request) {
        return ApiResponse.success(systemService.create(request));
    }

    @GetMapping("/api/v1/platform/systems/{systemId}")
    public ApiResponse<SystemVO> detail(@PathVariable String systemId) {
        return ApiResponse.success(systemService.detail(systemId));
    }

    @PatchMapping("/api/v1/platform/systems/{systemId}")
    public ApiResponse<SystemVO> update(@PathVariable String systemId, @RequestBody SystemUpdateRequest request) {
        return ApiResponse.success(systemService.update(systemId, request));
    }

    @PostMapping("/api/v1/platform/systems/{systemId}/enable")
    public ApiResponse<LifecycleResult> enable(@PathVariable String systemId,
                                               @RequestBody SystemLifecycleRequest request) {
        return ApiResponse.success(systemService.lifecycle(systemId, "enable", request));
    }

    @PostMapping("/api/v1/platform/systems/{systemId}/disable")
    public ApiResponse<LifecycleResult> disable(@PathVariable String systemId,
                                                @RequestBody SystemLifecycleRequest request) {
        return ApiResponse.success(systemService.lifecycle(systemId, "disable", request));
    }

    @DeleteMapping("/api/v1/platform/systems/{systemId}")
    public ApiResponse<LifecycleResult> delete(@PathVariable String systemId,
                                               @RequestBody(required = false) SystemLifecycleRequest request) {
        return ApiResponse.success(systemService.lifecycle(systemId, "delete", request));
    }

    @PostMapping("/api/v1/platform/systems/{systemId}/restore")
    public ApiResponse<LifecycleResult> restore(@PathVariable String systemId,
                                                @RequestBody SystemLifecycleRequest request) {
        return ApiResponse.success(systemService.lifecycle(systemId, "restore", request));
    }

    @GetMapping("/api/v1/platform/health")
    public ApiResponse<PlatformHealthVO> health() {
        return ApiResponse.success(systemService.health());
    }
}
