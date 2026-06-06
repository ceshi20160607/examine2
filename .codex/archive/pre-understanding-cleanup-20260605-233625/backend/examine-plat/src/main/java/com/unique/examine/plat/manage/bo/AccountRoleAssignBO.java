package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 账号角色授权入参。
 */
@Data
@Schema(description = "账号角色授权入参")
public class AccountRoleAssignBO {

    @Schema(description = "账号 ID")
    private Long accountId;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;
}
