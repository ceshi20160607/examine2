package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.AppVersion;
import com.unique.examine.base.mapper.AppVersionMapper;
import com.unique.examine.base.service.IAppVersionService;
import org.springframework.stereotype.Service;

@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion> implements IAppVersionService {
}
