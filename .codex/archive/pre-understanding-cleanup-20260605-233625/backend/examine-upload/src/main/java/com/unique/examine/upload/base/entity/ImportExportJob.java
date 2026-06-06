package com.unique.examine.upload.base.entity;

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
 * 导入导出任务
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_upload_import_export_job")
public class ImportExportJob implements Serializable {

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
     * 模块 ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 类型：IMPORT、EXPORT
     */
    @TableField("job_type")
    private String jobType;

    /**
     * 状态：PENDING、RUNNING、SUCCESS、FAILED
     */
    @TableField("status")
    private String status;

    /**
     * 导入源文件 ID
     */
    @TableField("source_file_id")
    private Long sourceFileId;

    /**
     * 结果文件 ID
     */
    @TableField("result_file_id")
    private Long resultFileId;

    /**
     * 请求参数
     */
    @TableField("request_json")
    private String requestJson;

    /**
     * 失败原因
     */
    @TableField("failure_reason")
    private String failureReason;

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
