package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.ImportExportTask;
import com.unique.examine.base.mapper.ImportExportTaskMapper;
import com.unique.examine.base.service.IImportExportTaskService;
import org.springframework.stereotype.Service;

@Service
public class ImportExportTaskServiceImpl extends ServiceImpl<ImportExportTaskMapper, ImportExportTask> implements IImportExportTaskService {
}
