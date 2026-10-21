package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatMenu;
import com.unique.unexamine.plat.base.mapper.UnPlatMenuMapper;
import com.unique.unexamine.plat.base.service.IUnPlatMenuService;
import org.springframework.stereotype.Service;

/** UnPlatMenu 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatMenuServiceImpl extends ServiceImpl<UnPlatMenuMapper, UnPlatMenu> implements IUnPlatMenuService {
}
