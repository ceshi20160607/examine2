package com.unique.unexamine.sys.action.print.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块打印模板及历史发布版本
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_print")
public class UnPrint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 打印模板版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 模板定义租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 打印业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内稳定打印模板编码
     */
    @TableField("template_code")
    private String templateCode;

    /**
     * 打印模板名称
     */
    @TableField("name")
    private String name;

    /**
     * 同一模板的版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 纸张页眉页脚签章及套打位置配置
     */
    @TableField("paper_config_json")
    private String paperConfigJson;

    /**
     * 字段关联值和自动分页明细配置
     */
    @TableField("body_config_json")
    private String bodyConfigJson;

    /**
     * 本版本发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /**
     * 创建模板的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaperConfigJson() {
        return paperConfigJson;
    }

    public void setPaperConfigJson(String paperConfigJson) {
        this.paperConfigJson = paperConfigJson;
    }

    public String getBodyConfigJson() {
        return bodyConfigJson;
    }

    public void setBodyConfigJson(String bodyConfigJson) {
        this.bodyConfigJson = bodyConfigJson;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
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
