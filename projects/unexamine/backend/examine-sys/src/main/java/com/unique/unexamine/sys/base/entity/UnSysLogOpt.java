package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统操作日志
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_sys_log_opt")
public class UnSysLogOpt implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统操作日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 操作所在的租户主键，系统管理操作可为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 操作系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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
