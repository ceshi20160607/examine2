package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 逐条保存值与名称的字典基础项
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_dict")
public class UnModuleDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 字典基础项稳定主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义基础项的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 基础值所属的字典分类编码
     */
    @TableField("dict_code")
    private String dictCode;

    /**
     * 基础值，例如1或0；创建后不可变更含义
     */
    @TableField("dict_value")
    private String dictValue;

    /**
     * 基础项当前名称，例如是或否
     */
    @TableField("name")
    private String name;

    /**
     * 树形或级联基础项的父级主键，根项为空
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 多级字典项从根开始的层级
     */
    @TableField("depth_level")
    private Integer depthLevel;

    /**
     * 从根到本项的稳定主键路径
     */
    @TableField("depth_path")
    private String depthPath;

    /**
     * 同级字典项展示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 状态选项展示颜色
     */
    @TableField("color")
    private String color;

    /**
     * 状态选项展示图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 所属字典分类中的默认选项标记
     */
    @TableField("default_flag")
    private Boolean defaultFlag;

    /**
     * 基础项资料提交版本，用于缓存隔离
     */
    @TableField("revision")
    private Long revision;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictValue() {
        return dictValue;
    }

    public void setDictValue(String dictValue) {
        this.dictValue = dictValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getDepthLevel() {
        return depthLevel;
    }

    public void setDepthLevel(Integer depthLevel) {
        this.depthLevel = depthLevel;
    }

    public String getDepthPath() {
        return depthPath;
    }

    public void setDepthPath(String depthPath) {
        this.depthPath = depthPath;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Boolean getDefaultFlag() {
        return defaultFlag;
    }

    public void setDefaultFlag(Boolean defaultFlag) {
        this.defaultFlag = defaultFlag;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
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
