package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.Nonce;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * nonce 去重和 TTL。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface NonceMapper extends BaseMapper<Nonce> {
}
