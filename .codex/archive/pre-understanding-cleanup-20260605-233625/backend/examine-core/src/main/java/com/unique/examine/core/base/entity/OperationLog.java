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
 * 审计操作日志
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_audit_operation_log")
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户 ID
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 系统 ID
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 操作人账号 ID
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 操作类型
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 操作对象类型
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 操作对象 ID
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 来源：WEB、MOBILE、OPENAPI、SYSTEM
     */
    @TableField("request_source")
    private String requestSource;

    /**
     * 变更前
     */
    @TableField("before_json")
    private String beforeJson;

    /**
     * 变更后
     */
    @TableField("after_json")
    private String afterJson;

    /**
     * 结果：SUCCESS、FAILED
     */
    @TableField("result_status")
    private String resultStatus;

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
