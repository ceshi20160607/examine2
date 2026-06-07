package com.unique.examine.upload.manage.service;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.upload.manage.bo.FileBindDTO;
import com.unique.examine.upload.manage.bo.FileQueryBO;
import com.unique.examine.upload.manage.bo.FileUnbindDTO;
import com.unique.examine.upload.manage.vo.FileAccessVO;
import com.unique.examine.upload.manage.vo.FileInfoVO;
import com.unique.examine.upload.manage.vo.FileListItemVO;
import com.unique.examine.upload.manage.vo.FileReferenceVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件管理服务。
 */
public interface UploadFileService {

    /**
     * 上传文件并保存临时文件元数据。
     *
     * @param systemId 系统 ID
     * @param file 上传文件
     * @param bizType 引用业务类型
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @param fieldCode 字段编码
     * @return 文件详情
     */
    FileInfoVO upload(Long systemId, MultipartFile file, String bizType, Long moduleId, Long recordId,
            String fieldCode);

    /**
     * 查询文件列表。
     *
     * @param systemId 系统 ID
     * @param queryBO 查询入参
     * @return 文件分页
     */
    PageResult<FileListItemVO> queryFiles(Long systemId, FileQueryBO queryBO);

    /**
     * 查询文件详情。
     *
     * @param systemId 系统 ID
     * @param fileId 文件 ID
     * @return 文件详情
     */
    FileInfoVO fileDetail(Long systemId, Long fileId);

    /**
     * 读取预览文件资源。
     *
     * @param systemId 系统 ID
     * @param fileId 文件 ID
     * @return 文件访问结果
     */
    FileAccessVO preview(Long systemId, Long fileId);

    /**
     * 读取下载文件资源。
     *
     * @param systemId 系统 ID
     * @param fileId 文件 ID
     * @return 文件访问结果
     */
    FileAccessVO download(Long systemId, Long fileId);

    /**
     * 删除文件，已引用文件只允许拒绝删除。
     *
     * @param systemId 系统 ID
     * @param fileId 文件 ID
     * @return 文件详情
     */
    FileInfoVO deleteFile(Long systemId, Long fileId);

    /**
     * 绑定文件引用。
     *
     * @param systemId 系统 ID
     * @param bindDTOList 绑定入参
     * @return 文件引用
     */
    List<FileReferenceVO> bindFiles(Long systemId, List<FileBindDTO> bindDTOList);

    /**
     * 解绑文件引用。
     *
     * @param systemId 系统 ID
     * @param unbindDTOList 解绑入参
     * @return 解绑后的文件引用
     */
    List<FileReferenceVO> unbindFiles(Long systemId, List<FileUnbindDTO> unbindDTOList);
}
