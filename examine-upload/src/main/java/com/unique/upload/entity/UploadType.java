package com.unique.upload.entity;


/**
 * 上传类型
 * @author UNIQUE
 * @date 2024/09/20
 */
public enum UploadType {
    /**
     * 亚马逊
     */
    aws_s3(),

    /**
     * ftp
     */
    ftp(),

    /**
     * 本地
     */
    local(),

    /**
     * 阿里云OSS
     */
    oss(),

    /**
     * 七牛云
     */
    qnc(),

    /**
     * 腾讯云
     */
    cos();

    UploadType() {
    }

}
