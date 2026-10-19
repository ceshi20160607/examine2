package com.unique.unexamine.approval.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.approval.base.entity.UnExamine;
import com.unique.unexamine.approval.base.mapper.UnExamineMapper;
import com.unique.unexamine.module.base.entity.UnModuleAction;
import com.unique.unexamine.approval.base.entity.UnModuleExamine;
import com.unique.unexamine.module.base.mapper.UnModuleActionMapper;
import com.unique.unexamine.approval.base.mapper.UnModuleExamineMapper;
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

/** 模块与审批定义之间的启动绑定；数据写入模块侧绑定表。 */
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class ExamineModuleBindingController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final UnModuleActionMapper actions;
    private final UnModuleExamineMapper examines;
    private final UnExamineMapper examineDefinitions;

    public ExamineModuleBindingController(ModuleSupport support, UnModuleActionMapper actions,
            UnModuleExamineMapper examines, UnExamineMapper examineDefinitions) {
        this.support = support;
        this.actions = actions;
        this.examines = examines;
        this.examineDefinitions = examineDefinitions;
    }

    @GetMapping("/examine-bindings")
    public List<UnModuleExamine> examineBindings(@PathVariable long moduleId, @RequestParam long tenantId,
                                                  HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return examines.selectList(new LambdaQueryWrapper<UnModuleExamine>().eq(UnModuleExamine::getTenantId, tenantId)
                .eq(UnModuleExamine::getModuleId, moduleId).eq(UnModuleExamine::getStatus, 1));
    }

    @PostMapping("/examine-bindings")
    public UnModuleExamine bindExamine(@PathVariable long moduleId, @RequestParam long tenantId,
                                      @Valid @RequestBody BindingForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        UnExamine definition = examineDefinitions.selectById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus()))
            bad("审批定义不属于当前租户或未启用");
        action(moduleId, body.actionCode(), "examine");
        if (examines.selectCount(new LambdaQueryWrapper<UnModuleExamine>().eq(UnModuleExamine::getTenantId, tenantId)
                .eq(UnModuleExamine::getModuleId, moduleId).eq(UnModuleExamine::getBindingCode, body.code())) > 0)
            conflict("审批绑定编码已存在");
        UnModuleExamine row = new UnModuleExamine();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setExamineId(body.definitionId());
        row.setBindingCode(body.code());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        examines.insert(row);
        return examines.selectById(row.getId());
    }

    @DeleteMapping("/examine-bindings/{id}")
    public void stopExamineBinding(@PathVariable long moduleId, @PathVariable long id,
                                   @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleExamine row = examines.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("审批绑定不存在");
        row.setStatus((byte) 0);
        examines.updateById(row);
    }

    @PutMapping("/examine-bindings/{id}")
    public UnModuleExamine updateExamineBinding(@PathVariable long moduleId, @PathVariable long id,
                                                 @RequestParam long tenantId, @Valid @RequestBody BindingForm body,
                                                 HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleExamine row = examines.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("审批绑定不存在");
        if (!row.getBindingCode().equals(body.code())) conflict("绑定编码不可修改");
        UnExamine definition = examineDefinitions.selectById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus()))
            bad("审批定义不属于当前租户或未启用");
        action(moduleId, body.actionCode(), "examine");
        row.setExamineId(body.definitionId());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        examines.updateById(row);
        return examines.selectById(id);
    }

    private void action(long moduleId, String actionCode, String type) {
        if (actions.selectCount(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId, moduleId)
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
