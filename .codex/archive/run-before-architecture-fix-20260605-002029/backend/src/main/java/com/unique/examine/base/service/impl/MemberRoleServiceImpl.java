package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.MemberRole;
import com.unique.examine.base.mapper.MemberRoleMapper;
import com.unique.examine.base.service.IMemberRoleService;
import org.springframework.stereotype.Service;

@Service
public class MemberRoleServiceImpl extends ServiceImpl<MemberRoleMapper, MemberRole> implements IMemberRoleService {
}
