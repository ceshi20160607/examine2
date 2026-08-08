package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@TableName("un_module_field")
public class Field implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("dictionary_id")
    private Long dictionaryId;

    @TableField("target_module_id")
    private Long targetModuleId;

    @TableField("field_code")
    private String fieldCode;

    @TableField("field_name")
    private String fieldName;

    @TableField("field_type")
    private String fieldType;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("is_required")
    private Boolean isRequired;

    @TableField("is_hidden")
    private Boolean isHidden;

    @TableField("is_readonly")
    private Boolean isReadonly;

    @TableField("is_searchable")
    private Boolean isSearchable;

    @TableField("is_filterable")
    private Boolean isFilterable;

    @TableField("show_in_list")
    private Boolean showInList;

    @TableField("show_in_detail")
    private Boolean showInDetail;

    @TableField("index_mode")
    private String indexMode;

    @TableField("desired_status")
    private String desiredStatus;

    @TableField("property_json")
    private String propertyJson;

    @TableField("code_locked_at")
    private LocalDateTime codeLockedAt;

    @TableField("created_revision")
    private Long createdRevision;

    @TableField("updated_revision")
    private Long updatedRevision;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @Version
    @TableField("version")
    private Long version;

    @TableLogic
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("deleted_by")
    private Long deletedBy;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(Long dictionaryId) {
        this.dictionaryId = dictionaryId;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }

    public Boolean getIsHidden() {
        return isHidden;
    }

    public void setIsHidden(Boolean isHidden) {
        this.isHidden = isHidden;
    }

    public Boolean getIsReadonly() {
        return isReadonly;
    }

    public void setIsReadonly(Boolean isReadonly) {
        this.isReadonly = isReadonly;
    }

    public Boolean getIsSearchable() {
        return isSearchable;
    }

    public void setIsSearchable(Boolean isSearchable) {
        this.isSearchable = isSearchable;
    }

    public Boolean getIsFilterable() {
        return isFilterable;
    }

    public void setIsFilterable(Boolean isFilterable) {
        this.isFilterable = isFilterable;
    }

    public Boolean getShowInList() {
        return showInList;
    }

    public void setShowInList(Boolean showInList) {
        this.showInList = showInList;
    }

    public Boolean getShowInDetail() {
        return showInDetail;
    }

    public void setShowInDetail(Boolean showInDetail) {
        this.showInDetail = showInDetail;
    }

    public String getIndexMode() {
        return indexMode;
    }

    public void setIndexMode(String indexMode) {
        this.indexMode = indexMode;
    }

    public String getDesiredStatus() {
        return desiredStatus;
    }

    public void setDesiredStatus(String desiredStatus) {
        this.desiredStatus = desiredStatus;
    }

    public String getPropertyJson() {
        return propertyJson;
    }

    public void setPropertyJson(String propertyJson) {
        this.propertyJson = propertyJson;
    }

    public LocalDateTime getCodeLockedAt() {
        return codeLockedAt;
    }

    public void setCodeLockedAt(LocalDateTime codeLockedAt) {
        this.codeLockedAt = codeLockedAt;
    }

    public Long getCreatedRevision() {
        return createdRevision;
    }

    public void setCreatedRevision(Long createdRevision) {
        this.createdRevision = createdRevision;
    }

    public Long getUpdatedRevision() {
        return updatedRevision;
    }

    public void setUpdatedRevision(Long updatedRevision) {
        this.updatedRevision = updatedRevision;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }
}
