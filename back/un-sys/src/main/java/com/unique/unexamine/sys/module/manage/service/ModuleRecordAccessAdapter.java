package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.core.module.ModuleRecordAccess;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ExamineBusinessDataUpdater;
import com.unique.unexamine.core.module.ExamineBusinessUpdateContext;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.SaveRecordRequest;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 将系统模块的统一权限结果提供给 examine 等独立业务模块。 */
@Component
public class ModuleRecordAccessAdapter implements ModuleRecordAccess, ExamineBusinessDataUpdater {
    private final ModulePermissionService permissions;
    private final IUnModuleRecordService records;
    private final ModuleRecordService moduleRecords;
    private final SystemManageService systems;

    public ModuleRecordAccessAdapter(ModulePermissionService permissions, IUnModuleRecordService records,
                                     ModuleRecordService moduleRecords, SystemManageService systems) {
        this.permissions = permissions;
        this.records = records;
        this.moduleRecords = moduleRecords;
        this.systems = systems;
    }

    @Override
    public Access require(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !systemId.equals(record.getSystemId()) || !moduleId.equals(record.getModuleId())
                || !access.context().tenantId().equals(record.getTenantId())
                || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        if (!permissions.teamAllows(access,record)) access.policy().requireOwner(record.getOwnerUserId());
        return new Access(access.context().systemId(), access.context().tenantId(), access.accountId(),
                access.context().owner(), access.context().tenantAdmin());
    }

    /**
     * 审批办理只核对系统与业务记录边界，不读取模块动作或负责人数据权限。
     * 节点字段快照在 examine 运行服务中继续限制可见和可编辑范围。
     */
    @Override
    public Access requireProcessBoundary(String token, Long systemId, Long tenantId,
                                         Long moduleId, Long recordId) {
        var context = systems.context(token, systemId, tenantId);
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !systemId.equals(record.getSystemId()) || !moduleId.equals(record.getModuleId())
                || !context.tenantId().equals(record.getTenantId()) || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        return new Access(context.systemId(), context.tenantId(), context.accountId(),
                context.owner(), context.tenantAdmin());
    }

    /** 审批工作台先校验当前系统租户，再按待办实例核对具体业务记录。 */
    @Override
    public Access requireProcessContext(String token, Long systemId, Long tenantId) {
        var context = systems.context(token, systemId, tenantId);
        return new Access(context.systemId(), context.tenantId(), context.accountId(),
                context.owner(), context.tenantAdmin());
    }

    /** 审批外部待办只使用当前有效记录的系统、租户、模块和负责人，不信任实例内旧归属。 */
    @Override
    public List<RecordOwner> activeRecordOwners(Long systemId, Long tenantId, List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) return List.of();
        return records.listByIds(recordIds).stream()
                .filter(record -> Objects.equals(record.getSystemId(), systemId)
                        && Objects.equals(record.getTenantId(), tenantId)
                        && record.getDeletedTime() == null)
                .map(record -> new RecordOwner(record.getId(), record.getModuleId(),
                        record.getOwnerUserId(), record.getOwnerDeptId()))
                .toList();
    }

    @Override
    public void update(String token, Long systemId, Long tenantId, Long moduleId, Long recordId,
                       java.util.Map<String, Object> values) {
        moduleRecords.save(token, systemId, tenantId, moduleId,
                new SaveRecordRequest(recordId, null, values));
    }

    /** 审批中间态只校验字段，不重新执行普通动作的数据权限，也不落业务主表。 */
    @Override
    public void validateProcessValues(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                      Long accountId, java.util.Map<String, Object> values) {
        moduleRecords.validateByProcess(new ExamineBusinessUpdateContext(systemId, tenantId, moduleId,
                recordId, accountId, "EXAMINE_FIELD_VALIDATE", values));
    }

    @Override
    public void update(ExamineBusinessUpdateContext context) {
        moduleRecords.saveByProcess(context);
    }

    /** 审批自动节点先复用模块字段规则校验，校验阶段不写入业务主表。 */
    @Override
    public void validate(ExamineBusinessUpdateContext context) {
        moduleRecords.validateByProcess(context);
    }
}
