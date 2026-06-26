package com.unique.examine.plat.manage.member;

import java.time.LocalDateTime;
import java.util.List;

/**
 * System member API models.
 */
public final class MemberModels {

    private MemberModels() {
    }

    public record MemberQueryRequest(String departmentId, Integer status, String roleId, String keyword,
                                     String bindingStatus) {
    }

    public record MemberSaveRequest(String deptId, String memberName, String employeeNo, String mobile,
                                    String email, Integer status, List<String> roleIds) {
    }

    public record AccountBindingRequest(String accountId, String loginName, String bindMode) {
    }

    public record MemberVO(String systemMemberId, String systemId, String tenantId, String deptId,
                           String memberName, String employeeNo, String mobile, String email, Integer status,
                           String bindingStatus, List<String> roleIds, LocalDateTime updatedAt) {
    }

    public record AccountBindingVO(String bindingId, String accountId, String systemMemberId, String bindingStatus,
                                   String disabledReason, LocalDateTime updatedAt) {
    }
}
