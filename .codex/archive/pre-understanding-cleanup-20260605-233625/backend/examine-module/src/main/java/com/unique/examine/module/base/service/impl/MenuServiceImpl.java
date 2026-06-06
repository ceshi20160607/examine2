package com.unique.examine.module.base.service.impl;

import com.unique.examine.module.base.entity.Menu;
import com.unique.examine.module.base.mapper.MenuMapper;
import com.unique.examine.module.base.service.IMenuService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 模块菜单 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements IMenuService {

}
