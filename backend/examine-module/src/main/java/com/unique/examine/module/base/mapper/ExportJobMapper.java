package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.ExportJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导出任务、筛选快照、权限快照、结果文件引用和重试状态。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface ExportJobMapper extends BaseMapper<ExportJob> {
}
