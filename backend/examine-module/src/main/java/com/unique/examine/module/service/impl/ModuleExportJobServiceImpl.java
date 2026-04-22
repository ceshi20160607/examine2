package com.unique.examine.module.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.module.entity.po.ModuleExportJob;
import com.unique.examine.module.mapper.ModuleExportJobMapper;
import com.unique.examine.module.service.IModuleExportJobService;
import org.springframework.stereotype.Service;

@Service
public class ModuleExportJobServiceImpl extends ServiceImpl<ModuleExportJobMapper, ModuleExportJob>
        implements IModuleExportJobService {
}

