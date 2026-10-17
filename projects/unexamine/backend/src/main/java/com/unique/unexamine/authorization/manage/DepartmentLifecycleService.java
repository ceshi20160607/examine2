package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.runtimedata.base.entity.BizRecordChildRow;
import com.unique.unexamine.runtimedata.base.entity.BizRecordChildValue;
import com.unique.unexamine.runtimedata.base.entity.BizRecordOwnerHistory;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordValue;
import com.unique.unexamine.runtimedata.base.service.BizRecordChildRowBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordChildValueBaseService;
import com.unique.unexamine.runtimedata.base.service.BizRecordOwnerHistoryBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordValueBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 部门停用的业务处理：先核对真实引用，再在同一事务内转移可直接变更的归属。 */
@Service
public class DepartmentLifecycleService {
    private final SystemDepartmentBaseService departmentService;
    private final SystemTenantMemberBaseService memberService;
    private final BusinessRecordBaseService recordService;
    private final BusinessRecordValueBaseService valueService;
    private final BizRecordChildRowBaseService childRowService;
    private final BizRecordChildValueBaseService childValueService;
    private final BizRecordOwnerHistoryBaseService historyService;
    private final RuntimeDataService runtimeDataService;
    private final DepartmentConfigurationReferences configurationReferences;
    private final AuditRecorder auditRecorder;

    public DepartmentLifecycleService(SystemDepartmentBaseService departmentService,
                                      SystemTenantMemberBaseService memberService,
                                      BusinessRecordBaseService recordService,
                                      BusinessRecordValueBaseService valueService,
                                      BizRecordChildRowBaseService childRowService,
                                      BizRecordChildValueBaseService childValueService,
                                      BizRecordOwnerHistoryBaseService historyService,
                                      RuntimeDataService runtimeDataService,
                                      DepartmentConfigurationReferences configurationReferences,
                                      AuditRecorder auditRecorder) {
        this.departmentService = departmentService;
        this.memberService = memberService;
        this.recordService = recordService;
        this.valueService = valueService;
        this.childRowService = childRowService;
        this.childValueService = childValueService;
        this.historyService = historyService;
        this.runtimeDataService = runtimeDataService;
        this.configurationReferences = configurationReferences;
        this.auditRecorder = auditRecorder;
    }

    /** 返回当前系统和租户中的引用，历史转移日志保持原值且不算待处理引用。 */
    @Transactional(readOnly = true)
    public SystemAuthorizationModels.DepartmentImpact impact(AuthenticatedContext context, Long departmentId) {
        SystemDepartment department = requireDepartment(context, departmentId);
        return inspect(context, department).impact();
    }

    /** 部门有配置或下级引用时先阻止停用；业务归属和部门字段可选择目标一并转移。 */
    @Transactional
    public SystemAuthorizationModels.DepartmentView deactivate(
            AuthenticatedContext context, Long departmentId,
            SystemAuthorizationModels.DepartmentDeactivateRequest request, String traceId) {
        SystemDepartment department = requireDepartment(context, departmentId);
        if (!"ACTIVE".equals(department.getStatus())) {
            throw new DomainException("DEPARTMENT_ALREADY_DISABLED", "该部门已停用，请刷新页面", HttpStatus.CONFLICT);
        }
        if (!Objects.equals(request.expectedVersion(), department.getVersion())) {
            throw new DomainException("DEPARTMENT_VERSION_CONFLICT", "部门已被修改，请重新检查影响范围", HttpStatus.CONFLICT);
        }
        ReferenceSnapshot snapshot = inspect(context, department);
        SystemAuthorizationModels.DepartmentImpact impact = snapshot.impact();
        if (snapshot.blockingCount() > 0) {
            throw new DomainException("DEPARTMENT_REFERENCED", "仍有子部门或业务配置引用该部门，请先处理影响清单", HttpStatus.CONFLICT);
        }
        SystemDepartment replacement = null;
        if (snapshot.transferCount() > 0) {
            if (request.replacementDepartmentId() == null) {
                throw new DomainException("DEPARTMENT_REPLACEMENT_REQUIRED", "该部门仍有成员或业务数据，请选择接收部门", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            replacement = requireDepartment(context, request.replacementDepartmentId());
            if (!"ACTIVE".equals(replacement.getStatus()) || replacement.getId().equals(departmentId)
                    || replacement.getPathCode().startsWith(department.getPathCode() + "/")) {
                throw new DomainException("DEPARTMENT_REPLACEMENT_INVALID", "接收部门必须是同租户的其他有效部门，且不能是下级部门", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } else if (request.replacementDepartmentId() != null) {
            throw new DomainException("DEPARTMENT_REPLACEMENT_UNNEEDED", "该部门没有需要转移的归属，请直接停用", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (replacement != null) {
            for (SystemTenantMember member : snapshot.members()) {
                member.setDepartmentId(replacement.getId());
                if (memberService.updateById(member) != 1) throw transferConflict();
            }
            Set<Long> directRecordIds = new LinkedHashSet<>();
            snapshot.records().forEach(record -> directRecordIds.add(record.getId()));
            Set<Long> fieldRecordIds = new LinkedHashSet<>();
            snapshot.mainValues().forEach(value -> fieldRecordIds.add(value.getRecordId()));
            for (BizRecordChildValue value : snapshot.childValues()) {
                BizRecordChildRow row = childRowService.selectById(value.getChildRowId());
                if (row == null || !context.systemId().equals(row.getSystemId())
                        || !context.tenantId().equals(row.getTenantId())) throw transferConflict();
                fieldRecordIds.add(row.getRecordId());
            }
            Set<Long> affectedRecordIds = new LinkedHashSet<>(directRecordIds);
            affectedRecordIds.addAll(fieldRecordIds);
            Map<Long, BusinessRecord> affectedRecords = new LinkedHashMap<>();
            for (Long recordId : affectedRecordIds) {
                BusinessRecord record = recordService.selectById(recordId);
                if (record == null || !context.systemId().equals(record.getSystemId())
                        || !context.tenantId().equals(record.getTenantId())) throw transferConflict();
                if (directRecordIds.contains(recordId)) {
                    BizRecordOwnerHistory history = new BizRecordOwnerHistory();
                    history.setRecordId(recordId);
                    history.setFromOwnerMemberId(record.getOwnerMemberId());
                    history.setToOwnerMemberId(record.getOwnerMemberId());
                    history.setFromDepartmentId(departmentId);
                    history.setToDepartmentId(replacement.getId());
                    history.setReason("部门停用转移");
                    history.setChangedByMemberId(context.memberId());
                    record.setDepartmentId(replacement.getId());
                    historyService.insert(history);
                }
                record.setUpdatedByMemberId(context.memberId());
                record.setUpdateTime(null);
                if (recordService.updateById(record) != 1) throw transferConflict();
                affectedRecords.put(recordId, record);
            }
            for (BusinessRecordValue value : snapshot.mainValues()) {
                value.setValueReferenceId(replacement.getId());
                if (valueService.updateById(value) != 1) throw transferConflict();
            }
            for (BizRecordChildValue value : snapshot.childValues()) {
                value.setValueReferenceId(replacement.getId());
                if (childValueService.updateById(value) != 1) throw transferConflict();
            }
            for (BusinessRecord record : affectedRecords.values()) {
                if (fieldRecordIds.contains(record.getId())) {
                    runtimeDataService.reindexAfterDepartmentReplacement(context, record);
                }
                auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                        "BUSINESS_RECORD_DEPARTMENT_REASSIGNED", "BUSINESS_RECORD", record.getId().toString(), "SUCCESS",
                        Map.of("fromDepartmentId", departmentId, "toDepartmentId", replacement.getId(),
                                "reason", "部门停用转移"));
            }
        }
        department.setStatus("DISABLED");
        department.setUpdateTime(null);
        if (departmentService.updateById(department) != 1) throw transferConflict();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("departmentName", department.getName());
        detail.put("replacementDepartmentId", replacement == null ? "" : replacement.getId());
        detail.put("memberCount", snapshot.members().size());
        detail.put("recordCount", snapshot.records().size());
        detail.put("mainFieldCount", snapshot.mainValues().size());
        detail.put("childFieldCount", snapshot.childValues().size());
        detail.put("impact", impact.references());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DEPARTMENT_DISABLED", "SYSTEM_DEPARTMENT", departmentId.toString(), "SUCCESS", detail);
        return view(departmentService.selectById(departmentId));
    }

    /** 恢复部门只开放新的业务选择，不修改历史转移和审计记录。 */
    @Transactional
    public SystemAuthorizationModels.DepartmentView activate(
            AuthenticatedContext context, Long departmentId,
            SystemAuthorizationModels.DepartmentActivateRequest request, String traceId) {
        SystemDepartment department = requireDepartment(context, departmentId);
        if (!"DISABLED".equals(department.getStatus())) {
            throw new DomainException("DEPARTMENT_ALREADY_ACTIVE", "该部门已启用，请刷新页面", HttpStatus.CONFLICT);
        }
        if (!Objects.equals(request.expectedVersion(), department.getVersion())) {
            throw new DomainException("DEPARTMENT_VERSION_CONFLICT", "部门已被修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (department.getParentId() != null) {
            SystemDepartment parent = requireDepartment(context, department.getParentId());
            if (!"ACTIVE".equals(parent.getStatus())) {
                throw new DomainException("DEPARTMENT_PARENT_DISABLED", "请先启用上级部门", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        department.setStatus("ACTIVE");
        department.setUpdateTime(null);
        if (departmentService.updateById(department) != 1) throw transferConflict();
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_DEPARTMENT_ACTIVATED", "SYSTEM_DEPARTMENT", departmentId.toString(), "SUCCESS",
                Map.of("departmentName", department.getName()));
        return view(departmentService.selectById(departmentId));
    }

    private ReferenceSnapshot inspect(AuthenticatedContext context, SystemDepartment department) {
        Long id = department.getId();
        List<SystemDepartment> children = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                .eq(SystemDepartment::getSystemId, context.systemId())
                .eq(SystemDepartment::getTenantId, context.tenantId())
                .eq(SystemDepartment::getParentId, id));
        List<SystemTenantMember> members = memberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, context.systemId())
                .eq(SystemTenantMember::getTenantId, context.tenantId())
                .eq(SystemTenantMember::getDepartmentId, id));
        // 软删除和归档业务记录仍可恢复，不能因当前列表隐藏而漏掉其部门引用。
        List<BusinessRecord> records = recordService.selectList(Wrappers.<BusinessRecord>lambdaQuery()
                .eq(BusinessRecord::getSystemId, context.systemId())
                .eq(BusinessRecord::getTenantId, context.tenantId())
                .eq(BusinessRecord::getDepartmentId, id));
        List<BusinessRecordValue> candidateMainValues = valueService.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                .eq(BusinessRecordValue::getValueType, "DEPARTMENT")
                .eq(BusinessRecordValue::getValueReferenceId, id));
        Set<Long> mainRecordIds = new LinkedHashSet<>();
        candidateMainValues.forEach(value -> mainRecordIds.add(value.getRecordId()));
        Set<Long> ownedRecordIds = mainRecordIds.isEmpty() ? Set.of() : recordService.selectList(
                Wrappers.<BusinessRecord>lambdaQuery().eq(BusinessRecord::getSystemId, context.systemId())
                        .eq(BusinessRecord::getTenantId, context.tenantId())
                        .in(BusinessRecord::getId, mainRecordIds)).stream()
                .map(BusinessRecord::getId).collect(java.util.stream.Collectors.toSet());
        List<BusinessRecordValue> mainValues = candidateMainValues.stream()
                .filter(value -> ownedRecordIds.contains(value.getRecordId())).toList();
        List<BizRecordChildValue> candidateChildValues = childValueService.selectList(Wrappers.<BizRecordChildValue>lambdaQuery()
                .eq(BizRecordChildValue::getValueType, "DEPARTMENT")
                .eq(BizRecordChildValue::getValueReferenceId, id));
        Set<Long> candidateRowIds = new LinkedHashSet<>();
        candidateChildValues.forEach(value -> candidateRowIds.add(value.getChildRowId()));
        Set<Long> ownedRowIds = candidateRowIds.isEmpty() ? Set.of() : childRowService.selectList(
                Wrappers.<BizRecordChildRow>lambdaQuery().eq(BizRecordChildRow::getSystemId, context.systemId())
                        .eq(BizRecordChildRow::getTenantId, context.tenantId())
                        .in(BizRecordChildRow::getId, candidateRowIds)).stream()
                .map(BizRecordChildRow::getId).collect(java.util.stream.Collectors.toSet());
        List<BizRecordChildValue> childValues = candidateChildValues.stream()
                .filter(value -> ownedRowIds.contains(value.getChildRowId())).toList();
        List<SystemAuthorizationModels.DepartmentReference> references = new ArrayList<>();
        references.add(new SystemAuthorizationModels.DepartmentReference("ROOT_DEPARTMENT", "系统根部门",
                department.getParentId() == null ? 1 : 0, false));
        references.add(new SystemAuthorizationModels.DepartmentReference("CHILD_DEPARTMENT", "下级部门", children.size(), false));
        references.add(new SystemAuthorizationModels.DepartmentReference("TENANT_MEMBER", "部门成员", members.size(), true));
        references.add(new SystemAuthorizationModels.DepartmentReference("BUSINESS_RECORD", "业务记录负责部门", records.size(), true));
        references.add(new SystemAuthorizationModels.DepartmentReference("MAIN_FIELD", "主记录自定义部门字段", mainValues.size(), true));
        references.add(new SystemAuthorizationModels.DepartmentReference("CHILD_FIELD", "子表自定义部门字段", childValues.size(), true));
        references.addAll(configurationReferences.inspect(context.systemId(), context.tenantId(), id));
        return new ReferenceSnapshot(department, members, records, mainValues, childValues, references);
    }

    private SystemDepartment requireDepartment(AuthenticatedContext context, Long id) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.BAD_REQUEST);
        }
        SystemDepartment department = departmentService.selectById(id);
        if (department == null || !context.systemId().equals(department.getSystemId())
                || !context.tenantId().equals(department.getTenantId())) {
            throw new DomainException("DEPARTMENT_NOT_FOUND", "当前租户中不存在该部门", HttpStatus.NOT_FOUND);
        }
        return department;
    }

    private DomainException transferConflict() {
        return new DomainException("DEPARTMENT_TRANSFER_CONFLICT", "部门或关联数据已被修改，请重新检查影响范围", HttpStatus.CONFLICT);
    }

    private SystemAuthorizationModels.DepartmentView view(SystemDepartment value) {
        return new SystemAuthorizationModels.DepartmentView(value.getId(), value.getParentId(), value.getCode(),
                value.getName(), value.getPathCode(), value.getSortOrder(), value.getStatus(), value.getVersion());
    }

    private record ReferenceSnapshot(SystemDepartment department, List<SystemTenantMember> members,
                                     List<BusinessRecord> records, List<BusinessRecordValue> mainValues,
                                     List<BizRecordChildValue> childValues,
                                     List<SystemAuthorizationModels.DepartmentReference> references) {
        long blockingCount() {
            return references.stream().filter(item -> !item.transferable()).mapToLong(SystemAuthorizationModels.DepartmentReference::count).sum();
        }

        long transferCount() {
            return references.stream().filter(SystemAuthorizationModels.DepartmentReference::transferable)
                    .mapToLong(SystemAuthorizationModels.DepartmentReference::count).sum();
        }

        SystemAuthorizationModels.DepartmentImpact impact() {
            return new SystemAuthorizationModels.DepartmentImpact(department.getId(), department.getName(),
                    references, blockingCount() == 0 && transferCount() == 0,
                    blockingCount() == 0 && transferCount() > 0);
        }
    }
}
