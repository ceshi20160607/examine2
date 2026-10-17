package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录与附件的引用关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_reference")
public class FileReference {

    /**
     * 文件引用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被引用的文件对象主键
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 引用文件的业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 附件加入业务记录时间
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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "FileReference{" +
            "id = " + id +
            ", fileId = " + fileId +
            ", recordId = " + recordId +
            ", createTime = " + createTime +
            "}";
    }
}
