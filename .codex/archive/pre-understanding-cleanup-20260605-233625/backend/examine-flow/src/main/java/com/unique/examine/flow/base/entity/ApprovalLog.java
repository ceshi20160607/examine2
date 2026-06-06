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
 * 流程审批日志
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_flow_approval_log")
public class ApprovalLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程实例 ID
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 任务 ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 动作：SUBMIT、APPROVE、REJECT、TRANSFER、RETURN、CANCEL、TERMINATE
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 操作人账号 ID
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 审批意见
     */
    @TableField("comment_text")
    private String commentText;

    /**
     * 动作快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

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
