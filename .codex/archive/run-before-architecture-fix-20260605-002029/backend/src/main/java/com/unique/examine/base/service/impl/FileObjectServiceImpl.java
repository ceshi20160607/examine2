package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.FileObject;
import com.unique.examine.base.mapper.FileObjectMapper;
import com.unique.examine.base.service.IFileObjectService;
import org.springframework.stereotype.Service;

@Service
public class FileObjectServiceImpl extends ServiceImpl<FileObjectMapper, FileObject> implements IFileObjectService {
}
