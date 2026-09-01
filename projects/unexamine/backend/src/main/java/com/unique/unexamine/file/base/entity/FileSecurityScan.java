package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("file_security_scan")
public class FileSecurityScan {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("file_id")
    private Long fileId;

    @TableField("scanner")
    private String scanner;

    @TableField("scan_version")
    private String scanVersion;

    @TableField("`status`")
    private String status;

    @TableField("result_code")
    private String resultCode;

    @TableField("result_detail_json")
    private String resultDetailJson;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
            ", startedAt = " + startedAt +
            ", finishedAt = " + finishedAt +
            ", createdAt = " + createdAt +
            "}";
    }
}
