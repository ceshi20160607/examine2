package com.unique.unexamine.common.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.common.base.entity.UnCommonAi;
import com.unique.unexamine.common.base.mapper.UnCommonAiMapper;
import com.unique.unexamine.common.base.service.IUnCommonAiService;
import org.springframework.stereotype.Service;

/** UnCommonAi 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnCommonAiServiceImpl extends ServiceImpl<UnCommonAiMapper, UnCommonAi> implements IUnCommonAiService {
}
