package com.unique.unexamine.common.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.common.base.entity.UnCommonNotice;
import com.unique.unexamine.common.base.mapper.UnCommonNoticeMapper;
import com.unique.unexamine.common.base.service.IUnCommonNoticeService;
import org.springframework.stereotype.Service;

/** UnCommonNotice 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnCommonNoticeServiceImpl extends ServiceImpl<UnCommonNoticeMapper, UnCommonNotice> implements IUnCommonNoticeService {
}
