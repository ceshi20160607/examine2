package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.Member;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台账号在系统内的成员扩展，不是独立登录账号。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface MemberMapper extends BaseMapper<Member> {
}
