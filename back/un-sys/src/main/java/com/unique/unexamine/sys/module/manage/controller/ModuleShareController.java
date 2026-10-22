package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.sys.module.manage.service.ModuleShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 来源租户配置记录或数据范围的共享授权，Controller 只交给业务 Service。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/shares")
@Tag(name="模块记录共享")
public class ModuleShareController {
    private final ModuleShareService service;

    /** 注入共享业务服务。 */
    public ModuleShareController(ModuleShareService service) { this.service=service; }

    /** 来源租户管理员将指定记录或范围共享给同系统目标租户，默认只读。 */
    @PostMapping
    @Operation(summary="共享指定业务记录或数据范围")
    public ModuleShareService.ShareView create(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@Valid @RequestBody ModuleShareService.SaveRequest request) {
        return service.create(token,systemId,tenantId,moduleId,request);
    }

    /** 来源租户管理员查看当前与已撤销授权。 */
    @GetMapping
    @Operation(summary="分页读取业务记录共享授权")
    public ModuleShareService.SharePage list(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="20") long size) {
        return service.list(token,systemId,tenantId,moduleId,page,size);
    }

    /** 来源租户管理员撤销共享，原始业务记录和历史日志仍保留。 */
    @DeleteMapping("/{shareId}")
    @Operation(summary="撤销业务记录共享")
    public ModuleShareService.ShareView revoke(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@PathVariable Long shareId) {
        return service.revoke(token,systemId,tenantId,moduleId,shareId);
    }
}
