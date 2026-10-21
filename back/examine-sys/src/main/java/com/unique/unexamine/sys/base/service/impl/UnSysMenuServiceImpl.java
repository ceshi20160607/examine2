package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.mapper.UnSysMenuMapper;
import com.unique.unexamine.sys.base.service.IUnSysMenuService;
import org.springframework.stereotype.Service;

/** UnSysMenu 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysMenuServiceImpl extends ServiceImpl<UnSysMenuMapper, UnSysMenu> implements IUnSysMenuService {
}
