package com.unique.examine.core.base.mapper;

import com.unique.examine.core.base.entity.OperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台、系统、运行、流程、文件和 OpenAPI 操作审计。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
