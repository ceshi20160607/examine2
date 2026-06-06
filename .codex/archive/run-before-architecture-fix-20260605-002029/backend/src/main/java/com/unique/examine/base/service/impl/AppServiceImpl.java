package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.App;
import com.unique.examine.base.mapper.AppMapper;
import com.unique.examine.base.service.IAppService;
import org.springframework.stereotype.Service;

@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements IAppService {
}
