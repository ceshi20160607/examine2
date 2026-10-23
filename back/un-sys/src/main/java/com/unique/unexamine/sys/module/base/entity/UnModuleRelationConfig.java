package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块关联关系配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_relation_config")
public class UnModuleRelationConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块关联配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义关联的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 来源业务模块主键
     */
    @TableField("source_module_id")
    private Long sourceModuleId;

    /**
     * 目标业务模块主键
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 关联或子表来源字段主键
     */
    @TableField("source_field_id")
    private Long sourceFieldId;

    /**
     * 单选多选子表或上下游关系类型
     */
    @TableField("relation_type")
    private String relationType;

    /**
     * 显示字段筛选条件及反向配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * ??????????ͣ״̬?????漴?
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSourceModuleId() {
        return sourceModuleId;
    }

    public void setSourceModuleId(Long sourceModuleId) {
        this.sourceModuleId = sourceModuleId;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
    }

    public Long getSourceFieldId() {
        return sourceFieldId;
    }

    public void setSourceFieldId(Long sourceFieldId) {
        this.sourceFieldId = sourceFieldId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
