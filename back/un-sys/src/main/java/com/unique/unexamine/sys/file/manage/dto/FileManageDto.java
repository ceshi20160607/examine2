package com.unique.unexamine.sys.file.manage.dto;

import java.io.InputStream;

/** FileManage 的请求与返回模型。 */
public final class FileManageDto {
    private FileManageDto() {}

    /** 对外只展示文件业务属性，不泄露存储对象键。 */
    public record FileView(Long id, String batchId, String originalName, String contentType, long sizeBytes,
                           String sha256, String status) {}

    /** 已授权下载的内容流由 HTTP 层负责关闭。 */
    public record FileContent(FileView file, InputStream stream) {}
}
