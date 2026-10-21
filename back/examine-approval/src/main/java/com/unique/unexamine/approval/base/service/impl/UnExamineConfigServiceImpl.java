package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineConfig;
import com.unique.unexamine.approval.base.mapper.UnExamineConfigMapper;
import com.unique.unexamine.approval.base.service.IUnExamineConfigService;
import org.springframework.stereotype.Service;

/** UnExamineConfig 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineConfigServiceImpl extends ServiceImpl<UnExamineConfigMapper, UnExamineConfig> implements IUnExamineConfigService {
}
