package com.unique.unexamine.work.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.work.base.entity.UnWorkTaskField;
import com.unique.unexamine.work.base.mapper.UnWorkTaskFieldMapper;
import com.unique.unexamine.work.base.service.IUnWorkTaskFieldService;
import org.springframework.stereotype.Service;

/** UnWorkTaskField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnWorkTaskFieldServiceImpl extends ServiceImpl<UnWorkTaskFieldMapper, UnWorkTaskField> implements IUnWorkTaskFieldService {
}
