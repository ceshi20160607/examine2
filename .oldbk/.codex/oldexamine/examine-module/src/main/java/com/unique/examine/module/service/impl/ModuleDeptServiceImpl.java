package com.unique.examine.module.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.module.entity.po.ModuleDept;
import com.unique.examine.module.mapper.ModuleDeptMapper;
import com.unique.examine.module.service.IModuleDeptService;
import org.springframework.stereotype.Service;

@Service
public class ModuleDeptServiceImpl extends ServiceImpl<ModuleDeptMapper, ModuleDept> implements IModuleDeptService {
}
