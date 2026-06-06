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
 * 流程任务
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_flow_task")
public class Task implements Serializable {

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
     * 节点 key
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 任务名称
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 当前处理人
     */
    @TableField("assignee_id")
    private Long assigneeId;

    /**
     * 候选人快照
     */
    @TableField("candidate_json")
    private String candidateJson;

    /**
     * 状态：PENDING、APPROVED、REJECTED、CANCELED、TRANSFERRED、RETURNED
     */
    @TableField("status")
    private String status;

    /**
     * 到期时间
     */
    @TableField("due_at")
    private LocalDateTime dueAt;

    /**
     * 处理时间
     */
    @TableField("handled_at")
    private LocalDateTime handledAt;

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
