package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台成员
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_member")
public class PlatformMember {

    /**
     * 平台成员主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 对应平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 所属平台部门标识
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 平台成员职务名称
     */
    @TableField("title")
    private String title;

    /**
     * 平台成员状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 平台成员创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 平台成员更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 平台成员乐观锁版本
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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
        return "PlatformMember{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", accountId = " + accountId +
            ", departmentId = " + departmentId +
            ", title = " + title +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
