package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowBinding;
import com.unique.examine.module.manage.SystemModuleFlowBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "自建系统态-module流程绑定")
@RestController
@RequestMapping("/v1/system/module/flow-bindings")
public class SystemModuleFlowBindingController {

    @Autowired
    private SystemModuleFlowBindingService systemModuleFlowBindingService;

    @Operation(summary = "模型流程绑定列表")
    @GetMapping("/apps/{appId}/models/{modelId}")
    public ApiResult<List<Map<String, Object>>> list(
            @PathVariable Long appId,
            @PathVariable Long modelId) {
        return ApiResult.ok(systemModuleFlowBindingService.listByModel(appId, modelId, AuthContextHolder.getPlatId()));
    }

    @Operation(summary = "可选流程模板")
    @GetMapping("/flow-temps")
    public ApiResult<List<Map<String, Object>>> flowTemps() {
        return ApiResult.ok(systemModuleFlowBindingService.listFlowTemps(AuthContextHolder.getPlatId()));
    }

    public record UpsertBody(Long id, Long appId, Long modelId, String triggerAction, Long tempId, Integer status) {}

    @Operation(summary = "保存模型流程绑定")
    @PostMapping("/upsert")
    public ApiResult<FlowBinding> upsert(@RequestBody UpsertBody body) {
        return ApiResult.ok(systemModuleFlowBindingService.upsert(
                AuthContextHolder.getPlatId(),
                new SystemModuleFlowBindingService.UpsertBindingCmd(
                        body.id(), body.appId(), body.modelId(), body.triggerAction(), body.tempId(), body.status())));
    }

    @Operation(summary = "删除绑定")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        systemModuleFlowBindingService.delete(AuthContextHolder.getPlatId(), id);
        return ApiResult.ok();
    }
}
