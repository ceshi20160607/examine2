package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台角色
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_role")
public class PlatformRole {

    /**
     * 平台角色主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 平台内角色编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 角色展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 角色用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 是否为平台内置角色
     */
    @TableField("built_in")
    private Boolean builtIn;

    /**
     * 平台角色状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 平台角色创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 平台角色更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 平台角色乐观锁版本
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(Boolean builtIn) {
        this.builtIn = builtIn;
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
        return "PlatformRole{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", builtIn = " + builtIn +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
