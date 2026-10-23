package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块通用业务主记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_record")
public class UnModuleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通用业务记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 记录原始所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 业务记录标题
     */
    @TableField("name")
    private String name;

    /**
     * 业务记录编号
     */
    @TableField("num")
    private String num;

    /**
     * 业务状态
     */
    @TableField("status")
    private String status;

    /**
     * 当前负责人平台账号主键
     */
    @TableField("owner_user_id")
    private Long ownerUserId;

    /**
     * 负责人在本租户所属部门主键
     */
    @TableField("owner_dept_id")
    private Long ownerDeptId;

    /**
     * 创建业务归属账号主键
     */
    @TableField("create_user_id")
    private Long createUserId;

    /**
     * 实际提交操作的账号主键
     */
    @TableField("actual_operator_id")
    private Long actualOperatorId;

    /**
     * 最近一次修改业务记录的平台账号主键
     */
    @TableField("update_user_id")
    private Long updateUserId;

    /**
     * 业务记录并发写入乐观锁版本
     */
    @TableField("lock_version")
    private Long lockVersion;

    /**
     * 业务记录归档时间
     */
    @TableField("archived_time")
    private LocalDateTime archivedTime;

    /**
     * 逻辑删除时间
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNum() {
        return num;
    }

    public void setNum(String num) {
        this.num = num;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getOwnerDeptId() {
        return ownerDeptId;
    }

    public void setOwnerDeptId(Long ownerDeptId) {
        this.ownerDeptId = ownerDeptId;
    }

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    public Long getActualOperatorId() {
        return actualOperatorId;
    }

    public void setActualOperatorId(Long actualOperatorId) {
        this.actualOperatorId = actualOperatorId;
    }

    public Long getUpdateUserId() {
        return updateUserId;
    }

    public void setUpdateUserId(Long updateUserId) {
        this.updateUserId = updateUserId;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }

    public LocalDateTime getArchivedTime() {
        return archivedTime;
    }

    public void setArchivedTime(LocalDateTime archivedTime) {
        this.archivedTime = archivedTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
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
