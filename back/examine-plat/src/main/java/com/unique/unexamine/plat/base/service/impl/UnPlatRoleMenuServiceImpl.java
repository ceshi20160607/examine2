package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatRoleMenu;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleMenuMapper;
import com.unique.unexamine.plat.base.service.IUnPlatRoleMenuService;
import org.springframework.stereotype.Service;

/** UnPlatRoleMenu 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatRoleMenuServiceImpl extends ServiceImpl<UnPlatRoleMenuMapper, UnPlatRoleMenu> implements IUnPlatRoleMenuService {
}
