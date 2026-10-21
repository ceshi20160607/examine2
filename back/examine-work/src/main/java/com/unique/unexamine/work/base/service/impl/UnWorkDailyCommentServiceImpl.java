package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkDailyComment;
import com.unique.unexamine.work.base.mapper.UnWorkDailyCommentMapper;
import com.unique.unexamine.work.base.service.IUnWorkDailyCommentService;
import org.springframework.stereotype.Service;

/** UnWorkDailyComment 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkDailyCommentServiceImpl extends ServiceImpl<UnWorkDailyCommentMapper, UnWorkDailyComment> implements IUnWorkDailyCommentService {
}
