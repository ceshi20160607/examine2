package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.Field;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模块字段定义、校验、唯一、关联、子表和自动编号配置。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface FieldMapper extends BaseMapper<Field> {
}
