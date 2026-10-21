package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import org.springframework.stereotype.Service;

/** UnSysMember 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysMemberServiceImpl extends ServiceImpl<UnSysMemberMapper, UnSysMember> implements IUnSysMemberService {
}
