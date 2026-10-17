package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块的可配置字段草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_field")
public class ModuleField {

    /**
     * 模块字段主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 字段所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内稳定的字段编码
     */
    @TableField("field_key")
    private String fieldKey;

    /**
     * 字段在表单和列表中的名称
     */
    @TableField("label")
    private String label;

    /**
     * 文本、数字、日期、布尔等字段类型
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 新增和编辑时是否必填
     */
    @TableField("required_flag")
    private Boolean requiredFlag;

    /**
     * 是否允许作为筛选条件
     */
    @TableField("searchable_flag")
    private Boolean searchableFlag;

    /**
     * 字段显示顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 选择类字段的候选项配置
     */
    @TableField("options_json")
    private String optionsJson;

    /**
     * 字段启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 字段创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 字段更新时间
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

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Boolean getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public Boolean getSearchableFlag() {
        return searchableFlag;
    }

    public void setSearchableFlag(Boolean searchableFlag) {
        this.searchableFlag = searchableFlag;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
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

    @Override
    public String toString() {
        return "ModuleField{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", fieldKey = " + fieldKey +
            ", label = " + label +
            ", fieldType = " + fieldType +
            ", requiredFlag = " + requiredFlag +
            ", searchableFlag = " + searchableFlag +
            ", sortOrder = " + sortOrder +
            ", optionsJson = " + optionsJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
