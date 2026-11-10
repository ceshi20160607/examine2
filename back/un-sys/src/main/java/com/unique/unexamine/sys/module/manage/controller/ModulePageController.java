package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModulePageDto.PageView;
import com.unique.unexamine.sys.module.manage.dto.ModulePageDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.service.ModulePageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 模块页面配置按用途独立保存字段展示位置。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/pages")
@Tag(name="模块页面配置")
public class ModulePageController {
    private final ModulePageService service;
    public ModulePageController(ModulePageService service) { this.service=service; }

    /** 读取一类页面的当前配置，关联页面额外传入来源关联字段主键。 */
    @GetMapping("/{pageKind}")
    @Operation(summary="读取模块页面字段布局")
    public Result<PageView> get(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@PathVariable String pageKind,
            @RequestParam(required=false) Long relationFieldId) {
        return Result.ok(service.configuration(token,systemId,tenantId,moduleId,pageKind,relationFieldId));
    }

    /** 保存页面字段布局后立即影响对应列表、表单或详情。 */
    @PutMapping("/{pageKind}")
    @Operation(summary="保存模块页面字段布局")
    public Result<PageView> save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@PathVariable String pageKind,
            @RequestParam(required=false) Long relationFieldId,@Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token,systemId,tenantId,moduleId,pageKind,relationFieldId,request));
    }
}
