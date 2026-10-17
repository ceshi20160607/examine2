package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 数据字典草稿选项
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_dictionary_item")
public class CfgDictionaryItem {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属字典标识
     */
    @TableField("dictionary_id")
    private Long dictionaryId;

    /**
     * 上级配置项标识
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 范围内稳定编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 选项显示文本
     */
    @TableField("label")
    private String label;

    /**
     * 选项层级编码路径
     */
    @TableField("path_code")
    private String pathCode;

    /**
     * 选项所在层级，根节点为零
     */
    @TableField("depth_level")
    private Integer depthLevel;

    /**
     * 从根节点到当前选项的标识路径，使用逗号分隔
     */
    @TableField("depth_path")
    private String depthPath;

    /**
     * 选项展示颜色
     */
    @TableField("color")
    private String color;

    /**
     * 展示图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 选项语义编码
     */
    @TableField("semantic_code")
    private String semanticCode;

    /**
     * 是否为默认选项
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 是否允许作为看板分组选项
     */
    @TableField("board_enabled")
    private Boolean boardEnabled;

    /**
     * 显示排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 生命周期状态
     */
    @TableField("`status`")
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
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(Long dictionaryId) {
        this.dictionaryId = dictionaryId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPathCode() {
        return pathCode;
    }

    public void setPathCode(String pathCode) {
        this.pathCode = pathCode;
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

    public String getSemanticCode() {
        return semanticCode;
    }

    public void setSemanticCode(String semanticCode) {
        this.semanticCode = semanticCode;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getBoardEnabled() {
        return boardEnabled;
    }

    public void setBoardEnabled(Boolean boardEnabled) {
        this.boardEnabled = boardEnabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "CfgDictionaryItem{" +
            "id = " + id +
            ", dictionaryId = " + dictionaryId +
            ", parentId = " + parentId +
            ", code = " + code +
            ", label = " + label +
            ", pathCode = " + pathCode +
            ", depthLevel = " + depthLevel +
            ", depthPath = " + depthPath +
            ", color = " + color +
            ", icon = " + icon +
            ", semanticCode = " + semanticCode +
            ", isDefault = " + isDefault +
            ", boardEnabled = " + boardEnabled +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
