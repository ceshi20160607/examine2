package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-21
 */
@TableName("un_module_runtime_schema_field")
public class RuntimeSchemaField implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("schema_version_id")
    private Long schemaVersionId;

    @TableField("module_snapshot_id")
    private Long moduleSnapshotId;

    @TableField("field_snapshot_id")
    private Long fieldSnapshotId;

    @TableField("source_field_id")
    private Long sourceFieldId;

    @TableField("logical_module_id")
    private Long logicalModuleId;

    @TableField("logical_field_id")
    private Long logicalFieldId;

    @TableField("parent_field_snapshot_id")
    private Long parentFieldSnapshotId;

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

    @TableField("field_scope")
    private String fieldScope;

    @TableField("is_required")
    private Boolean isRequired;

    @TableField("is_readonly")
    private Boolean isReadonly;

    @TableField("property_json")
    private String propertyJson;

    @TableField("result_schema")
    private String resultSchema;

    @TableField("evaluator_version")
    private Short evaluatorVersion;

    @TableField("expression_checksum")
    private String expressionChecksum;

    @TableField("topological_rank")
    private Short topologicalRank;

    @TableField("dependency_json")
    private String dependencyJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public Long getSchemaVersionId() {
        return schemaVersionId;
    }

    public void setSchemaVersionId(Long schemaVersionId) {
        this.schemaVersionId = schemaVersionId;
    }

    public Long getModuleSnapshotId() {
        return moduleSnapshotId;
    }

    public void setModuleSnapshotId(Long moduleSnapshotId) {
        this.moduleSnapshotId = moduleSnapshotId;
    }

    public Long getFieldSnapshotId() {
        return fieldSnapshotId;
    }

    public void setFieldSnapshotId(Long fieldSnapshotId) {
        this.fieldSnapshotId = fieldSnapshotId;
    }

    public Long getSourceFieldId() {
        return sourceFieldId;
    }

    public void setSourceFieldId(Long sourceFieldId) {
        this.sourceFieldId = sourceFieldId;
    }

    public Long getLogicalModuleId() {
        return logicalModuleId;
    }

    public void setLogicalModuleId(Long logicalModuleId) {
        this.logicalModuleId = logicalModuleId;
    }

    public Long getLogicalFieldId() {
        return logicalFieldId;
    }

    public void setLogicalFieldId(Long logicalFieldId) {
        this.logicalFieldId = logicalFieldId;
    }

    public Long getParentFieldSnapshotId() {
        return parentFieldSnapshotId;
    }

    public void setParentFieldSnapshotId(Long parentFieldSnapshotId) {
        this.parentFieldSnapshotId = parentFieldSnapshotId;
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

    public String getFieldScope() {
        return fieldScope;
    }

    public void setFieldScope(String fieldScope) {
        this.fieldScope = fieldScope;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }

    public Boolean getIsReadonly() {
        return isReadonly;
    }

    public void setIsReadonly(Boolean isReadonly) {
        this.isReadonly = isReadonly;
    }

    public String getPropertyJson() {
        return propertyJson;
    }

    public void setPropertyJson(String propertyJson) {
        this.propertyJson = propertyJson;
    }

    public String getResultSchema() {
        return resultSchema;
    }

    public void setResultSchema(String resultSchema) {
        this.resultSchema = resultSchema;
    }

    public Short getEvaluatorVersion() {
        return evaluatorVersion;
    }

    public void setEvaluatorVersion(Short evaluatorVersion) {
        this.evaluatorVersion = evaluatorVersion;
    }

    public String getExpressionChecksum() {
        return expressionChecksum;
    }

    public void setExpressionChecksum(String expressionChecksum) {
        this.expressionChecksum = expressionChecksum;
    }

    public Short getTopologicalRank() {
        return topologicalRank;
    }

    public void setTopologicalRank(Short topologicalRank) {
        this.topologicalRank = topologicalRank;
    }

    public String getDependencyJson() {
        return dependencyJson;
    }

    public void setDependencyJson(String dependencyJson) {
        this.dependencyJson = dependencyJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
