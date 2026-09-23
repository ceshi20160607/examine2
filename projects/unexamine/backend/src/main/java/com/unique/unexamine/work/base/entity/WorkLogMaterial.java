package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志人工选材快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("work_log_material")
public class WorkLogMaterial {

    /**
     * 日志选材主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工作日志主键
     */
    @TableField("work_log_id")
    private Long workLogId;

    /**
     * 材料类型：任务、待办、消息或业务操作
     */
    @TableField("material_type")
    private String materialType;

    /**
     * 来源材料标识
     */
    @TableField("material_id")
    private String materialId;

    /**
     * 选中时的材料标题快照
     */
    @TableField("material_title")
    private String materialTitle;

    /**
     * 选中时的材料摘要快照
     */
    @TableField("material_summary")
    private String materialSummary;

    /**
     * 来源材料发生时间
     */
    @TableField("source_time")
    private LocalDateTime sourceTime;

    /**
     * 选择材料的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 材料快照创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkLogId() {
        return workLogId;
    }

    public void setWorkLogId(Long workLogId) {
        this.workLogId = workLogId;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String materialId) {
        this.materialId = materialId;
    }

    public String getMaterialTitle() {
        return materialTitle;
    }

    public void setMaterialTitle(String materialTitle) {
        this.materialTitle = materialTitle;
    }

    public String getMaterialSummary() {
        return materialSummary;
    }

    public void setMaterialSummary(String materialSummary) {
        this.materialSummary = materialSummary;
    }

    public LocalDateTime getSourceTime() {
        return sourceTime;
    }

    public void setSourceTime(LocalDateTime sourceTime) {
        this.sourceTime = sourceTime;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "WorkLogMaterial{" +
            "id = " + id +
            ", workLogId = " + workLogId +
            ", materialType = " + materialType +
            ", materialId = " + materialId +
            ", materialTitle = " + materialTitle +
            ", materialSummary = " + materialSummary +
            ", sourceTime = " + sourceTime +
            ", createdByAccountId = " + createdByAccountId +
            ", createdTime = " + createdTime +
            "}";
    }
}
