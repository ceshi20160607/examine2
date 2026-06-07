package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * 模块动作配置批量保存入参。
 */
@Data
@Schema(description = "模块动作配置批量保存入参")
public class ActionConfigSaveBO {

    @Valid
    @Schema(description = "动作配置列表")
    private List<ActionConfigBO> actions;
}
