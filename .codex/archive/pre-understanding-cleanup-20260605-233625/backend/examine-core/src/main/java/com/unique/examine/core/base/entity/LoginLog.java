package com.unique.examine.core.base.entity;

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
 * 系统登录日志
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_sys_login_log")
public class LoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账号 ID
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 登录账号
     */
    @TableField("username")
    private String username;

    /**
     * 状态：SUCCESS、FAILED、LOCKED
     */
    @TableField("login_status")
    private String loginStatus;

    /**
     * 来源 IP
     */
    @TableField("remote_ip")
    private String remoteIp;

    /**
     * 客户端 UA
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 失败原因
     */
    @TableField("failure_reason")
    private String failureReason;

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
