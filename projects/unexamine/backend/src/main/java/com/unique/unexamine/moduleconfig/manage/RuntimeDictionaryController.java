package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime/dictionaries")
public class RuntimeDictionaryController {
    private final DictionaryConfigurationService service;

    public RuntimeDictionaryController(DictionaryConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    public ApiResult<DictionaryModels.RuntimeDictionary> runtime(
            @PathVariable String code,
            @RequestParam(required = false) Long parentId) {
        return ApiResult.ok(service.runtime(AuthenticationContextHolder.require(), code, parentId));
    }

    @GetMapping("/by-id/{dictionaryId}")
    public ApiResult<DictionaryModels.RuntimeDictionary> runtimeById(
            @PathVariable Long dictionaryId,
            @RequestParam(required = false) Long parentId) {
        return ApiResult.ok(service.runtime(AuthenticationContextHolder.require(), dictionaryId, parentId));
    }
}
