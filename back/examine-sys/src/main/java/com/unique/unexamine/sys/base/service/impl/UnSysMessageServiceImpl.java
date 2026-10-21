package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysMessage;
import com.unique.unexamine.sys.base.mapper.UnSysMessageMapper;
import com.unique.unexamine.sys.base.service.IUnSysMessageService;
import org.springframework.stereotype.Service;

/** UnSysMessage 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysMessageServiceImpl extends ServiceImpl<UnSysMessageMapper, UnSysMessage> implements IUnSysMessageService {
}
