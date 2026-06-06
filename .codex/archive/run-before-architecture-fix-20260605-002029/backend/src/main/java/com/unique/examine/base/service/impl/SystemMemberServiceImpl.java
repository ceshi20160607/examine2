package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.SystemMember;
import com.unique.examine.base.mapper.SystemMemberMapper;
import com.unique.examine.base.service.ISystemMemberService;
import org.springframework.stereotype.Service;

@Service
public class SystemMemberServiceImpl extends ServiceImpl<SystemMemberMapper, SystemMember> implements ISystemMemberService {
}
