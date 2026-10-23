package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 租户角色对模块字段的控制
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_sys_role_field")
public class UnSysRoleField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色模块字段许可主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 当前租户角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 字段所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 稳定模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 是否允许读取字段
     */
    @TableField("readable_flag")
    private Boolean readableFlag;

    /**
     * 是否允许写入字段
     */
    @TableField("writable_flag")
    private Boolean writableFlag;

    /**
     * 是否允许导出字段
     */
    @TableField("exportable_flag")
    private Boolean exportableFlag;

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
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Boolean getReadableFlag() {
        return readableFlag;
    }

    public void setReadableFlag(Boolean readableFlag) {
        this.readableFlag = readableFlag;
    }

    public Boolean getWritableFlag() {
        return writableFlag;
    }

    public void setWritableFlag(Boolean writableFlag) {
        this.writableFlag = writableFlag;
    }

    public Boolean getExportableFlag() {
        return exportableFlag;
    }

    public void setExportableFlag(Boolean exportableFlag) {
        this.exportableFlag = exportableFlag;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
