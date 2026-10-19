package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块上下游关联关系配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
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
     * 关联起点模块主键
     */
    @TableField("source_module_id")
    private Long sourceModuleId;

    /**
     * 关联终点模块主键
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 起点模块内唯一的关联编码
     */
    @TableField("relation_code")
    private String relationCode;

    /**
     * 关联显示名称
     */
    @TableField("name")
    private String name;

    /**
     * 关联方向：forward 或 both
     */
    @TableField("direction")
    private String direction;

    /**
     * 上下游记录的字段映射配置
     */
    @TableField("mapping_json")
    private String mappingJson;

    /**
     * 关联配置状态：1 启用，0 停用
     */
    @TableField("status")
    private Byte status;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getRelationCode() {
        return relationCode;
    }

    public void setRelationCode(String relationCode) {
        this.relationCode = relationCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getMappingJson() {
        return mappingJson;
    }

    public void setMappingJson(String mappingJson) {
        this.mappingJson = mappingJson;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
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
}
