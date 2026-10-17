package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 系统后台设置工作空间专属登录展示；空白值表示恢复默认展示。 */
public record UpdateTenantLoginPresentationRequest(
        @Size(max = 200) String loginTitle,
        @Size(max = 500) String loginSubtitle,
        @NotNull Integer expectedVersion) {
}
