package com.unique.unexamine.backgroundjobs.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 后台作业逐项业务结果，不保存运行尝试记录
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_job_item_result")
public class JobItemResult {

    /**
     * 作业逐项结果主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属后台作业申请主键
     */
    @TableField("job_id")
    private Long jobId;

    /**
     * 作业内稳定的业务项标识，用于幂等写入
     */
    @TableField("item_key")
    private String itemKey;

    /**
     * 导入导出等行处理场景的原始行号
     */
    @TableField("`row_number`")
    private Long rowNumber;

    /**
     * 该业务项的最终处理状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 该业务项的结果数据或文件引用
     */
    @TableField("result_json")
    private String resultJson;

    /**
     * 该业务项的失败错误码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 该业务项的失败原因摘要
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 逐项结果首次记录时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 逐项结果更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public Long getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Long rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
        return "JobItemResult{" +
            "id = " + id +
            ", jobId = " + jobId +
            ", itemKey = " + itemKey +
            ", rowNumber = " + rowNumber +
            ", status = " + status +
            ", resultJson = " + resultJson +
            ", errorCode = " + errorCode +
            ", errorMessage = " + errorMessage +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
