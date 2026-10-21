package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatMessage;
import com.unique.unexamine.plat.base.mapper.UnPlatMessageMapper;
import com.unique.unexamine.plat.base.service.IUnPlatMessageService;
import org.springframework.stereotype.Service;

/** UnPlatMessage 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatMessageServiceImpl extends ServiceImpl<UnPlatMessageMapper, UnPlatMessage> implements IUnPlatMessageService {
}
