package com.unique.unexamine.flow.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.flow.base.entity.UnFlow;
import com.unique.unexamine.flow.base.service.IUnFlowService;
import com.unique.unexamine.module.base.entity.UnModuleAction;
import com.unique.unexamine.flow.base.entity.UnModuleFlow;
import com.unique.unexamine.module.base.service.IUnModuleActionService;
import com.unique.unexamine.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.module.manage.ModuleSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.regex.Pattern;

/** 模块与流程定义之间的启动绑定；数据写入模块侧绑定表。 */
@Tag(name = "流程模块绑定", description = "模块事件与流程定义的绑定。")
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class FlowModuleBindingController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final IUnModuleActionService actions;
    private final IUnModuleFlowService flows;
    private final IUnFlowService flowDefinitions;

    public FlowModuleBindingController(ModuleSupport support, IUnModuleActionService actions,
            IUnModuleFlowService flows, IUnFlowService flowDefinitions) {
        this.support = support;
        this.actions = actions;
        this.flows = flows;
        this.flowDefinitions = flowDefinitions;
    }

    @Operation(summary = "查询流程绑定（流程模块绑定）")
    @GetMapping("/flow-bindings")
    public List<UnModuleFlow> flowBindings(@PathVariable long moduleId, @RequestParam long tenantId,
                                            HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return flows.list(new LambdaQueryWrapper<UnModuleFlow>().eq(UnModuleFlow::getTenantId, tenantId)
                .eq(UnModuleFlow::getModuleId, moduleId).eq(UnModuleFlow::getStatus, 1));
    }

    @Operation(summary = "新建流程绑定（流程模块绑定）")
    @PostMapping("/flow-bindings")
    public UnModuleFlow bindFlow(@PathVariable long moduleId, @RequestParam long tenantId,
                                 @Valid @RequestBody BindingForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        UnFlow definition = flowDefinitions.getById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus())
                || definition.getCurrentVersionId() == null) bad("流程定义不属于当前租户或未发布启用");
        action(moduleId, body.actionCode(), "flow");
        if (flows.count(new LambdaQueryWrapper<UnModuleFlow>().eq(UnModuleFlow::getTenantId, tenantId)
                .eq(UnModuleFlow::getModuleId, moduleId).eq(UnModuleFlow::getBindingCode, body.code())) > 0)
            conflict("流程绑定编码已存在");
        UnModuleFlow row = new UnModuleFlow();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setFlowId(body.definitionId());
        row.setBindingCode(body.code());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        flows.save(row);
        return flows.getById(row.getId());
    }

    @Operation(summary = "停用流程绑定（流程模块绑定）")
    @DeleteMapping("/flow-bindings/{id}")
    public void stopFlowBinding(@PathVariable long moduleId, @PathVariable long id,
                                @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleFlow row = flows.getById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("流程绑定不存在");
        row.setStatus((byte) 0);
        flows.updateById(row);
    }

    @Operation(summary = "修改流程绑定（流程模块绑定）")
    @PutMapping("/flow-bindings/{id}")
    public UnModuleFlow updateFlowBinding(@PathVariable long moduleId, @PathVariable long id,
                                           @RequestParam long tenantId, @Valid @RequestBody BindingForm body,
                                           HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleFlow row = flows.getById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("流程绑定不存在");
        if (!row.getBindingCode().equals(body.code())) conflict("绑定编码不可修改");
        UnFlow definition = flowDefinitions.getById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus())
                || definition.getCurrentVersionId() == null) bad("流程定义不属于当前租户或未发布启用");
        action(moduleId, body.actionCode(), "flow");
        row.setFlowId(body.definitionId());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        flows.updateById(row);
        return flows.getById(id);
    }


    private void action(long moduleId, String actionCode, String type) {
        if (actions.count(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId, moduleId)
                .eq(UnModuleAction::getActionCode, actionCode).eq(UnModuleAction::getActionType, type)
                .eq(UnModuleAction::getStatus, 1)) == 0) bad("触发动作不存在或类型不匹配");
    }

    private void read(HttpServletRequest request, long tenantId, long moduleId) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private void write(HttpServletRequest request, long tenantId, long moduleId) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private static void code(String code) { if (code == null || !CODE.matcher(code).matches()) bad("编码格式无效"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void missing(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    public record BindingForm(@NotBlank String code, @NotNull Long definitionId, @NotBlank String actionCode) { }
}
