package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkDaily;
import com.unique.unexamine.work.base.mapper.UnWorkDailyMapper;
import com.unique.unexamine.work.base.service.IUnWorkDailyService;
import org.springframework.stereotype.Service;

/** UnWorkDaily 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkDailyServiceImpl extends ServiceImpl<UnWorkDailyMapper, UnWorkDaily> implements IUnWorkDailyService {
}
