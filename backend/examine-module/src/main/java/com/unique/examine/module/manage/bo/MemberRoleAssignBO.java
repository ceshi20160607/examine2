package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 成员角色分配入参。
 */
@Data
@Schema(description = "成员角色分配入参")
public class MemberRoleAssignBO {

    @Schema(description = "角色 ID 集合")
    private List<String> roleIds;
}
