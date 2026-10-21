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
 * @since 2026-10-21
 */
@TableName("un_module_scence_config")
public class UnModuleScenceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 场景查询配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联场景主键
     */
    @TableField("scence_id")
    private Long scenceId;

    /**
     * 已保存的结构化筛选条件
     */
    @TableField("filter_json")
    private String filterJson;

    /**
     * 场景默认列表列配置
     */
    @TableField("column_json")
    private String columnJson;

    /**
     * 场景默认排序配置
     */
    @TableField("sort_json")
    private String sortJson;

    /**
     * 场景查询配置发布版本号
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public Long getScenceId() {
        return scenceId;
    }

    public void setScenceId(Long scenceId) {
        this.scenceId = scenceId;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getColumnJson() {
        return columnJson;
    }

    public void setColumnJson(String columnJson) {
        this.columnJson = columnJson;
    }

    public String getSortJson() {
        return sortJson;
    }

    public void setSortJson(String sortJson) {
        this.sortJson = sortJson;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
