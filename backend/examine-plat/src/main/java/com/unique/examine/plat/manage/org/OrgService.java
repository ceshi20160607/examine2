package com.unique.examine.plat.manage.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatDepartment;
import com.unique.examine.plat.base.service.PlatDepartmentBaseService;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.org.OrgModels.DepartmentSaveRequest;
import com.unique.examine.plat.manage.org.OrgModels.DepartmentVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Organization service backed by persisted department tree data.
 */
@Service
public class OrgService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;

    private final SystemMemberContextResolver contextResolver;
    private final PlatDepartmentBaseService departmentBaseService;

    public OrgService(SystemMemberContextResolver contextResolver,
                      PlatDepartmentBaseService departmentBaseService) {
        this.contextResolver = contextResolver;
        this.departmentBaseService = departmentBaseService;
    }

    /**
     * Return department tree for a system.
     *
     * @param systemId system id
     * @return department tree
     */
    public List<DepartmentVO> departmentTree(String systemId) {
        SystemMemberContext context = contextResolver.requireSystemAdmin(systemId);
        List<PlatDepartment> departments = departmentBaseService.list(new LambdaQueryWrapper<PlatDepartment>()
                .eq(PlatDepartment::getSystemId, context.systemId())
                .eq(PlatDepartment::getTenantId, context.tenantId())
                .eq(PlatDepartment::getDeleted, DELETED_NO)
                .orderByAsc(PlatDepartment::getSortOrder)
                .orderByAsc(PlatDepartment::getId));
        return buildTree(departments, 0L);
    }

    /**
     * Create a department.
     *
     * @param systemId system id
     * @param request save request
     * @return department
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO createDepartment(String systemId, DepartmentSaveRequest request) {
        SystemMemberContext context = contextResolver.requireSystemAdmin(systemId);
        requireText(request == null ? null : request.deptCode(), "部门编码不能为空");
        requireText(request.deptName(), "部门名称不能为空");
        Long parentId = parseParentId(request.parentId());
        if (departmentBaseService.count(new LambdaQueryWrapper<PlatDepartment>()
                .eq(PlatDepartment::getSystemId, context.systemId())
                .eq(PlatDepartment::getTenantId, context.tenantId())
                .eq(PlatDepartment::getDeptCode, request.deptCode())
                .eq(PlatDepartment::getDeleted, DELETED_NO)) > 0) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "部门编码已存在");
        }
        PlatDepartment department = new PlatDepartment();
        department.setSystemId(context.systemId());
        department.setTenantId(context.tenantId());
        department.setParentId(parentId);
        department.setDeptCode(request.deptCode());
        department.setDeptName(request.deptName());
        department.setSortOrder(Objects.isNull(request.sortOrder()) ? 99 : request.sortOrder());
        department.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        department.setDeleted(DELETED_NO);
        departmentBaseService.saveEntity(department);
        return toVO(department, List.of());
    }

    private List<DepartmentVO> buildTree(List<PlatDepartment> departments, Long parentId) {
        List<DepartmentVO> children = new ArrayList<>();
        for (PlatDepartment department : departments) {
            if (Objects.equals(department.getParentId(), parentId)) {
                children.add(toVO(department, buildTree(departments, department.getId())));
            }
        }
        return children;
    }

    private DepartmentVO toVO(PlatDepartment department, List<DepartmentVO> children) {
        return new DepartmentVO(String.valueOf(department.getId()), parentIdValue(department.getParentId()),
                department.getDeptCode(), department.getDeptName(), department.getSortOrder(),
                department.getStatus(), children);
    }

    private String parentIdValue(Long parentId) {
        return Objects.isNull(parentId) || parentId == 0 ? "0" : String.valueOf(parentId);
    }

    private Long parseParentId(String parentId) {
        if (!StringUtils.hasText(parentId) || "0".equals(parentId)) {
            return 0L;
        }
        return contextResolver.parseRequiredId(parentId, "父部门ID格式不正确");
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }
}
