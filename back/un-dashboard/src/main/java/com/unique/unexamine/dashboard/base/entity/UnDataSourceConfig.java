package com.unique.unexamine.dashboard.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 自定义数据源的关联输出筛选配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_data_source_config")
public class UnDataSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据源查询配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属数据源主键
     */
    @TableField("data_source_id")
    private Long dataSourceId;

    /**
     * 已配置模块关联路径与连接方式
     */
    @TableField("relation_json")
    private String relationJson;

    /**
     * 允许输出的模块字段稳定标识
     */
    @TableField("output_json")
    private String outputJson;

    /**
     * 结构化固定筛选条件
     */
    @TableField("filter_json")
    private String filterJson;

    /**
     * 默认排序和时间字段配置
     */
    @TableField("sort_json")
    private String sortJson;

    /**
     * 已发布查询配置整体快照
     */
    @TableField("published_json")
    private String publishedJson;

    /**
     * 查询配置发布版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getRelationJson() {
        return relationJson;
    }

    public void setRelationJson(String relationJson) {
        this.relationJson = relationJson;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getSortJson() {
        return sortJson;
    }

    public void setSortJson(String sortJson) {
        this.sortJson = sortJson;
    }

    public String getPublishedJson() {
        return publishedJson;
    }

    public void setPublishedJson(String publishedJson) {
        this.publishedJson = publishedJson;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
