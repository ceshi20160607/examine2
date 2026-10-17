package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 文件安全扫描结果与历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_security_scan")
public class FileSecurityScan {

    /**
     * 安全扫描主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被扫描文件对象主键
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 扫描器标识
     */
    @TableField("scanner")
    private String scanner;

    /**
     * 扫描规则或引擎版本
     */
    @TableField("scan_version")
    private String scanVersion;

    /**
     * 扫描任务状态和结果
     */
    @TableField("`status`")
    private String status;

    /**
     * 扫描结果编码
     */
    @TableField("result_code")
    private String resultCode;

    /**
     * 不含文件正文的扫描详情
     */
    @TableField("result_detail_json")
    private String resultDetailJson;

    /**
     * 扫描开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 扫描结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 记录创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getScanner() {
        return scanner;
    }

    public void setScanner(String scanner) {
        this.scanner = scanner;
    }

    public String getScanVersion() {
        return scanVersion;
    }

    public void setScanVersion(String scanVersion) {
        this.scanVersion = scanVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultDetailJson() {
        return resultDetailJson;
    }

    public void setResultDetailJson(String resultDetailJson) {
        this.resultDetailJson = resultDetailJson;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "FileSecurityScan{" +
            "id = " + id +
            ", fileId = " + fileId +
            ", scanner = " + scanner +
            ", scanVersion = " + scanVersion +
            ", status = " + status +
            ", resultCode = " + resultCode +
            ", resultDetailJson = " + resultDetailJson +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", createTime = " + createTime +
            "}";
    }
}
