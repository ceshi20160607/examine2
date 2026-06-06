package com.unique.examine.flow.base.entity;

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
 * 流程实例
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_flow_instance")
public class Instance implements Serializable {

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
     * 模块 ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 业务记录 ID
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 流程模板 ID
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 流程模板版本 ID
     */
    @TableField("template_version_id")
    private Long templateVersionId;

    /**
     * 状态：RUNNING、APPROVED、REJECTED、CANCELED、TERMINATED
     */
    @TableField("status")
    private String status;

    /**
     * 当前节点 key
     */
    @TableField("current_node_key")
    private String currentNodeKey;

    /**
     * 发起人账号 ID
     */
    @TableField("started_by")
    private Long startedBy;

    /**
     * 发起时间
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    @TableField("ended_at")
    private LocalDateTime endedAt;

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
