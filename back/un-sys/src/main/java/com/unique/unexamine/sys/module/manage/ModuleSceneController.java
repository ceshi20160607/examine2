package com.unique.unexamine.sys.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模块列表场景入口，只接收请求并调用同模块 manage Service。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/scenes")
@Tag(name="模块列表场景")
public class ModuleSceneController {
    private final ModuleSceneService service;

    /** 注入场景业务服务。 */
    public ModuleSceneController(ModuleSceneService service) { this.service=service; }

    /** 保存本人视图或管理员维护的公共场景。 */
    @PostMapping
    @Operation(summary="保存模块列表场景")
    public ModuleSceneService.SceneView save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,
            @Valid @RequestBody ModuleSceneService.SaveRequest request) {
        return service.save(token,systemId,tenantId,moduleId,request);
    }

    /** 只列出当前账号能够执行的公共及个人场景。 */
    @GetMapping
    @Operation(summary="查询可用模块列表场景")
    public List<ModuleSceneService.SceneView> list(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) {
        return service.list(token,systemId,tenantId,moduleId);
    }
}
