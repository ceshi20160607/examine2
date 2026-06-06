package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.PublishVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模块发布版本快照，运行态只读。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface PublishVersionMapper extends BaseMapper<PublishVersion> {
}
