package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块字段
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_field")
public class UnModuleField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块字段主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义字段的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段展示名称
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 字段数据类型
     */
    @TableField("field_type")
    private String fieldType;

    /**
     * 保存时是否必填
     */
    @TableField("required_flag")
    private Boolean requiredFlag;

    /**
     * 表单是否只读
     */
    @TableField("readonly_flag")
    private Boolean readonlyFlag;

    /**
     * 表单是否隐藏
     */
    @TableField("hidden_flag")
    private Boolean hiddenFlag;

    /**
     * 设计器保存的字段顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 设计器保存的位置分组及占位宽度
     */
    @TableField("layout_json")
    private String layoutJson;

    /**
     * 类型校验默认值选项及权限配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 字段草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 字段生效版本号
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

    /**
     * æœ€è¿‘å‘å¸ƒæ—¶çš„å­—æ®µä½ç½®å’Œå ä½å®½åº¦å¿«ç…§
     */
    @TableField("published_layout_json")
    private String publishedLayoutJson;

    /**
     * æœ€è¿‘å‘å¸ƒæ—¶çš„å­—æ®µåç§°ç±»åž‹æ ¡éªŒç­‰é…ç½®å¿«ç…§
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

    /**
     * æœ€è¿‘å‘å¸ƒæ—¶çš„å­—æ®µé¡ºåº
     */
    @TableField("published_sort_no")
    private Integer publishedSortNo;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Boolean getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public Boolean getReadonlyFlag() {
        return readonlyFlag;
    }

    public void setReadonlyFlag(Boolean readonlyFlag) {
        this.readonlyFlag = readonlyFlag;
    }

    public Boolean getHiddenFlag() {
        return hiddenFlag;
    }

    public void setHiddenFlag(Boolean hiddenFlag) {
        this.hiddenFlag = hiddenFlag;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
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

    public String getPublishedLayoutJson() {
        return publishedLayoutJson;
    }

    public void setPublishedLayoutJson(String publishedLayoutJson) {
        this.publishedLayoutJson = publishedLayoutJson;
    }

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
    }

    public Integer getPublishedSortNo() {
        return publishedSortNo;
    }

    public void setPublishedSortNo(Integer publishedSortNo) {
        this.publishedSortNo = publishedSortNo;
    }
}
