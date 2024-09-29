package com.unique.upload.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;


/**
 * 上传文件配置
 * @author UNIQUE
 * @date 2024/09/20
 */
@Data
@ConfigurationProperties(prefix = "unique.upload")
public class UploadProperties {

    /**
     * 上传类型
     */
    private UploadType type;

    /**
     * 端点
     */
    private String endpoint;

    /**
     * 默认的域名
     */
    private String domain;

    /**
     * 桶名称
     */
    private String bucketName;

    /**
     * 区域
     */
    private String region;

    /**
     * 账号
     */
    private String accessKeyId;

    /**
     * 密码
     */
    private String accessKeySecret;

    /**
     * 额外数据
     */
    private Map<String, Object> extra;

    public Map<String, Object> getExtra() {
        if (extra == null) {
            return Collections.emptyMap();
        }
        return extra;
    }
}
