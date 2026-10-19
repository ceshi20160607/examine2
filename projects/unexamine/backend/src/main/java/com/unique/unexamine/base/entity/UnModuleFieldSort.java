package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块字段排序
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_field_sort")
public class UnModuleFieldSort implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块字段排序主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 所属租户主键，0 为基础模块默认顺序
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 排序适用位置：form、detail 或 list
     */
    @TableField("view_type")
    private String viewType;

    /**
     * 需要排序的字段编码
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 字段显示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 排序配置创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 顺序更新时间
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getViewType() {
        return viewType;
    }

    public void setViewType(String viewType) {
        this.viewType = viewType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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
