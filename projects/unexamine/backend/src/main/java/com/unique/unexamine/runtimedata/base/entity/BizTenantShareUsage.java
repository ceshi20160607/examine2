package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 目标租户通过共享授权访问业务数据的历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_tenant_share_usage")
public class BizTenantShareUsage {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 共享授权主键
     */
    @TableField("share_id")
    private Long shareId;

    /**
     * 执行共享访问的目标租户成员
     */
    @TableField("target_tenant_member_id")
    private Long targetTenantMemberId;

    /**
     * 通过共享授权执行的业务动作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 共享访问结果编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 共享访问发生时间
     */
    @TableField("occur_time")
    private LocalDateTime occurTime;

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

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
    }

    @Override
    public String toString() {
        return "BizTenantShareUsage{" +
            "id = " + id +
            ", shareId = " + shareId +
            ", targetTenantMemberId = " + targetTenantMemberId +
            ", actionCode = " + actionCode +
            ", resultCode = " + resultCode +
            ", occurTime = " + occurTime +
            "}";
    }
}
