package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台菜单
 * </p>
 *
 * @author Template Base
 * @since 2026-10-21
 */
@TableName("un_plat_menu")
public class UnPlatMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台菜单主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上级菜单主键
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 菜单权限编码
     */
    @TableField("code")
    private String code;

    /**
     * 菜单名称
     */
    @TableField("name")
    private String name;

    /**
     * 前端路由
     */
    @TableField("route")
    private String route;

    /**
     * 菜单排序序号
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 菜单状态
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
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
}
