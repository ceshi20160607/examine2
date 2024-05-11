package com.unique.examine.entity.dto;

import com.unique.examine.entity.bo.ExamineNodeBO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author UNIQUE
 * @create 2023-03-10
 * @verson 1.0.0
 */
@Data
public class ExamineNodeAdd {

    @ApiModelProperty(value = "审批实例id")
    private Long examineRecordId;

    @ApiModelProperty(value = "审批节点")
    private ExamineNodeBO examineNode;
}
