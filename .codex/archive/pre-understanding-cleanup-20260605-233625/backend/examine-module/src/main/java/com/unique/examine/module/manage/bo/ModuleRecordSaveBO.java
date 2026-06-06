package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 模块运行态记录保存入参。
 */
@Data
@Schema(description = "模块运行态记录保存入参")
public class ModuleRecordSaveBO {

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "记录编号，创建时可为空由后端自动生成，编辑时传入则按同租户同系统同模型校验唯一后更新")
    private String recordNo;

    @Schema(description = "负责人账号 ID")
    private Long ownerAccountId;

    @Schema(description = "部门 ID")
    private Long deptId;

    @Schema(description = "业务字段值，key 为 fieldCode")
    private Map<String, Object> values;
}
