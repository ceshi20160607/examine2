package com.unique.unexamine.authorization.manage;

import java.util.List;

public final class SystemPeopleDirectoryModels {
    private SystemPeopleDirectoryModels() {
    }

    public record Directory(
            List<Department> departments,
            List<Person> people,
            long permissionVersion) {
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
