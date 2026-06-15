package com.unique.examine.core.base.mapper;

import com.unique.examine.core.base.entity.ErrorLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误码、栈摘要和 requestId。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface ErrorLogMapper extends BaseMapper<ErrorLog> {
}
