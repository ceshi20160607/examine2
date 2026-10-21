package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleTeamer;
import com.unique.unexamine.module.base.mapper.UnModuleTeamerMapper;
import com.unique.unexamine.module.base.service.IUnModuleTeamerService;
import org.springframework.stereotype.Service;

/** UnModuleTeamer 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleTeamerServiceImpl extends ServiceImpl<UnModuleTeamerMapper, UnModuleTeamer> implements IUnModuleTeamerService {
}
