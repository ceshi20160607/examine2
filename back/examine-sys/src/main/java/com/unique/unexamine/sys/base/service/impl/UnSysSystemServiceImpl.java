package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysSystem;
import com.unique.unexamine.sys.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import org.springframework.stereotype.Service;

/** UnSysSystem 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysSystemServiceImpl extends ServiceImpl<UnSysSystemMapper, UnSysSystem> implements IUnSysSystemService {
}
