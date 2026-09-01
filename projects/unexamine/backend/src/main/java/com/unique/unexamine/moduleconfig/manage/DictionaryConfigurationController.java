package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionaryItem;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dictionaries")
@RequirePermission(resourceType = "CONFIG", resourceCode = "DICTIONARY", actionCode = "MANAGE")
public class DictionaryConfigurationController {
    private final DictionaryConfigurationService service;

    public DictionaryConfigurationController(DictionaryConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(resourceType = "CONFIG", resourceCode = "DICTIONARY", actionCode = "PREVIEW")
    public ApiResult<List<DictionaryModels.DictionaryDraft>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<DictionaryModels.DictionaryDraft> create(
            @Valid @RequestBody DictionaryModels.CreateDictionaryRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), request,
                TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/{dictionaryId}")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "DICTIONARY", actionCode = "PREVIEW")
    public ApiResult<DictionaryModels.DictionaryDraft> draft(@PathVariable Long dictionaryId) {
        return ApiResult.ok(service.draft(AuthenticationContextHolder.require(), dictionaryId));
    }

    @PostMapping("/{dictionaryId}/items")
    public ApiResult<CfgDictionaryItem> createItem(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody DictionaryModels.CreateItemRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.createItem(AuthenticationContextHolder.require(), dictionaryId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/{dictionaryId}/items/{itemId}")
    public ApiResult<CfgDictionaryItem> updateItem(
            @PathVariable Long dictionaryId,
            @PathVariable Long itemId,
            @Valid @RequestBody DictionaryModels.UpdateItemRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.updateItem(AuthenticationContextHolder.require(), dictionaryId, itemId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/{dictionaryId}/publication-check")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "DICTIONARY", actionCode = "PREVIEW")
    public ApiResult<DictionaryModels.PublicationCheck> check(@PathVariable Long dictionaryId) {
        return ApiResult.ok(service.check(AuthenticationContextHolder.require(), dictionaryId));
    }

    @PostMapping("/{dictionaryId}/publish")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "DICTIONARY", actionCode = "PUBLISH")
    public ApiResult<DictionaryModels.PublishedDictionary> publish(
            @PathVariable Long dictionaryId,
            @Valid @RequestBody DictionaryModels.PublishDictionaryRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), dictionaryId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/{dictionaryId}/versions")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "DICTIONARY", actionCode = "PREVIEW")
    public ApiResult<List<DictionaryModels.VersionSummary>> versions(@PathVariable Long dictionaryId) {
        return ApiResult.ok(service.versions(AuthenticationContextHolder.require(), dictionaryId));
    }
}
