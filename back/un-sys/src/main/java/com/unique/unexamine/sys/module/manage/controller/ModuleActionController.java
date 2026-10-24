package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.ActionView;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RuntimeAction;
import com.unique.unexamine.sys.module.manage.service.ModuleExportService;
import com.unique.unexamine.sys.module.manage.service.ModuleMenuService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 模块默认和自定义动作共用菜单配置与执行入口，Controller 不执行字段查询或保存。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/actions")
@Tag(name="模块菜单动作")
public class ModuleActionController {
    private final ModuleMenuService menus;
    private final ModuleRecordService records;
    private final ModuleExportService exports;
    /** 注入配置与业务记录执行服务。 */
    public ModuleActionController(ModuleMenuService menus,ModuleRecordService records,ModuleExportService exports) {
        this.menus=menus; this.records=records; this.exports=exports;
    }

    /** 后台查看十个默认菜单和自定义动作。 */
    @GetMapping("/configuration")
    @Operation(summary="读取模块动作配置")
    public Result<List<ActionView>> configuration(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(menus.configuration(token,systemId,tenantId,moduleId));
    }
    /** 运行态只返回已生效、启用且角色获权的菜单。 */
    @GetMapping
    @Operation(summary="读取可用模块动作")
    public Result<List<RuntimeAction>> available(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(records.availableActions(token,systemId,tenantId,moduleId));
    }
    /** 保存动作配置立即改变运行态。 */
    @PostMapping
    @Operation(summary="保存模块动作配置")
    public Result<ActionView> save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId,
            @Valid @RequestBody SaveRequest request) { return Result.ok(menus.save(token,systemId,tenantId,moduleId,request)); }

    /** 当前筛选的小批量 CSV 由导出菜单独立授权，列受字段导出许可限制。 */
    @PostMapping("/export")
    @Operation(summary="导出当前筛选的模块记录")
    public ResponseEntity<byte[]> export(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long moduleId,@RequestBody ExportRequest request) {
        var file=exports.export(token,systemId,tenantId,moduleId,request);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(file.content());
    }
}
