package com.unique.examine.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 仅占位：验证 MyBatis + 动态数据源可装配；无业务表依赖。
 */
@Mapper
public interface DemoMapper {

    @DS("master")
    @Select("SELECT 1")
    Integer selectOne();
}
