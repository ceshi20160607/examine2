package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatRoleData;
import com.unique.unexamine.plat.base.mapper.UnPlatRoleDataMapper;
import com.unique.unexamine.plat.base.service.IUnPlatRoleDataService;
import org.springframework.stereotype.Service;

/** UnPlatRoleData 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatRoleDataServiceImpl extends ServiceImpl<UnPlatRoleDataMapper, UnPlatRoleData> implements IUnPlatRoleDataService {
}
