package com.unique.examine.module.base.service.impl;

import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.mapper.ModelMapper;
import com.unique.examine.module.base.service.IModelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 动态模块模型 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class ModelServiceImpl extends ServiceImpl<ModelMapper, Model> implements IModelService {

}
