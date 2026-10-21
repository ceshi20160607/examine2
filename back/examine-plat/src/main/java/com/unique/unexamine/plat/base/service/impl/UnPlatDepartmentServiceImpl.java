package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import com.unique.unexamine.plat.base.mapper.UnPlatDepartmentMapper;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import org.springframework.stereotype.Service;

/** UnPlatDepartment 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatDepartmentServiceImpl extends ServiceImpl<UnPlatDepartmentMapper, UnPlatDepartment> implements IUnPlatDepartmentService {
}
