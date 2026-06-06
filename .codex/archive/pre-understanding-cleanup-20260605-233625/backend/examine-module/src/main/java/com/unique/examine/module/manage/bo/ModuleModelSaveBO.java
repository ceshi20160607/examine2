package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模块模型保存入参。
 */
@Data
@Schema(description = "模块模型保存入参")
public class ModuleModelSaveBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "数据范围策略：OWNER、DEPT、DEPT_TREE、ROLE、ALL")
    private String dataScopeType;

    @Schema(description = "是否启用流程：0-否，1-是")
    private Byte flowEnabled;

    @Schema(description = "是否允许导入：0-否，1-是")
    private Byte importEnabled;

    @Schema(description = "是否允许导出：0-否，1-是")
    private Byte exportEnabled;
}
