package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块运行态菜单草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_menu")
public class ModuleMenu {

    /**
     * 模块菜单主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 菜单指向的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内菜单编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 运行态菜单显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 列表或其他模块页面入口类型
     */
    @TableField("menu_type")
    private String menuType;

    /**
     * 菜单显示顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 菜单启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 菜单创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 菜单更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    @Override
    public String toString() {
        return "ModuleMenu{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", menuType = " + menuType +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
