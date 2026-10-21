package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkDailyField;
import com.unique.unexamine.work.base.mapper.UnWorkDailyFieldMapper;
import com.unique.unexamine.work.base.service.IUnWorkDailyFieldService;
import org.springframework.stereotype.Service;

/** UnWorkDailyField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkDailyFieldServiceImpl extends ServiceImpl<UnWorkDailyFieldMapper, UnWorkDailyField> implements IUnWorkDailyFieldService {
}
