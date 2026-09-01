package com.unique.unexamine.moduleconfig.base.entity;

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
@TableName("cfg_query_index_field")
public class CfgQueryIndexField {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("query_index_id")
    private Long queryIndexId;

    @TableField("field_id")
    private Long fieldId;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("sort_direction")
    private String sortDirection;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQueryIndexId() {
        return queryIndexId;
    }

    public void setQueryIndexId(Long queryIndexId) {
        this.queryIndexId = queryIndexId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "CfgQueryIndexField{" +
            "id = " + id +
            ", queryIndexId = " + queryIndexId +
            ", fieldId = " + fieldId +
            ", sortOrder = " + sortOrder +
            ", sortDirection = " + sortDirection +
            ", createdAt = " + createdAt +
            "}";
    }
}
