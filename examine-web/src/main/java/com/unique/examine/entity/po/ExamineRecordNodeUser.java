package com.unique.examine.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 审批适用用户部门表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
@Getter
@Setter
@TableName("un_examine_record_node_user")
@ApiModel(value = "ExamineRecordNodeUser对象", description = "审批适用用户部门表")
public class ExamineRecordNodeUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("父级id从0开始")
    private Long parentId;

    @ApiModelProperty("深度从0开始")
    private String depts;

    @ApiModelProperty("转他人处理flag 默认0 1表示这个是转他人的审批场景 2抄送的邮箱")
    private Integer transferFlag;


    @ApiModelProperty("审批id")
    private Long recordId;
    @ApiModelProperty("审批id")
    private Long recordNodeId;


    @ApiModelProperty("审批id")
    private Long examineId;

    @ApiModelProperty("节点id")
    private Long nodeId;

    @ApiModelProperty("适用类型 0用户 1部门 2 角色 4邮箱")
    private Integer applyType;

    @ApiModelProperty("适用用户id")
    private Long userId;

    @ApiModelProperty("适用部门id")
    private Long deptId;

    @ApiModelProperty("适用角色id")
    private Long roleId;

    @ApiModelProperty("邮箱")
    private String eamil;

    @ApiModelProperty("审批状态")
    private Integer status;

    @ApiModelProperty("审批状态备注")
    private String remark;

    @ApiModelProperty("排序")
    private Integer sorting;

    @ApiModelProperty("创建人")
    @TableField(fill = FieldFill.INSERT)
    private Long createUserId;

    @ApiModelProperty("更新人")
    @TableField(fill = FieldFill.UPDATE)
    private Long updateUserId;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty("企业id")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long companyId;


}
