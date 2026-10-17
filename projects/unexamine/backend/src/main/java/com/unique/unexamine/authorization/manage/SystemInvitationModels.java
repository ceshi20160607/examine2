package com.unique.unexamine.authorization.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 系统邀请只承载入职信息，令牌仅在创建结果中返回一次。 */
public final class SystemInvitationModels {
    private SystemInvitationModels() {}

    public record CreateRequest(@Size(max = 200) String targetAccount,
                                @NotNull Long departmentId,
                                @NotNull List<Long> roleIds,
                                @Size(max = 100) String employeeNumber,
                                @Size(max = 100) String positionTitle) {}

    public record InvitationView(Long id, Long systemId, String systemName, Long tenantId, String tenantName,
                                 String targetAccount, Long departmentId, String departmentName,
                                 List<Long> roleIds, String employeeNumber, String positionTitle,
                                 String status, LocalDateTime expireTime, LocalDateTime createTime,
                                 Integer version) {}

    public record CreatedInvitation(InvitationView invitation, String token) {}

    public record RevokeRequest(@NotNull Integer expectedVersion) {}

    public record AcceptedInvitation(Long systemId, Long tenantId, Long systemMemberId, Long tenantMemberId) {}

    public record AcceptRequest(@NotBlank String token) {}
}
