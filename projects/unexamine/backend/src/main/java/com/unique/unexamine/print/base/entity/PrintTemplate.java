package com.unique.unexamine.print.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 绑定业务模块的打印模板草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_print_template")
public class PrintTemplate {

    /**
     * 打印模板草稿主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模板所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

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
     * 模块内模板编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 模板展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 纸张尺寸
     */
    @TableField("page_size")
    private String pageSize;

    /**
     * 页面方向
     */
    @TableField("orientation")
    private String orientation;

    /**
     * 草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 页眉、页脚、签章区和明细表格等草稿布局
     */
    @TableField("template_json")
    private String templateJson;

    /**
     * 草稿、已发布或停用等模板状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建模板的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 草稿乐观锁版本
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getTemplateJson() {
        return templateJson;
    }

    public void setTemplateJson(String templateJson) {
        this.templateJson = templateJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
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
        return "PrintTemplate{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", pageSize = " + pageSize +
            ", orientation = " + orientation +
            ", draftRevision = " + draftRevision +
            ", templateJson = " + templateJson +
            ", status = " + status +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
