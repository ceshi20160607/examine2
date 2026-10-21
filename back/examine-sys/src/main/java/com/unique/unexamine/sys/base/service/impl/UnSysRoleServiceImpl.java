package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.mapper.UnSysRoleMapper;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import org.springframework.stereotype.Service;

/** UnSysRole 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysRoleServiceImpl extends ServiceImpl<UnSysRoleMapper, UnSysRole> implements IUnSysRoleService {
}
