package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkTaskGroup;
import com.unique.unexamine.work.base.mapper.UnWorkTaskGroupMapper;
import com.unique.unexamine.work.base.service.IUnWorkTaskGroupService;
import org.springframework.stereotype.Service;

/** UnWorkTaskGroup 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkTaskGroupServiceImpl extends ServiceImpl<UnWorkTaskGroupMapper, UnWorkTaskGroup> implements IUnWorkTaskGroupService {
}
