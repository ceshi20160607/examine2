package com.unique.examine.plat.base.mapper;

import com.unique.examine.plat.base.entity.Config;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 密码策略、会话策略、文件存储、OpenAPI 全局策略和审计保留配置。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface ConfigMapper extends BaseMapper<Config> {
}
