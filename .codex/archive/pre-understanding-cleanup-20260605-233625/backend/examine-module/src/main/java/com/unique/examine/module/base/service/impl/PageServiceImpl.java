package com.unique.examine.module.base.service.impl;

import com.unique.examine.module.base.entity.Page;
import com.unique.examine.module.base.mapper.PageMapper;
import com.unique.examine.module.base.service.IPageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 模块页面 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class PageServiceImpl extends ServiceImpl<PageMapper, Page> implements IPageService {

}
