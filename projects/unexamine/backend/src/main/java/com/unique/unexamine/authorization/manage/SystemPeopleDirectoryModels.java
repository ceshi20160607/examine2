package com.unique.unexamine.authorization.manage;

import java.util.List;
import java.util.Map;

public final class SystemPeopleDirectoryModels {
    private SystemPeopleDirectoryModels() {
    }

    public record Directory(
            List<Department> departments,
            List<Person> people,
            long permissionVersion) {
    }

    /** 已授权记录中引用的组织名称；不包含来源租户的完整通讯录或成员权限。 */
    public record Labels(Map<Long, String> members, Map<Long, String> departments) {
    }

    public record Department(
            Long id,
            Long parentId,
            String name,
            String fullName,
            int memberCount) {
    }

    public record Person(
            Long tenantMemberId,
            Long systemMemberId,
            Long accountId,
            String displayName,
            String employeeNumber,
            Long departmentId,
            String departmentName,
            Long managerTenantMemberId,
            String managerName,
            String positionTitle,
            List<String> roleNames,
            boolean tenantAdmin) {
    }
}
