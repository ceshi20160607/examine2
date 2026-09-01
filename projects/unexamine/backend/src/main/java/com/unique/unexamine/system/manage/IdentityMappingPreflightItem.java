package com.unique.unexamine.system.manage;

import java.util.List;

public record IdentityMappingPreflightItem(
        int rowNumber,
        String externalUserId,
        String externalDepartment,
        String email,
        String mobile,
        String employeeNo,
        String displayName,
        String mfaLevel,
        String device,
        String requestId,
        Long accountId,
        Long systemMemberId,
        Long tenantMemberId,
        Long departmentId,
        String departmentName,
        String outcome,
        String plannedAction,
        List<String> reasons) {
}
