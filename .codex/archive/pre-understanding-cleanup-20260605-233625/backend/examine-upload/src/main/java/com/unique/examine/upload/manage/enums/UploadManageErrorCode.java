package com.unique.examine.upload.manage.enums;

import lombok.Getter;

/**
 * 上传中心错误码。
 */
@Getter
public enum UploadManageErrorCode {

    PARAM_REQUIRED("UPLOAD_PARAM_REQUIRED", "上传参数缺失"),
    DATA_NOT_FOUND("UPLOAD_DATA_NOT_FOUND", "文件或附件数据不存在"),
    STATUS_INVALID("UPLOAD_STATUS_INVALID", "文件状态不允许当前操作");

    private final String code;
    private final String message;

    UploadManageErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
