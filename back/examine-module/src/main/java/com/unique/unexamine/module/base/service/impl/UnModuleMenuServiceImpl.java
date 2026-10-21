package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleMenu;
import com.unique.unexamine.module.base.mapper.UnModuleMenuMapper;
import com.unique.unexamine.module.base.service.IUnModuleMenuService;
import org.springframework.stereotype.Service;

/** UnModuleMenu 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleMenuServiceImpl extends ServiceImpl<UnModuleMenuMapper, UnModuleMenu> implements IUnModuleMenuService {
}
