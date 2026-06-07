package com.unique.examine.plat.base.mapper;

import com.unique.examine.plat.base.entity.System;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自定义系统容器，承载系统编码、租户模式、创建人和状态。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface SystemMapper extends BaseMapper<System> {
}
