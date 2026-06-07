package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.DictItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典项，支持层级和内置只读。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {
}
