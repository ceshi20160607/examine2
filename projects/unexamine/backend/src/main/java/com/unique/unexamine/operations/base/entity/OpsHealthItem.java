package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 健康检查逐项结果
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_health_item")
public class OpsHealthItem {

    /**
     * 健康检查项目主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属健康检查运行主键
     */
    @TableField("health_run_id")
    private Long healthRunId;

    /**
     * 检查项稳定编码
     */
    @TableField("check_code")
    private String checkCode;

    /**
     * 检查项显示名称
     */
    @TableField("check_name")
    private String checkName;

    /**
     * 检查项业务类别
     */
    @TableField("category")
    private String category;

    /**
     * 检查项结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 结果说明和修复建议
     */
    @TableField("message")
    private String message;

    /**
     * 检查项数值与证据
     */
    @TableField("metric_json")
    private String metricJson;

    /**
     * 检查项完成时间
     */
    @TableField("check_time")
    private LocalDateTime checkTime;

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

    public LocalDateTime getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(LocalDateTime checkTime) {
        this.checkTime = checkTime;
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
            ", checkTime = " + checkTime +
            "}";
    }
}
