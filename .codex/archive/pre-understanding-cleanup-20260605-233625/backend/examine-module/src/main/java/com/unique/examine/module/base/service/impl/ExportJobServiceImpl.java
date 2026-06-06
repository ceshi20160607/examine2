package com.unique.examine.module.base.service.impl;

import com.unique.examine.module.base.entity.ExportJob;
import com.unique.examine.module.base.mapper.ExportJobMapper;
import com.unique.examine.module.base.service.IExportJobService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 模块导出任务 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class ExportJobServiceImpl extends ServiceImpl<ExportJobMapper, ExportJob> implements IExportJobService {

}
