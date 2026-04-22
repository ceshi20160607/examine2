package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleDict;
import com.unique.examine.module.entity.po.ModuleDictItem;
import com.unique.examine.web.service.SystemModuleDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "自建系统态-module字典")
@RestController
@RequestMapping("/v1/system/module/dicts")
public class SystemModuleDictController {

    @Autowired
    private SystemModuleDictService systemModuleDictService;

    @Operation(summary = "字典列表（按 appId）")
    @GetMapping("/apps/{appId}")
    public ApiResult<List<ModuleDict>> listDicts(@PathVariable("appId") Long appId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleDictService.listDicts(appId, platId));
    }

    public record UpsertDictBody(Long id, String dictCode, String dictName, Integer status, String remark) {}

    @Operation(summary = "新增/更新字典（按 appId）")
    @PostMapping("/apps/{appId}/upsert")
    public ApiResult<ModuleDict> upsertDict(@PathVariable("appId") Long appId, @RequestBody UpsertDictBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleDictService.upsertDict(appId, platId, body));
    }

    @Operation(summary = "字典项列表（按 dictId）")
    @GetMapping("/{dictId}/items")
    public ApiResult<List<ModuleDictItem>> listItems(@PathVariable("dictId") Long dictId) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleDictService.listItems(dictId, platId));
    }

    public record UpsertItemBody(Long id, String itemValue, String itemLabel, Integer sortNo, Integer status) {}

    @Operation(summary = "新增/更新字典项（按 dictId）")
    @PostMapping("/{dictId}/items/upsert")
    public ApiResult<ModuleDictItem> upsertItem(@PathVariable("dictId") Long dictId, @RequestBody UpsertItemBody body) {
        Long platId = AuthContextHolder.getPlatId();
        return ApiResult.ok(systemModuleDictService.upsertItem(dictId, platId, body));
    }

    public record DeleteIdsBody(List<Long> ids) {}

    @Operation(summary = "删除字典（按 id 列表）")
    @PostMapping("/delete")
    public ApiResult<Void> deleteDicts(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleDictService.deleteDicts(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }

    @Operation(summary = "删除字典项（按 id 列表）")
    @PostMapping("/items/delete")
    public ApiResult<Void> deleteItems(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        systemModuleDictService.deleteItems(platId, body == null ? null : body.ids());
        return ApiResult.ok();
    }
}

