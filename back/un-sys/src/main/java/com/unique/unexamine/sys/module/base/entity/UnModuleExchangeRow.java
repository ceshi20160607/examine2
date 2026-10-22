package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 导入导出逐行业务结果与回滚依据
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_exchange_row")
public class UnModuleExchangeRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 导入导出作业行结果主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属导入导出作业主键
     */
    @TableField("job_id")
    private Long jobId;

    /**
     * 原始文件或结果文件行序号
     */
    @TableField("row_no")
    private Integer rowNo;

    /**
     * 实际新增或更新业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 该行新增更新跳过或导出操作
     */
    @TableField("operation_code")
    private String operationCode;

    /**
     * 预演通过成功失败或已回滚状态
     */
    @TableField("status")
    private String status;

    /**
     * 该行明确失败原因编码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 错误字段及本次写入前后值快照
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 回滚前必须匹配的记录乐观锁版本
     */
    @TableField("record_version")
    private Long recordVersion;

    /**
     * 行检查或处理时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public Long getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(Long recordVersion) {
        this.recordVersion = recordVersion;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
