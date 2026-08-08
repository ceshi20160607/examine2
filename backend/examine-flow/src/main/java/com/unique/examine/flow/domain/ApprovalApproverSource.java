package com.unique.examine.flow.domain;

/**
 * Selects where an approval route obtains its members. Dynamic sources are
 * resolved for the exact system/tenant immediately before validation,
 * simulation and instance start.
 */
public record ApprovalApproverSource(
        Kind kind,
        Long sourceId,
        String moduleCode
) {
    private static final String MODULE_CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{0,63}$";

    public ApprovalApproverSource {
        kind = kind == null ? Kind.FIXED : kind;
        if (!kind.requiresSourceId() && sourceId != null) {
            throw new IllegalArgumentException(
                    kind + " approver source must not define sourceId");
        }
        if (kind.requiresSourceId() && (sourceId == null || sourceId <= 0)) {
            throw new IllegalArgumentException(
                    kind + " approver source requires a positive sourceId");
        }
        if (kind.requiresModuleCode()) {
            if (moduleCode == null || !moduleCode.matches(MODULE_CODE_PATTERN)) {
                throw new IllegalArgumentException(
                        kind + " approver source requires a canonical moduleCode");
            }
        } else if (moduleCode != null) {
            throw new IllegalArgumentException(
                    kind + " approver source must not define moduleCode");
        }
    }

    public ApprovalApproverSource(Kind kind, Long sourceId) {
        this(kind, sourceId, null);
    }

    public static ApprovalApproverSource fixed() {
        return new ApprovalApproverSource(Kind.FIXED, null);
    }

    public static ApprovalApproverSource role(long roleId) {
        return new ApprovalApproverSource(Kind.ROLE, roleId);
    }

    public static ApprovalApproverSource department(long departmentId) {
        return new ApprovalApproverSource(Kind.DEPARTMENT, departmentId);
    }

    public static ApprovalApproverSource departmentLeader(long departmentId) {
        return new ApprovalApproverSource(Kind.DEPARTMENT_LEADER, departmentId);
    }

    public static ApprovalApproverSource requesterManager() {
        return new ApprovalApproverSource(Kind.REQUESTER_MANAGER, null);
    }

    public static ApprovalApproverSource requester() {
        return new ApprovalApproverSource(Kind.REQUESTER, null);
    }

    public static ApprovalApproverSource requesterDepartmentLeader() {
        return new ApprovalApproverSource(
                Kind.REQUESTER_DEPARTMENT_LEADER, null);
    }

    public static ApprovalApproverSource recordMemberField(
            String moduleCode,
            long fieldId
    ) {
        return new ApprovalApproverSource(Kind.RECORD_MEMBER_FIELD, fieldId, moduleCode);
    }

    public static ApprovalApproverSource previousHandler() {
        return new ApprovalApproverSource(Kind.PREVIOUS_HANDLER, null, null);
    }

    public boolean dynamic() {
        return kind != Kind.FIXED;
    }

    public enum Kind {
        FIXED,
        ROLE,
        DEPARTMENT,
        DEPARTMENT_LEADER,
        REQUESTER,
        REQUESTER_MANAGER,
        REQUESTER_DEPARTMENT_LEADER,
        RECORD_MEMBER_FIELD,
        PREVIOUS_HANDLER;

        public boolean requiresSourceId() {
            return this == ROLE
                    || this == DEPARTMENT
                    || this == DEPARTMENT_LEADER
                    || this == RECORD_MEMBER_FIELD;
        }

        public boolean requiresModuleCode() {
            return this == RECORD_MEMBER_FIELD;
        }
    }
}
