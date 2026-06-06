package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.ExportJobLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导出任务状态流转、领取、失败和重试日志。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface ExportJobLogMapper extends BaseMapper<ExportJobLog> {
}
