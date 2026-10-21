package com.unique.unexamine.sys.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.mapper.UnSysDepartmentMapper;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import org.springframework.stereotype.Service;

/** UnSysDepartment 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnSysDepartmentServiceImpl extends ServiceImpl<UnSysDepartmentMapper, UnSysDepartment> implements IUnSysDepartmentService {
}
