package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysRoleData;
import com.unique.unexamine.sys.base.mapper.UnSysRoleDataMapper;
import com.unique.unexamine.sys.base.service.IUnSysRoleDataService;
import org.springframework.stereotype.Service;

/** UnSysRoleData 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysRoleDataServiceImpl extends ServiceImpl<UnSysRoleDataMapper, UnSysRoleData> implements IUnSysRoleDataService {
}
