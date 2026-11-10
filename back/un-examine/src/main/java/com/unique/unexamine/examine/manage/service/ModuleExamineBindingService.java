package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.ExamineDefinitionAccess;
import com.unique.unexamine.core.module.ExamineDefinitionAudit;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.examine.base.entity.UnExamine;
import com.unique.unexamine.examine.base.entity.UnModuleExamine;
import com.unique.unexamine.examine.base.service.IUnExamineService;
import com.unique.unexamine.examine.base.service.IUnModuleExamineService;
import com.unique.unexamine.examine.manage.dto.ModuleExamineDto.SaveRequest;
import com.unique.unexamine.examine.manage.dto.ModuleExamineDto.View;
import com.unique.unexamine.sys.module.manage.service.ModulePageStatusService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块事件与审批定义绑定管理；运行中的审批实例只读取自身已保存的发布快照。 */
@Service
public class ModuleExamineBindingService {
    private final ExamineDefinitionAccess access;
    private final ExamineDefinitionAudit audit;
    private final IUnModuleExamineService bindings;
    private final IUnExamineService examines;
    private final ObjectMapper json;
    private final ModulePageStatusService pageStatuses;

    public ModuleExamineBindingService(ExamineDefinitionAccess access, ExamineDefinitionAudit audit,
                                       IUnModuleExamineService bindings, IUnExamineService examines,
                                       ObjectMapper json,ModulePageStatusService pageStatuses) {
        this.access = access;
        this.audit = audit;
        this.bindings = bindings;
        this.examines = examines;
        this.json = json;
        this.pageStatuses = pageStatuses;
    }

    /** 查询当前系统租户的绑定；传入模块时只返回该模块的配置。 */
    public List<View> list(String token, Long systemId, Long tenantId, Long moduleId) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        if (moduleId != null) access.validateModule(context, moduleId);
        LambdaQueryWrapper<UnModuleExamine> where = new LambdaQueryWrapper<UnModuleExamine>()
                .eq(UnModuleExamine::getSystemId, context.systemId())
                .eq(UnModuleExamine::getTenantId, context.tenantId());
        if (moduleId != null) where.eq(UnModuleExamine::getModuleId, moduleId);
        return bindings.list(where.orderByAsc(UnModuleExamine::getModuleId)
                        .orderByAsc(UnModuleExamine::getTriggerCode).orderByAsc(UnModuleExamine::getId))
                .stream().map(this::view).toList();
    }

    /** 查询一个绑定详情。 */
    public View query(String token, Long systemId, Long tenantId, Long bindingId) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        return view(owned(context, bindingId));
    }

    /** 保存绑定并立即启用；已运行实例不受后续绑定修改影响。 */
    @Transactional(rollbackFor = Exception.class)
    public View save(String token, Long systemId, Long tenantId, SaveRequest request) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        if (request == null || request.moduleId() == null || request.examineId() == null
                || request.triggerCode() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批绑定资料不能为空");
        access.validateModule(context, request.moduleId());
        access.validateActionMenu(context, request.moduleId(), request.actionMenuId());
        pageStatuses.requireActive(context.systemId(),context.tenantId(),request.moduleId(),request.statusGroupId());
        validateTrigger(request.triggerCode());
        validateExamine(context, request.moduleId(), request.examineId());
        String conditionJson = writeCondition(request.condition());
        try {
            TriggerConditionMatcher.validate(json, conditionJson);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        access.validateFieldKeys(context, request.moduleId(), TriggerConditionMatcher.fieldKeys(json, conditionJson));
        access.validateConditionValues(context, request.moduleId(), conditionJson);

        UnModuleExamine binding = request.id() == null ? null : owned(context, request.id());
        View before = binding == null ? null : view(binding);
        if (binding == null) {
            binding = new UnModuleExamine();
            binding.setSystemId(context.systemId());
            binding.setTenantId(context.tenantId());
            binding.setCreateTime(LocalDateTime.now());
        }
        // 编辑绑定时也要写入已校验的目标模块，避免接口返回成功而运行时仍命中旧模块。
        binding.setModuleId(request.moduleId());
        binding.setExamineId(request.examineId());
        binding.setStatusGroupId(request.statusGroupId());
        binding.setActionMenuId(request.actionMenuId());
        binding.setTriggerCode(request.triggerCode().trim().toUpperCase(Locale.ROOT));
        binding.setConditionJson(conditionJson);
        binding.setStatus("ACTIVE");
        binding.setUpdateTime(LocalDateTime.now());
        bindings.addOrUpdate(binding);
        if (before!=null) bindings.update(new LambdaUpdateWrapper<UnModuleExamine>()
                .eq(UnModuleExamine::getId,binding.getId())
                .set(UnModuleExamine::getStatusGroupId,request.statusGroupId())
                .set(UnModuleExamine::getActionMenuId,request.actionMenuId())
                .set(UnModuleExamine::getConditionJson,conditionJson));
        View after = view(binding);
        audit.record(context, "MODULE_EXAMINE_BINDING_SAVE", binding.getId(), detail(before, after));
        return after;
    }

    /** 停用绑定；审批定义和已经创建的审批实例保持不变。 */
    @Transactional(rollbackFor = Exception.class)
    public View disable(String token, Long systemId, Long tenantId, Long bindingId) {
        ExamineDefinitionAccess.Context context = access.requireAdmin(token, systemId, tenantId);
        UnModuleExamine binding = owned(context, bindingId);
        View before = view(binding);
        binding.setStatus("DISABLED");
        binding.setUpdateTime(LocalDateTime.now());
        bindings.addOrUpdate(binding);
        View after = view(binding);
        audit.record(context, "MODULE_EXAMINE_BINDING_DISABLE", bindingId, detail(before, after));
        return after;
    }

    private UnModuleExamine owned(ExamineDefinitionAccess.Context context, Long id) {
        UnModuleExamine binding = bindings.queryById(id);
        if (binding == null || !context.systemId().equals(binding.getSystemId())
                || !context.tenantId().equals(binding.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "模块审批绑定不存在");
        return binding;
    }

    private void validateExamine(ExamineDefinitionAccess.Context context, Long moduleId, Long examineId) {
        UnExamine examine = examines.queryById(examineId);
        if (examine == null || !context.systemId().equals(examine.getSystemId())
                || !context.tenantId().equals(examine.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批定义不存在");
        if (!"ACTIVE".equals(examine.getStatus()) || examine.getPublishedVersion() == null
                || examine.getPublishedVersion() < 1 || examine.getPublishedSnapshotJson() == null
                || examine.getPublishedSnapshotJson().isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批定义尚未发布或已停用");
        if (examine.getModuleId() != null && !moduleId.equals(examine.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批定义不属于当前业务模块");
    }

    private void validateTrigger(String triggerCode) {
        if (triggerCode == null || !triggerCode.trim().matches("[A-Za-z][A-Za-z0-9_]{1,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批触发事件编码只能使用字母、数字和下划线，且必须以字母开头");
    }

    private String writeCondition(JsonNode condition) {
        if (condition == null || condition.isNull()) return null;
        try { return json.writeValueAsString(condition); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批触发条件无法保存", ex); }
    }

    private View view(UnModuleExamine row) {
        JsonNode condition = null;
        try { if (row.getConditionJson() != null) condition = json.readTree(row.getConditionJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批触发条件已损坏", ex); }
        return new View(row.getId(), row.getSystemId(), row.getTenantId(), row.getModuleId(), row.getExamineId(),row.getStatusGroupId(),
                row.getActionMenuId(), row.getTriggerCode(), condition, row.getStatus(), row.getCreateTime(), row.getUpdateTime());
    }

    private String detail(View before, View after) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("before", before);
        values.put("after", after);
        try { return json.writeValueAsString(values); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批绑定审计内容无法保存", ex); }
    }
}
