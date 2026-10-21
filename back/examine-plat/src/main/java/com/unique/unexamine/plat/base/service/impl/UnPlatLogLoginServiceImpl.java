package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatLogLogin;
import com.unique.unexamine.plat.base.mapper.UnPlatLogLoginMapper;
import com.unique.unexamine.plat.base.service.IUnPlatLogLoginService;
import org.springframework.stereotype.Service;

/** UnPlatLogLogin 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatLogLoginServiceImpl extends ServiceImpl<UnPlatLogLoginMapper, UnPlatLogLogin> implements IUnPlatLogLoginService {
}
