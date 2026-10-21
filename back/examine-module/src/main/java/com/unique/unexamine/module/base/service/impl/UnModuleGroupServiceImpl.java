package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleGroup;
import com.unique.unexamine.module.base.mapper.UnModuleGroupMapper;
import com.unique.unexamine.module.base.service.IUnModuleGroupService;
import org.springframework.stereotype.Service;

/** UnModuleGroup 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleGroupServiceImpl extends ServiceImpl<UnModuleGroupMapper, UnModuleGroup> implements IUnModuleGroupService {
}
