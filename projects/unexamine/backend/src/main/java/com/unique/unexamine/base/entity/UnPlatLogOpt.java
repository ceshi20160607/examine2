package com.unique.unexamine.base.entity;

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
 * @since 2026-10-19
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
     * 业务操作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 被操作对象类型
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 被操作对象主键
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 请求路径
     */
    @TableField("request_path")
    private String requestPath;

    /**
     * 操作是否成功：1成功、0失败
     */
    @TableField("success")
    private Byte success;

    /**
     * 客户端地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 操作发生时间
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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public Byte getSuccess() {
        return success;
    }

    public void setSuccess(Byte success) {
        this.success = success;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
