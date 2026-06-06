package com.unique.examine.flow.base.service.impl;

import com.unique.examine.flow.base.entity.Instance;
import com.unique.examine.flow.base.mapper.InstanceMapper;
import com.unique.examine.flow.base.service.IInstanceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 流程实例 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class InstanceServiceImpl extends ServiceImpl<InstanceMapper, Instance> implements IInstanceService {

}
