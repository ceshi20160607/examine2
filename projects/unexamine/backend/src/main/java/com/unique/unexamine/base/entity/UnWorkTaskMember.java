package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 任务协作、关注和验收成员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_work_task_member")
public class UnWorkTaskMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务成员关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 参与的系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 协作、关注或验收关系类型
     */
    @TableField("relation_type")
    private String relationType;

    /**
     * 加入任务时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
