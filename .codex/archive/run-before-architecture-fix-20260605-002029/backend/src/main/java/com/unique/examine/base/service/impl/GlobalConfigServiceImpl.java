package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.GlobalConfig;
import com.unique.examine.base.mapper.GlobalConfigMapper;
import com.unique.examine.base.service.IGlobalConfigService;
import org.springframework.stereotype.Service;

@Service
public class GlobalConfigServiceImpl extends ServiceImpl<GlobalConfigMapper, GlobalConfig> implements IGlobalConfigService {
}
