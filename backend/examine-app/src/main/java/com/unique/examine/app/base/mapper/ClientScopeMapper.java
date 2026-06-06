package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.ClientScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * scope、模块、动作、字段读写权限和数据范围。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface ClientScopeMapper extends BaseMapper<ClientScope> {
}
