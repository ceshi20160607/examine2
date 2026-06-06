package com.unique.examine.app.base.service.impl;

import com.unique.examine.app.base.entity.Version;
import com.unique.examine.app.base.mapper.VersionMapper;
import com.unique.examine.app.base.service.IVersionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 应用版本 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class VersionServiceImpl extends ServiceImpl<VersionMapper, Version> implements IVersionService {

}
