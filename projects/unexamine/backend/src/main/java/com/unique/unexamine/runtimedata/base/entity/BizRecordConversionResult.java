package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 一次转化创建的目标记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_conversion_result")
public class BizRecordConversionResult {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属转化执行记录主键
     */
    @TableField("conversion_id")
    private Long conversionId;

    /**
     * 来源和目标记录共同所属的数据租户
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 目标业务记录所属模块
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 目标业务记录主键
     */
    @TableField("target_record_id")
    private Long targetRecordId;

    /**
     * 目标记录在转化结果中的类型
     */
    @TableField("result_type")
    private String resultType;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "BizRecordConversionResult{" +
            "id = " + id +
            ", conversionId = " + conversionId +
            ", tenantId = " + tenantId +
            ", targetModuleId = " + targetModuleId +
            ", targetRecordId = " + targetRecordId +
            ", resultType = " + resultType +
            ", createTime = " + createTime +
            "}";
    }
}
