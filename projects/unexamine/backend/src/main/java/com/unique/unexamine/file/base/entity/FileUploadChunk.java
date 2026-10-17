package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 上传会话的分片校验与完成明细
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_upload_chunk")
public class FileUploadChunk {

    /**
     * 上传分片主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属上传会话主键
     */
    @TableField("upload_session_id")
    private Long uploadSessionId;

    /**
     * 从零开始的分片序号
     */
    @TableField("chunk_number")
    private Integer chunkNumber;

    /**
     * 分片在完整文件中的起始字节位置
     */
    @TableField("byte_offset")
    private Long byteOffset;

    /**
     * 分片实际字节数
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 分片内容 SHA-256 校验值
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 分片接收和校验状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 分片记录创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 分片写入并校验完成时间
     */
    @TableField("completed_time")
    private LocalDateTime completedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(Long uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }

    public Integer getChunkNumber() {
        return chunkNumber;
    }

    public void setChunkNumber(Integer chunkNumber) {
        this.chunkNumber = chunkNumber;
    }

    public Long getByteOffset() {
        return byteOffset;
    }

    public void setByteOffset(Long byteOffset) {
        this.byteOffset = byteOffset;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }

    @Override
    public String toString() {
        return "FileUploadChunk{" +
            "id = " + id +
            ", uploadSessionId = " + uploadSessionId +
            ", chunkNumber = " + chunkNumber +
            ", byteOffset = " + byteOffset +
            ", sizeBytes = " + sizeBytes +
            ", sha256 = " + sha256 +
            ", status = " + status +
            ", createdTime = " + createdTime +
            ", completedTime = " + completedTime +
            "}";
    }
}
