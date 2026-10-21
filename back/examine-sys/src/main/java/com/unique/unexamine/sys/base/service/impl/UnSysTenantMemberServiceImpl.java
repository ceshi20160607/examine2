package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import org.springframework.stereotype.Service;

/** UnSysTenantMember 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysTenantMemberServiceImpl extends ServiceImpl<UnSysTenantMemberMapper, UnSysTenantMember> implements IUnSysTenantMemberService {
}
