package com.unique.examine.web.controller;

import com.unique.examine.core.entity.PlatConfig;
import com.unique.examine.core.service.PlatConfigService;
import com.unique.examine.web.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "平台配置")
@RestController
@RequestMapping("/api/v1/platform/configs")
public class PlatConfigController {

    private final PlatConfigService platConfigService;

    public PlatConfigController(PlatConfigService platConfigService) {
        this.platConfigService = platConfigService;
    }

    @Operation(summary = "列表")
    @GetMapping
    public ApiResult<List<PlatConfig>> list() {
        return ApiResult.ok(platConfigService.listAll());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public ApiResult<PlatConfig> get(@PathVariable Long id) {
        return ApiResult.ok(platConfigService.getById(id));
    }

    @Operation(summary = "新增或更新（按 id 是否为空）")
    @PostMapping
    public ApiResult<Void> save(@RequestBody PlatConfig body) {
        platConfigService.saveOrUpdateConfig(body);
        return ApiResult.ok();
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody PlatConfig body) {
        body.setId(id);
        platConfigService.saveOrUpdateConfig(body);
        return ApiResult.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        platConfigService.removeByKey(id);
        return ApiResult.ok();
    }
}
