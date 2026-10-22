package com.unique.unexamine.sys.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 模块默认和自定义动作共用菜单配置与执行入口，Controller 不执行字段查询或保存。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/actions")
@Tag(name="模块菜单动作")
public class ModuleActionController {
    private final ModuleMenuService menus;
    private final ModuleRecordService records;
    /** 注入配置与业务记录执行服务。 */
    public ModuleActionController(ModuleMenuService menus,ModuleRecordService records) { this.menus=menus; this.records=records; }

    /** 后台查看十个默认菜单和自定义动作。 */
    @GetMapping("/configuration")
    @Operation(summary="读取模块动作配置")
    public List<ModuleMenuService.ActionView> configuration(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId) {
        return menus.configuration(token,systemId,tenantId,moduleId);
    }
    /** 运行态只返回已发布、启用且角色获权的菜单。 */
    @GetMapping
    @Operation(summary="读取可用模块动作")
    public List<ModuleRecordService.RuntimeAction> available(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId) {
        return records.availableActions(token,systemId,tenantId,moduleId);
    }
    /** 保存动作草稿不立即改变运行态。 */
    @PostMapping
    @Operation(summary="保存模块动作草稿")
    public ModuleMenuService.ActionView save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId,
            @Valid @RequestBody ModuleMenuService.SaveRequest request) { return menus.save(token,systemId,tenantId,moduleId,request); }
    /** 发布动作并刷新授权缓存。 */
    @PostMapping("/{menuId}/publish")
    @Operation(summary="发布模块动作")
    public ModuleMenuService.ActionView publish(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId,
            @PathVariable Long menuId) { return menus.publish(token,systemId,tenantId,moduleId,menuId); }
    /** 自定义字段输入复用字段类型、字典和原布局。 */
    @GetMapping("/{menuId}/records/{recordId}/form")
    @Operation(summary="读取动作字段表单")
    public ModuleRecordService.RecordInformation form(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId,
            @PathVariable Long menuId,@PathVariable Long recordId) { return records.actionForm(token,systemId,tenantId,moduleId,menuId,recordId); }
    /** 执行字段更新、归属转移或按映射转化，全部交给业务事务。 */
    @PostMapping("/{menuId}/records/{recordId}/execute")
    @Operation(summary="执行模块动作")
    public ModuleRecordService.ActionResult execute(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long moduleId,
            @PathVariable Long menuId,@PathVariable Long recordId,@Valid @RequestBody ModuleRecordService.ExecuteRequest request) {
        return records.execute(token,systemId,tenantId,moduleId,menuId,recordId,request);
    }
}
