package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkProjectMember;
import com.unique.unexamine.work.base.mapper.UnWorkProjectMemberMapper;
import com.unique.unexamine.work.base.service.IUnWorkProjectMemberService;
import org.springframework.stereotype.Service;

/** UnWorkProjectMember 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkProjectMemberServiceImpl extends ServiceImpl<UnWorkProjectMemberMapper, UnWorkProjectMember> implements IUnWorkProjectMemberService {
}
