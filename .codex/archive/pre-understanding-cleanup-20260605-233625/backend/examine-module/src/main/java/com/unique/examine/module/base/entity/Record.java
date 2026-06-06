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
 * 模块业务记录
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_module_record")
public class Record implements Serializable {

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
     * 模块 ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 业务记录编号
     */
    @TableField("record_no")
    private String recordNo;

    /**
     * 负责人账号 ID
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 归属部门 ID
     */
    @TableField("dept_id")
    private Long deptId;

    /**
     * 状态：DRAFT、ACTIVE、FLOWING、ARCHIVED
     */
    @TableField("record_status")
    private String recordStatus;

    /**
     * 当前流程实例 ID
     */
    @TableField("flow_instance_id")
    private Long flowInstanceId;

    /**
     * 记录版本号
     */
    @TableField("version_no")
    private Integer versionNo;

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
