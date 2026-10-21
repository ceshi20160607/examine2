package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysRoleMenu;
import com.unique.unexamine.sys.base.mapper.UnSysRoleMenuMapper;
import com.unique.unexamine.sys.base.service.IUnSysRoleMenuService;
import org.springframework.stereotype.Service;

/** UnSysRoleMenu 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysRoleMenuServiceImpl extends ServiceImpl<UnSysRoleMenuMapper, UnSysRoleMenu> implements IUnSysRoleMenuService {
}
