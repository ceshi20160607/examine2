package com.unique.examine.plat.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台账号角色分配入参。
 */
@Data
@Schema(description = "平台账号角色分配入参")
public class PlatformAccountRoleAssignBO {

    @Schema(description = "平台角色 ID 集合")
    @NotNull(message = "角色 ID 集合不能为空")
    private List<Long> roleIds;
}
