package com.unique.examine.plat.base.entity;

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
 * 租户部门
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_platt_department")
public class Department implements Serializable {

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
     * 父部门 ID
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 部门编码
     */
    @TableField("dept_code")
    private String deptCode;

    /**
     * 部门名称
     */
    @TableField("dept_name")
    private String deptName;

    /**
     * 排序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 状态：ENABLED、DISABLED
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
