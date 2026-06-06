package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "运行菜单保存入参")
public class RuntimeMenuSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @Schema(description = "父菜单ID") private Long parentId;
    @Schema(description = "应用ID") private Long appId;
    @Schema(description = "模块ID") private Long moduleId;
    @Schema(description = "页面ID") private Long pageId;
    @NotBlank private String menuName;
    @NotBlank private String menuCode;
    @NotBlank private String permissionCode;
    @Schema(description = "排序号") private Integer sortOrder;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

