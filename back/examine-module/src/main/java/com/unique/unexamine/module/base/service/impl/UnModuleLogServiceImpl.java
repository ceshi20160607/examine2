package com.unique.unexamine.module.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.module.base.entity.UnModuleLog;
import com.unique.unexamine.module.base.mapper.UnModuleLogMapper;
import com.unique.unexamine.module.base.service.IUnModuleLogService;
import org.springframework.stereotype.Service;

/** UnModuleLog 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnModuleLogServiceImpl extends ServiceImpl<UnModuleLogMapper, UnModuleLog> implements IUnModuleLogService {
}
