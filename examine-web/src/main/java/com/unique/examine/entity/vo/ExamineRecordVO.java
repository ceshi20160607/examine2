package com.unique.examine.entity.vo;

import com.unique.examine.entity.po.ExamineRecord;
import com.unique.examine.entity.po.ExamineRecordNode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批表
 * </p>
 *
 * @author UNIQUE
 * @since 2024-01-30
 */
@Data
@ApiModel(value = "ExamineRecordVO", description = "审批实例")
public class ExamineRecordVO implements Serializable {

    @ApiModelProperty("eamine")
    private ExamineRecord examineRecord;

    @ApiModelProperty("节点")
    private ExamineRecordNode examineRecordNode;



}
