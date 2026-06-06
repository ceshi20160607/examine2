package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.RuntimeMenu;
import com.unique.examine.base.mapper.RuntimeMenuMapper;
import com.unique.examine.base.service.IRuntimeMenuService;
import org.springframework.stereotype.Service;

@Service
public class RuntimeMenuServiceImpl extends ServiceImpl<RuntimeMenuMapper, RuntimeMenu> implements IRuntimeMenuService {
}
