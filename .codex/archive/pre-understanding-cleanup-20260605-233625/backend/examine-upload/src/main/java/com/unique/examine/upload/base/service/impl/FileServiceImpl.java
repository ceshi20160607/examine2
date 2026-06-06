package com.unique.examine.upload.base.service.impl;

import com.unique.examine.upload.base.entity.File;
import com.unique.examine.upload.base.mapper.FileMapper;
import com.unique.examine.upload.base.service.IFileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 上传文件 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements IFileService {

}
