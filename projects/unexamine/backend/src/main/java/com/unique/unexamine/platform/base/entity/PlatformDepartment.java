package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台组织部门
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_department")
public class PlatformDepartment {

    /**
     * 平台组织部门主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 上级平台部门标识
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 平台内部门编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 部门展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 部门层级编码路径
     */
    @TableField("path_code")
    private String pathCode;

    /**
     * 同级部门排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 平台组织部门状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 平台组织部门创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 平台组织部门更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 平台组织部门乐观锁版本
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public String getPathCode() {
        return pathCode;
    }

    public void setPathCode(String pathCode) {
        this.pathCode = pathCode;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "PlatformDepartment{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", parentId = " + parentId +
            ", code = " + code +
            ", name = " + name +
            ", pathCode = " + pathCode +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
