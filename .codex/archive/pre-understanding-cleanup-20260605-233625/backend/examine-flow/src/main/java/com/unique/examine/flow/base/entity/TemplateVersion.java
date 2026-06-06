package com.unique.examine.flow.base.entity;

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
 * 流程模板版本
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_flow_template_version")
public class TemplateVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模板 ID
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 状态：DRAFT、PUBLISHED、ARCHIVED
     */
    @TableField("status")
    private String status;

    /**
     * 流程图快照
     */
    @TableField("graph_json")
    private String graphJson;

    /**
     * 发布时间
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

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
}
