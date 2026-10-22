package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块关联关系
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_relation")
public class UnModuleRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务记录关联主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 来源业务记录主键
     */
    @TableField("source_record_id")
    private Long sourceRecordId;

    /**
     * 目标业务记录主键
     */
    @TableField("target_record_id")
    private Long targetRecordId;

    /**
     * 普通关联对应配置主键，转化关系为空
     */
    @TableField("relation_config_id")
    private Long relationConfigId;

    /**
     * 子表明细或关联展示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 关联状态
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
     * 普通关联或模块转化关系
     */
    @TableField("relation_type")
    private String relationType;

    /**
     * 转化目标模块主键，普通关联为空
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 产生转化关系的操作菜单主键
     */
    @TableField("action_menu_id")
    private Long actionMenuId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public Long getRelationConfigId() {
        return relationConfigId;
    }

    public void setRelationConfigId(Long relationConfigId) {
        this.relationConfigId = relationConfigId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
    }

    public Long getActionMenuId() {
        return actionMenuId;
    }

    public void setActionMenuId(Long actionMenuId) {
        this.actionMenuId = actionMenuId;
    }
}
