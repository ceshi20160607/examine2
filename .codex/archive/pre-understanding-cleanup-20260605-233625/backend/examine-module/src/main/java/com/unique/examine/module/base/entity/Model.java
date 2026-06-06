package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 动态模块模型
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_module_model")
public class Model implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户 ID
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 系统 ID
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 应用 ID
     */
    @TableField("app_id")
    private Long appId;

    /**
     * 模块编码
     */
    @TableField("module_code")
    private String moduleCode;

    /**
     * 模块名称
     */
    @TableField("module_name")
    private String moduleName;

    /**
     * 数据范围：OWNER、DEPT、DEPT_TREE、ROLE、ALL
     */
    @TableField("data_scope_type")
    private String dataScopeType;

    /**
     * 是否启用流程：0-否，1-是
     */
    @TableField("flow_enabled")
    private Byte flowEnabled;

    /**
     * 是否允许导入：0-否，1-是
     */
    @TableField("import_enabled")
    private Byte importEnabled;

    /**
     * 是否允许导出：0-否，1-是
     */
    @TableField("export_enabled")
    private Byte exportEnabled;

    /**
     * 状态：DRAFT、PUBLISHED、DISABLED
     */
    @TableField("status")
    private String status;

    /**
     * 创建人账号 ID
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 更新人账号 ID
     */
    @TableField("updated_by")
    private Long updatedBy;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0-否，1-是
     */
    @TableField("deleted")
    @TableLogic
    private Byte deleted;
}
