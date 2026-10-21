package com.unique.unexamine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块场景配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_scence_config")
public class UnModuleScenceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块场景配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属模块场景主键
     */
    @TableField("scence_id")
    private Long scenceId;

    /**
     * 列表表头的字段、显隐、宽度与顺序配置
     */
    @TableField("columns_json")
    private String columnsJson;

    /**
     * 场景默认筛选条件
     */
    @TableField("filters_json")
    private String filtersJson;

    /**
     * 场景默认字段排序及方向
     */
    @TableField("sort_json")
    private String sortJson;

    /**
     * 场景默认每页记录数
     */
    @TableField("page_size")
    private Short pageSize;

    /**
     * 配置创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 配置更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScenceId() {
        return scenceId;
    }

    public void setScenceId(Long scenceId) {
        this.scenceId = scenceId;
    }

    public String getColumnsJson() {
        return columnsJson;
    }

    public void setColumnsJson(String columnsJson) {
        this.columnsJson = columnsJson;
    }

    public String getFiltersJson() {
        return filtersJson;
    }

    public void setFiltersJson(String filtersJson) {
        this.filtersJson = filtersJson;
    }

    public String getSortJson() {
        return sortJson;
    }

    public void setSortJson(String sortJson) {
        this.sortJson = sortJson;
    }

    public Short getPageSize() {
        return pageSize;
    }

    public void setPageSize(Short pageSize) {
        this.pageSize = pageSize;
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
