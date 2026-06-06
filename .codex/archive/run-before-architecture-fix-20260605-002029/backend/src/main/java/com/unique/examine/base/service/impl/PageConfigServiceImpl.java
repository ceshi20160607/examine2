package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.PageConfig;
import com.unique.examine.base.mapper.PageConfigMapper;
import com.unique.examine.base.service.IPageConfigService;
import org.springframework.stereotype.Service;

@Service
public class PageConfigServiceImpl extends ServiceImpl<PageConfigMapper, PageConfig> implements IPageConfigService {
}
