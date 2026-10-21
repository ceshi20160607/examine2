package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysLogLogin;
import com.unique.unexamine.sys.base.mapper.UnSysLogLoginMapper;
import com.unique.unexamine.sys.base.service.IUnSysLogLoginService;
import org.springframework.stereotype.Service;

/** UnSysLogLogin 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysLogLoginServiceImpl extends ServiceImpl<UnSysLogLoginMapper, UnSysLogLogin> implements IUnSysLogLoginService {
}
