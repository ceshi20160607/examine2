package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建手动业务动作。动作编码由系统生成，管理员只配置可见名称、位置和需要填写的业务字段。
 */
public record CreateCustomActionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "MODULE_ENTRY|LIST_TOOLBAR|BATCH|ROW|DETAIL_HEADER|DETAIL_MORE") String location,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> parameterFieldCodes,
        @NotNull Boolean confirmation,
        @Size(max = 300) String confirmationText) {
}
