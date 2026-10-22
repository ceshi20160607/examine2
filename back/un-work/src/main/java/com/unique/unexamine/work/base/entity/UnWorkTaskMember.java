package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 任务协作关注与验收成员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_work_task_member")
public class UnWorkTaskMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务参与人关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 参与平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 协作关注或验收成员身份
     */
    @TableField("member_type")
    private String memberType;

    /**
     * 参与关系状态
     */
    @TableField("status")
    private String status;

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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
