package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotNull;

/** 记录团队成员；负责人由主记录单独保存，角色只描述其他参与人。 */
public record RuntimeRecordParticipant(@NotNull Long memberId, @NotNull String role) {
}
