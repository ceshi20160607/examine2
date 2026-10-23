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
 * 模块与统一菜单的关联和展示配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_module_menu")
public class UnModuleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块菜单关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义菜单的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 统一系统菜单主键
     */
    @TableField("menu_id")
    private Long menuId;

    /**
     * 模块内菜单展示位置
     */
    @TableField("display_position")
    private String displayPosition;

    /**
     * 同位置展示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 当前启停状态
     */
    @TableField("status")
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

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public String getDisplayPosition() {
        return displayPosition;
    }

    public void setDisplayPosition(String displayPosition) {
        this.displayPosition = displayPosition;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
