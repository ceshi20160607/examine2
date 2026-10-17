package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 查询索引包含的模块字段
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_query_index_field")
public class CfgQueryIndexField {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 所属查询索引标识
     */
    @TableField("query_index_id")
    private Long queryIndexId;

    /**
     * 索引使用的模块字段标识
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 显示排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 索引字段排序方向
     */
    @TableField("sort_direction")
    private String sortDirection;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "CfgQueryIndexField{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", queryIndexId = " + queryIndexId +
            ", fieldId = " + fieldId +
            ", sortOrder = " + sortOrder +
            ", sortDirection = " + sortDirection +
            ", createTime = " + createTime +
            "}";
    }
}
