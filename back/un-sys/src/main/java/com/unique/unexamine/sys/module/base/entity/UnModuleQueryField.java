package com.unique.unexamine.sys.module.base.entity;

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
 * @since 2026-10-22
 */
@TableName("un_module_query_field")
public class UnModuleQueryField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 查询字段配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 配置所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 是否用于快速搜索
     */
    @TableField("search_flag")
    private Boolean searchFlag;

    /**
     * 是否用于高级筛选
     */
    @TableField("filter_flag")
    private Boolean filterFlag;

    /**
     * 是否允许结果排序
     */
    @TableField("sort_flag")
    private Boolean sortFlag;

    /**
     * 查询方式及可用操作符配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 查询配置发布状态
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

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Boolean getSearchFlag() {
        return searchFlag;
    }

    public void setSearchFlag(Boolean searchFlag) {
        this.searchFlag = searchFlag;
    }

    public Boolean getFilterFlag() {
        return filterFlag;
    }

    public void setFilterFlag(Boolean filterFlag) {
        this.filterFlag = filterFlag;
    }

    public Boolean getSortFlag() {
        return sortFlag;
    }

    public void setSortFlag(Boolean sortFlag) {
        this.sortFlag = sortFlag;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
}
