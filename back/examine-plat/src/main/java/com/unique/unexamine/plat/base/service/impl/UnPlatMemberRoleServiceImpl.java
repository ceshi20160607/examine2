package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberRoleMapper;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import org.springframework.stereotype.Service;

/** UnPlatMemberRole 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatMemberRoleServiceImpl extends ServiceImpl<UnPlatMemberRoleMapper, UnPlatMemberRole> implements IUnPlatMemberRoleService {
}
