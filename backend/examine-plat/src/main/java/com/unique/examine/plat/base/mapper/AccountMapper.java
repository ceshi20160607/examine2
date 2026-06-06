package com.unique.examine.plat.base.mapper;

import com.unique.examine.plat.base.entity.Account;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局登录主体，承载登录名、密码哈希、状态和安全字段。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}
