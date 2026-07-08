package com.unique.examine.module.manage.config;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishCheckResultVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishResult;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageDesignerSaveRequest;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageDesignerVO;
import com.unique.examine.module.manage.config.ModulePageDesignModels.PageSchemaVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module page designer API controller.
 */
@RestController
public class ModulePageDesignController {

    private final ModulePageDesignService pageDesignService;

    public ModulePageDesignController(ModulePageDesignService pageDesignService) {
        this.pageDesignService = pageDesignService;
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/pages")
    public ApiResponse<List<PageDesignerVO>> pages(@PathVariable String systemId, @PathVariable String moduleId) {
        return ApiResponse.success(pageDesignService.pages(systemId, moduleId));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/pages")
    public ApiResponse<PageDesignerVO> savePage(@PathVariable String systemId, @PathVariable String moduleId,
                                                @RequestBody PageDesignerSaveRequest request) {
        return ApiResponse.success(pageDesignService.savePage(systemId, moduleId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/pages/{pageCode}/publish-check")
    public ApiResponse<PublishCheckResultVO> publishCheck(@PathVariable String systemId,
                                                          @PathVariable String moduleId,
                                                          @PathVariable String pageCode) {
        return ApiResponse.success(pageDesignService.publishCheck(systemId, moduleId, pageCode));
    }

    @PostMapping("/api/v1/systems/{systemId}/modules/{moduleId}/pages/{pageCode}/publish")
    public ApiResponse<PublishResult> publishPage(@PathVariable String systemId,
                                                  @PathVariable String moduleId,
                                                  @PathVariable String pageCode,
                                                  @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(pageDesignService.publishPage(systemId, moduleId, pageCode, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/modules/{moduleId}/pages/{pageCode}/schema")
    public ApiResponse<PageSchemaVO> pageSchema(@PathVariable String systemId,
                                                @PathVariable String moduleId,
                                                @PathVariable String pageCode,
                                                @RequestParam(defaultValue = "draft") String snapshot) {
        return ApiResponse.success(pageDesignService.pageSchema(systemId, moduleId, pageCode, snapshot));
    }

    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/pages/{pageCode}")
    public ApiResponse<PageDesignerVO> runtimePage(@PathVariable String systemId,
                                                   @PathVariable String moduleId,
                                                   @PathVariable String pageCode) {
        return ApiResponse.success(pageDesignService.runtimePage(systemId, moduleId, pageCode));
    }

    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/pages/{pageCode}/schema")
    public ApiResponse<PageSchemaVO> runtimePageSchema(@PathVariable String systemId,
                                                       @PathVariable String moduleId,
                                                       @PathVariable String pageCode) {
        return ApiResponse.success(pageDesignService.runtimePageSchema(systemId, moduleId, pageCode));
    }
}
