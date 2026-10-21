package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import org.springframework.stereotype.Service;

/** UnPlatMember 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatMemberServiceImpl extends ServiceImpl<UnPlatMemberMapper, UnPlatMember> implements IUnPlatMemberService {
}
