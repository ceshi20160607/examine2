package com.unique.examine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 平台租户
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_platt_tenant")
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户编码，全局唯一
     */
    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 租户名称
     */
    @TableField("tenant_name")
    private String tenantName;

    /**
     * 状态：ENABLED、DISABLED、EXPIRED
     */
    @TableField("status")
    private String status;

    /**
     * 默认管理员账号 ID
     */
    @TableField("admin_account_id")
    private Long adminAccountId;

    /**
     * 有效期截止时间
     */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /**
     * 租户扩展配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 创建人账号 ID
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 更新人账号 ID
     */
    @TableField("updated_by")
    private Long updatedBy;

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

    /**
     * 逻辑删除：0-否，1-是
     */
    @TableField("deleted")
    @TableLogic
    private Byte deleted;
}
