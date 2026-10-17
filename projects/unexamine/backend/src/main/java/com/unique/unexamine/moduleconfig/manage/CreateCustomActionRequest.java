package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建命名业务动作。动作编码由系统生成，管理员选择填写字段或固定状态效果。
 */
public record CreateCustomActionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "MODULE_ENTRY|LIST_TOOLBAR|BATCH|ROW|DETAIL_HEADER|DETAIL_MORE") String location,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> parameterFieldCodes,
        @NotNull Boolean confirmation,
        @Size(max = 300) String confirmationText,
        @Pattern(regexp = "UPDATE_FIELDS|SET_STATUS") String operation,
        @Size(max = 64) String statusValue) {
}
