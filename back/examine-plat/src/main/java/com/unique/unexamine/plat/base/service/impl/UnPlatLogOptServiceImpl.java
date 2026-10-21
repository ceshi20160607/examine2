package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.mapper.UnPlatLogOptMapper;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import org.springframework.stereotype.Service;

/** UnPlatLogOpt 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatLogOptServiceImpl extends ServiceImpl<UnPlatLogOptMapper, UnPlatLogOpt> implements IUnPlatLogOptService {
}
