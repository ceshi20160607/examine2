package com.unique.unexamine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 查询模块字段配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_query_field")
public class UnModuleQueryField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块查询字段配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 可用于查询的字段编码
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 此字段允许使用的查询操作符
     */
    @TableField("operators_json")
    private String operatorsJson;

    /**
     * 查询项显示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 查询项状态：1 启用，0 停用
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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOperatorsJson() {
        return operatorsJson;
    }

    public void setOperatorsJson(String operatorsJson) {
        this.operatorsJson = operatorsJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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
