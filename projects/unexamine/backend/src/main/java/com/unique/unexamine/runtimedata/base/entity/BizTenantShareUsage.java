package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_tenant_share_usage")
public class BizTenantShareUsage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("share_id")
    private Long shareId;

    @TableField("target_tenant_member_id")
    private Long targetTenantMemberId;

    @TableField("action_code")
    private String actionCode;

    @TableField("result_code")
    private String resultCode;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShareId() {
        return shareId;
    }

    public void setShareId(Long shareId) {
        this.shareId = shareId;
    }

    public Long getTargetTenantMemberId() {
        return targetTenantMemberId;
    }

    public void setTargetTenantMemberId(Long targetTenantMemberId) {
        this.targetTenantMemberId = targetTenantMemberId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "BizTenantShareUsage{" +
            "id = " + id +
            ", shareId = " + shareId +
            ", targetTenantMemberId = " + targetTenantMemberId +
            ", actionCode = " + actionCode +
            ", resultCode = " + resultCode +
            ", occurredAt = " + occurredAt +
            "}";
    }
}
