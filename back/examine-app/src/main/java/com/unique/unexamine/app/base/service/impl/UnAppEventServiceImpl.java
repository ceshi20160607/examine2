package com.unique.unexamine.app.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.app.base.entity.UnAppEvent;
import com.unique.unexamine.app.base.mapper.UnAppEventMapper;
import com.unique.unexamine.app.base.service.IUnAppEventService;
import org.springframework.stereotype.Service;

/** UnAppEvent 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnAppEventServiceImpl extends ServiceImpl<UnAppEventMapper, UnAppEvent> implements IUnAppEventService {
}
