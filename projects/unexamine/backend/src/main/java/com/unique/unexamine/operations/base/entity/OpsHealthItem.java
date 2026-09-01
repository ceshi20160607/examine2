package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("ops_health_item")
public class OpsHealthItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("health_run_id")
    private Long healthRunId;

    @TableField("check_code")
    private String checkCode;

    @TableField("check_name")
    private String checkName;

    @TableField("category")
    private String category;

    @TableField("`status`")
    private String status;

    @TableField("message")
    private String message;

    @TableField("metric_json")
    private String metricJson;

    @TableField("checked_at")
    private LocalDateTime checkedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHealthRunId() {
        return healthRunId;
    }

    public void setHealthRunId(Long healthRunId) {
        this.healthRunId = healthRunId;
    }

    public String getCheckCode() {
        return checkCode;
    }

    public void setCheckCode(String checkCode) {
        this.checkCode = checkCode;
    }

    public String getCheckName() {
        return checkName;
    }

    public void setCheckName(String checkName) {
        this.checkName = checkName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMetricJson() {
        return metricJson;
    }

    public void setMetricJson(String metricJson) {
        this.metricJson = metricJson;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    @Override
    public String toString() {
        return "OpsHealthItem{" +
            "id = " + id +
            ", healthRunId = " + healthRunId +
            ", checkCode = " + checkCode +
            ", checkName = " + checkName +
            ", category = " + category +
            ", status = " + status +
            ", message = " + message +
            ", metricJson = " + metricJson +
            ", checkedAt = " + checkedAt +
            "}";
    }
}
