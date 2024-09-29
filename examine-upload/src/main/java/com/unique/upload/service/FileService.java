package com.unique.upload.service;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.unique.upload.entity.UploadEntity;
import com.unique.upload.entity.UploadProperties;
import com.unique.upload.entity.UploadType;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 文件接口
 * @author UNIQUE
 * @date 2024/09/20
 */
public interface FileService {

    /**
     * 文件ID和文件名的连接符
     */
    String JOIN_STR = "-";

    /**
     * 文件分隔符
     */
    String SLASH = "/";

    /**
     * 文件分隔符
     */
    String LOCALE_PREFIX = "locale";

    /**
     * 文件分隔符
     */
    String LOCALE_SUFFIX = ".json";

    /**
     * 上传文件
     *
     * @param inputStream 文件流
     * @param entity      参数对象
     * @return result
     */
    public UploadEntity uploadFile(InputStream inputStream, UploadEntity entity);

    /**
     * 上传文件
     *
     * @param inputStream 文件流
     * @param entity      参数对象
     * @param bucketName  桶名称
     * @return result
     */
    public UploadEntity uploadFile(InputStream inputStream, UploadEntity entity, String bucketName);

    /**
     * 上传临时文件，和正式上传不同的是，此文件7天后会被删除
     *
     * @param inputStream 文件流
     * @param entity      参数对象
     * @return result
     */
    public UploadEntity uploadTempFile(InputStream inputStream, UploadEntity entity);

    /**
     * 上传临时文件，和正式上传不同的是，此文件7天后会被删除
     *
     * @param inputStream 文件流
     * @param entity      参数对象
     * @param bucketName  桶名称
     * @return result
     */
    public UploadEntity uploadTempFile(InputStream inputStream, UploadEntity entity, String bucketName);

    /**
     * 删除文件
     *
     * @param key 上传接口返回的path
     */
    public void deleteFile(String key);

    /**
     * 删除文件
     *
     * @param key        上传接口返回的path
     * @param bucketName 桶名称
     */
    public void deleteFile(String key, String bucketName);

    /**
     * 批量删除文件
     *
     * @param keys key列表
     */
    public void deleteFileBatch(List<String> keys);


    /**
     * 批量删除文件
     *
     * @param keys       key列表
     * @param bucketName 桶名称
     */
    public void deleteFileBatch(List<String> keys, String bucketName);


    /**
     * 重命名文件
     *
     * @param entity   参数对象
     * @param fileName 文件名称
     */
    public void renameFile(UploadEntity entity, String fileName);

    /**
     * 重命名文件
     *
     * @param entity   参数对象
     * @param fileName 文件名称
     * @param bucketName 桶名称
     */
    public void renameFile(UploadEntity entity, String fileName, String bucketName);

    /**
     * 获取文件
     *
     * @param entity 参数对象
     * @return 文件流，可能为空
     */
    public InputStream downFile(UploadEntity entity);

    /**
     * 获取文件
     *
     * @param entity 参数对象
     * @return 文件流，可能为空
     * @param bucketName 桶名称
     */
    public InputStream downFile(UploadEntity entity,String bucketName);
    /**
     * 获取文件上传类型
     *
     * @return type
     */
    UploadType getType();

    /**
     * 生成service
     *
     * @param properties 参数
     * @return fileService
     */
    FileService build(UploadProperties properties);

    /**
     * 生成默认的文件名
     * 文件名默认为 fileId-fileName
     *
     * @param fileId   文件ID
     * @param fileName 文件名
     * @return 格式后的文件名
     */
    default String buildFileName(String fileId, String fileName) {
        if (fileId == null || fileName == null) {
            throw new IllegalArgumentException("fileId or fileName is null");
        }
        if(StrUtil.isNotEmpty(fileName)
                && StrUtil.startWith(fileName,LOCALE_PREFIX)
                && StrUtil.endWith(fileName, LOCALE_SUFFIX)
                && StrUtil.contains(fileName,StrUtil.AT)
        ){
            List<String> strList = Arrays.stream(fileName.split(StrUtil.AT)).collect(Collectors.toList());
            StringBuilder builder = StrUtil.builder();
            for (String path : strList) {
                String prefix = CollUtil.getFirst(strList);
                if(!path.equals(prefix)){
                    builder.append(SLASH);
                }
                builder.append(path);
            }
            return builder.toString();
        }else{
            return getDateStr() + SLASH + fileId + JOIN_STR + fileName;
        }
    }

    /**
     * 获取日期类型的字符串，上传文件默认按照日期存放
     *
     * @return data
     */
    default String getDateStr() {
        return DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
    }

    /**
     * 获取配置信息
     *
     * @return properties
     */
    UploadProperties getProperties();
}
