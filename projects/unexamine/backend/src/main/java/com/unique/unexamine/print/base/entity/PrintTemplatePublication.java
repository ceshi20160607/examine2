package com.unique.unexamine.print.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 打印模板当前生效版本指针，可回滚至历史版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_print_template_publication")
public class PrintTemplatePublication {

    /**
     * 模板当前发布指针主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模板配置所属租户主键
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 模板绑定的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模板草稿主键
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * 当前生效的不可变模板版本
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 最近发布或回滚的系统成员主键
     */
    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

    /**
     * 发布时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 发布指针乐观锁版本
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getUpdatedByMemberId() {
        return updatedByMemberId;
    }

    public void setUpdatedByMemberId(Long updatedByMemberId) {
        this.updatedByMemberId = updatedByMemberId;
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
        return "PrintTemplatePublication{" +
            "id = " + id +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", templateId = " + templateId +
            ", currentVersionId = " + currentVersionId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
