package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.ModuleField;
import com.unique.examine.base.mapper.ModuleFieldMapper;
import com.unique.examine.base.service.IModuleFieldService;
import org.springframework.stereotype.Service;

@Service
public class ModuleFieldServiceImpl extends ServiceImpl<ModuleFieldMapper, ModuleField> implements IModuleFieldService {
}
