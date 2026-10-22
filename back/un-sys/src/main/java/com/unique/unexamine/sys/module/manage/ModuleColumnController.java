package com.unique.unexamine.sys.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人列表表头配置接口只调用模块业务 Service。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/columns")
@Tag(name="模块个人表头")
public class ModuleColumnController {
    private final ModuleColumnService service;

    /** 注入个人表头业务服务。 */
    public ModuleColumnController(ModuleColumnService service) { this.service=service; }

    /** 替换当前账号的列顺序、宽度、显隐及固定配置。 */
    @PutMapping
    @Operation(summary="保存个人列表表头")
    public List<ModuleColumnService.Preference> save(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,
            @RequestBody List<ModuleColumnService.Preference> request) {
        return service.save(token,systemId,tenantId,moduleId,request);
    }

    /** 只读取当前账号仍有权限查看的表头配置。 */
    @GetMapping
    @Operation(summary="读取个人列表表头")
    public List<ModuleColumnService.Preference> list(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) {
        return service.list(token,systemId,tenantId,moduleId);
    }
}
