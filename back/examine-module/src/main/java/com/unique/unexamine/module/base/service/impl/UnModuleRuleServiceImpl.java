package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleRule;
import com.unique.unexamine.module.base.mapper.UnModuleRuleMapper;
import com.unique.unexamine.module.base.service.IUnModuleRuleService;
import org.springframework.stereotype.Service;

/** UnModuleRule 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleRuleServiceImpl extends ServiceImpl<UnModuleRuleMapper, UnModuleRule> implements IUnModuleRuleService {
}
