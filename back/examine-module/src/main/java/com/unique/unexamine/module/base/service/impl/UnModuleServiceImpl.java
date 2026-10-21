package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.mapper.UnModuleMapper;
import com.unique.unexamine.module.base.service.IUnModuleService;
import org.springframework.stereotype.Service;

/** UnModule 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleServiceImpl extends ServiceImpl<UnModuleMapper, UnModule> implements IUnModuleService {
}
