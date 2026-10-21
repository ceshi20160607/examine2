package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台组织部门
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_plat_department")
public class UnPlatDepartment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台部门主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上级部门主键，0表示根部门
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 平台内唯一的部门编码
     */
    @TableField("department_code")
    private String departmentCode;

    /**
     * 部门名称
     */
    @TableField("name")
    private String name;

    /**
     * 部门负责人对应的平台成员主键
     */
    @TableField("manager_member_id")
    private Long managerMemberId;

    /**
     * 同级部门排序号
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 部门状态：1启用、0停用
     */
    @TableField("status")
    private Byte status;

    /**
     * 部门备注
     */
    @TableField("remark")
    private String remark;

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

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getManagerMemberId() {
        return managerMemberId;
    }

    public void setManagerMemberId(Long managerMemberId) {
        this.managerMemberId = managerMemberId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
