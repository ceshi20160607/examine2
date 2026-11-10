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
 * 模块页面字段布局
 * </p>
 *
 * @author Template Base
 * @since 2026-11-07
 */
@TableName("un_module_page_field")
public class UnModulePageField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块页面字段配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属模块页面主键
     */
    @TableField("page_id")
    private Long pageId;

    /**
     * 页面展示的稳定模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 当前页面内的字段顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 字段在当前页面的行号
     */
    @TableField("row_no")
    private Integer rowNo;

    /**
     * 字段在当前页面所在行的列号
     */
    @TableField("column_no")
    private Integer columnNo;

    /**
     * 字段在当前页面所在行的占位宽度百分比
     */
    @TableField("width_percent")
    private Byte widthPercent;

    /**
     * 页面字段启停状态
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

    public Long getPageId() {
        return pageId;
    }

    public void setPageId(Long pageId) {
        this.pageId = pageId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public Integer getColumnNo() {
        return columnNo;
    }

    public void setColumnNo(Integer columnNo) {
        this.columnNo = columnNo;
    }

    public Byte getWidthPercent() {
        return widthPercent;
    }

    public void setWidthPercent(Byte widthPercent) {
        this.widthPercent = widthPercent;
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
