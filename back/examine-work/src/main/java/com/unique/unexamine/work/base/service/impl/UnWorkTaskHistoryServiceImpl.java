package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkTaskHistory;
import com.unique.unexamine.work.base.mapper.UnWorkTaskHistoryMapper;
import com.unique.unexamine.work.base.service.IUnWorkTaskHistoryService;
import org.springframework.stereotype.Service;

/** UnWorkTaskHistory 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkTaskHistoryServiceImpl extends ServiceImpl<UnWorkTaskHistoryMapper, UnWorkTaskHistory> implements IUnWorkTaskHistoryService {
}
