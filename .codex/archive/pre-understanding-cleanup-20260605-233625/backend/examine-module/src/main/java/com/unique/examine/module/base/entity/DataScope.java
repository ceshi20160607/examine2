package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 模块数据权限
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_module_data_scope")
public class DataScope implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块 ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 角色 ID
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 范围：OWNER、DEPT、DEPT_TREE、ROLE、ALL、CUSTOM
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 自定义范围配置
     */
    @TableField("scope_json")
    private String scopeJson;

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
}
