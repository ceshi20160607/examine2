package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.mapper.UnSysSystemConfigMapper;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import org.springframework.stereotype.Service;

/** UnSysSystemConfig 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysSystemConfigServiceImpl extends ServiceImpl<UnSysSystemConfigMapper, UnSysSystemConfig> implements IUnSysSystemConfigService {
}
