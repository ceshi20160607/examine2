package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkTask;
import com.unique.unexamine.work.base.mapper.UnWorkTaskMapper;
import com.unique.unexamine.work.base.service.IUnWorkTaskService;
import org.springframework.stereotype.Service;

/** UnWorkTask 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkTaskServiceImpl extends ServiceImpl<UnWorkTaskMapper, UnWorkTask> implements IUnWorkTaskService {
}
