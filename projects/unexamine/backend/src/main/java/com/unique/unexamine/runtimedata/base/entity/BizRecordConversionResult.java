package com.unique.unexamine.runtimedata.base.entity;

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
@TableName("biz_record_conversion_result")
public class BizRecordConversionResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("conversion_id")
    private Long conversionId;

    @TableField("target_module_id")
    private Long targetModuleId;

    @TableField("target_record_id")
    private Long targetRecordId;

    @TableField("result_type")
    private String resultType;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversionId() {
        return conversionId;
    }

    public void setConversionId(Long conversionId) {
        this.conversionId = conversionId;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "BizRecordConversionResult{" +
            "id = " + id +
            ", conversionId = " + conversionId +
            ", targetModuleId = " + targetModuleId +
            ", targetRecordId = " + targetRecordId +
            ", resultType = " + resultType +
            ", createdAt = " + createdAt +
            "}";
    }
}
