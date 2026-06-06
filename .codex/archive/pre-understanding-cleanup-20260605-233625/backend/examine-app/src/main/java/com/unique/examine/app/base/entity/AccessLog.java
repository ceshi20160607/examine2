package com.unique.examine.app.base.entity;

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
 * OpenAPI 调用日志
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_openapi_access_log")
public class AccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户端 ID
     */
    @TableField("client_id")
    private Long clientId;

    /**
     * 请求 ID
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 请求路径
     */
    @TableField("request_path")
    private String requestPath;

    /**
     * HTTP 方法
     */
    @TableField("http_method")
    private String httpMethod;

    /**
     * 状态：SUCCESS、FAILED
     */
    @TableField("status")
    private String status;

    /**
     * 响应码
     */
    @TableField("response_code")
    private String responseCode;

    /**
     * 耗时毫秒
     */
    @TableField("cost_ms")
    private Integer costMs;

    /**
     * 来源 IP
     */
    @TableField("remote_ip")
    private String remoteIp;

    /**
     * 错误摘要
     */
    @TableField("error_message")
    private String errorMessage;

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
