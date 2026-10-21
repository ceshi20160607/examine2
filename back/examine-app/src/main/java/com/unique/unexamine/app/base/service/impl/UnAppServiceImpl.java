package com.unique.unexamine.app.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.app.base.entity.UnApp;
import com.unique.unexamine.app.base.mapper.UnAppMapper;
import com.unique.unexamine.app.base.service.IUnAppService;
import org.springframework.stereotype.Service;

/** UnApp 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnAppServiceImpl extends ServiceImpl<UnAppMapper, UnApp> implements IUnAppService {
}
