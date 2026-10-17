package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 任务协作、关注和验收成员
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_task_member")
public class WorkTaskMember {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务标识
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 参与人平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 任务参与人租户成员ID
     */
    @TableField("tenant_member_id")
    private Long tenantMemberId;

    /**
     * 任务成员参与类型
     */
    @TableField("member_type")
    private String memberType;

    /**
     * 创建时间
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

    public Long getTenantMemberId() {
        return tenantMemberId;
    }

    public void setTenantMemberId(Long tenantMemberId) {
        this.tenantMemberId = tenantMemberId;
    }

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "WorkTaskMember{" +
            "id = " + id +
            ", taskId = " + taskId +
            ", accountId = " + accountId +
            ", tenantMemberId = " + tenantMemberId +
            ", memberType = " + memberType +
            ", createTime = " + createTime +
            "}";
    }
}
