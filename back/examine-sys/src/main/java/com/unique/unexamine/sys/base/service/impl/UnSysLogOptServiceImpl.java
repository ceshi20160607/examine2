package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.mapper.UnSysLogOptMapper;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import org.springframework.stereotype.Service;

/** UnSysLogOpt 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysLogOptServiceImpl extends ServiceImpl<UnSysLogOptMapper, UnSysLogOpt> implements IUnSysLogOptService {
}
