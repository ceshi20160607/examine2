package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkProject;
import com.unique.unexamine.work.base.mapper.UnWorkProjectMapper;
import com.unique.unexamine.work.base.service.IUnWorkProjectService;
import org.springframework.stereotype.Service;

/** UnWorkProject 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkProjectServiceImpl extends ServiceImpl<UnWorkProjectMapper, UnWorkProject> implements IUnWorkProjectService {
}
