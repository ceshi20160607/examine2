package com.unique.unexamine.file;

import java.io.IOException;
import java.io.InputStream;

/** 文件存储接入边界；文件归属、引用和访问权限由调用业务校验。 */
public interface FileStorage {
    /** 流式写入并返回不可由客户端指定的对象键及内容摘要。 */
    StoredFile store(InputStream content) throws IOException;

    /** 读取已登记对象，调用方须先完成业务授权。 */
    InputStream open(String key) throws IOException;

    /** 清理业务确认可以删除的对象。 */
    void delete(String key) throws IOException;

    /** 存储结果不携带业务作用域。 */
    record StoredFile(String key, long size, String sha256) {}
}
