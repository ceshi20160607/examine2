package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录按发布索引配置生成的查询键
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_index")
public class BizRecordIndex {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务数据所属系统
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 业务数据归属租户
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 业务记录所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 本模块查询索引定义主键
     */
    @TableField("query_index_id")
    private Long queryIndexId;

    /**
     * 复合查询键的哈希摘要
     */
    @TableField("index_key_hash")
    private String indexKeyHash;

    /**
     * 唯一索引的规范化键摘要；非唯一索引为空
     */
    @TableField("unique_key_hash")
    private String uniqueKeyHash;

    /**
     * 可诊断的复合查询键原文
     */
    @TableField("index_key_text")
    private String indexKeyText;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最近修改时间
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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getQueryIndexId() {
        return queryIndexId;
    }

    public void setQueryIndexId(Long queryIndexId) {
        this.queryIndexId = queryIndexId;
    }

    public String getIndexKeyHash() {
        return indexKeyHash;
    }

    public void setIndexKeyHash(String indexKeyHash) {
        this.indexKeyHash = indexKeyHash;
    }

    public String getUniqueKeyHash() {
        return uniqueKeyHash;
    }

    public void setUniqueKeyHash(String uniqueKeyHash) {
        this.uniqueKeyHash = uniqueKeyHash;
    }

    public String getIndexKeyText() {
        return indexKeyText;
    }

    public void setIndexKeyText(String indexKeyText) {
        this.indexKeyText = indexKeyText;
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

    @Override
    public String toString() {
        return "BizRecordIndex{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", queryIndexId = " + queryIndexId +
            ", indexKeyHash = " + indexKeyHash +
            ", uniqueKeyHash = " + uniqueKeyHash +
            ", indexKeyText = " + indexKeyText +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
