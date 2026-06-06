package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模块页面保存入参。
 */
@Data
@Schema(description = "模块页面保存入参")
public class ModulePageSaveBO {

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "页面编码")
    private String pageCode;

    @Schema(description = "页面名称")
    private String pageName;

    @Schema(description = "页面类型：LIST、FORM、DETAIL、DASHBOARD")
    private String pageType;

    @Schema(description = "布局配置 JSON")
    private String layoutJson;

    @Schema(description = "按钮配置 JSON")
    private String buttonJson;
}
