package com.unique.examine.plat.manage.org;

import java.util.List;

/**
 * Organization API models.
 */
public final class OrgModels {

    private OrgModels() {
    }

    public record DepartmentSaveRequest(String parentId, String deptCode, String deptName, Integer sortOrder,
                                        Integer status) {
    }

    public record DepartmentVO(String deptId, String parentId, String deptCode, String deptName,
                               Integer sortOrder, Integer status, List<DepartmentVO> children) {
    }
}
