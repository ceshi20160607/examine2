package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.ConfigManageService;
import com.unique.examine.manage.vo.*;
import com.unique.examine.manage.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/config")
public class ConfigController {
    private final ConfigManageService configManageService;

    @GetMapping("/apps")
    public ApiResponse<PageResult<SimpleVO>> apps(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId) { return ApiResponse.ok(configManageService.apps(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId))); }
    @PostMapping("/apps")
    public ApiResponse<SimpleVO> saveApp(@Valid @RequestBody AppSaveBO bo) { return ApiResponse.ok(configManageService.saveApp(bo)); }
    @PostMapping("/apps/{appId}/publish")
    public ApiResponse<SimpleVO> publishApp(@PathVariable Long appId) { return ApiResponse.ok(configManageService.publishApp(appId)); }

    @GetMapping("/modules")
    public ApiResponse<PageResult<SimpleVO>> modules(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam Long appId) { return ApiResponse.ok(configManageService.modules(pageNo, pageSize, appId)); }
    @PostMapping("/modules")
    public ApiResponse<SimpleVO> saveModule(@Valid @RequestBody ModuleSaveBO bo) { return ApiResponse.ok(configManageService.saveModule(bo)); }

    @GetMapping("/fields")
    public ApiResponse<PageResult<SimpleVO>> fields(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam Long moduleId) { return ApiResponse.ok(configManageService.fields(pageNo, pageSize, moduleId)); }
    @PostMapping("/fields")
    public ApiResponse<SimpleVO> saveField(@Valid @RequestBody FieldSaveBO bo) { return ApiResponse.ok(configManageService.saveField(bo)); }
    @PostMapping("/field-options")
    public ApiResponse<SimpleVO> saveFieldOption(@Valid @RequestBody FieldOptionSaveBO bo) { return ApiResponse.ok(configManageService.saveFieldOption(bo)); }

    @GetMapping("/pages")
    public ApiResponse<PageResult<SimpleVO>> pages(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam Long moduleId) { return ApiResponse.ok(configManageService.pages(pageNo, pageSize, moduleId)); }
    @PostMapping("/pages")
    public ApiResponse<SimpleVO> savePage(@Valid @RequestBody PageSaveBO bo) { return ApiResponse.ok(configManageService.savePage(bo)); }
    @PostMapping("/pages/{pageId}/publish")
    public ApiResponse<SimpleVO> publishPage(@PathVariable Long pageId) { return ApiResponse.ok(configManageService.publishPage(pageId)); }

    @GetMapping("/menus")
    public ApiResponse<PageResult<SimpleVO>> menus(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId) { return ApiResponse.ok(configManageService.menus(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId))); }
    @PostMapping("/menus")
    public ApiResponse<SimpleVO> saveMenu(@Valid @RequestBody RuntimeMenuSaveBO bo) { return ApiResponse.ok(configManageService.saveMenu(bo)); }

    @GetMapping("/dictionaries")
    public ApiResponse<PageResult<SimpleVO>> dictionaries(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId) { return ApiResponse.ok(configManageService.dictionaries(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId))); }
    @PostMapping("/dictionaries")
    public ApiResponse<SimpleVO> saveDictionary(@Valid @RequestBody DictionarySaveBO bo) { return ApiResponse.ok(configManageService.saveDictionary(bo)); }
    @PostMapping("/dictionary-items")
    public ApiResponse<SimpleVO> saveDictionaryItem(@Valid @RequestBody DictionaryItemSaveBO bo) { return ApiResponse.ok(configManageService.saveDictionaryItem(bo)); }

    private Long resolveSystemId(Long systemId) {
        Long resolved = systemId == null ? SecurityContext.currentUser().getSystemId() : systemId;
        if (resolved == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少系统上下文"); }
        return resolved;
    }

    private Long resolveTenantId(Long tenantId) {
        Long resolved = tenantId == null ? SecurityContext.currentUser().getTenantId() : tenantId;
        if (resolved == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少租户上下文"); }
        return resolved;
    }
}
