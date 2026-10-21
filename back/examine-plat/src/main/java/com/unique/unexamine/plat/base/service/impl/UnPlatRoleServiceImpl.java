package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleMapper;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import org.springframework.stereotype.Service;

/** UnPlatRole 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatRoleServiceImpl extends ServiceImpl<UnPlatRoleMapper, UnPlatRole> implements IUnPlatRoleService {
}
