package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台操作日志
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_plat_log_opt")
public class UnPlatLogOpt implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台操作日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 操作平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 操作类型编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 操作对象类型
     */
    @TableField("object_type")
    private String objectType;

    /**
     * 操作对象主键
     */
    @TableField("object_id")
    private Long objectId;

    /**
     * 操作摘要和影响范围
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 请求追踪标识
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 操作时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
