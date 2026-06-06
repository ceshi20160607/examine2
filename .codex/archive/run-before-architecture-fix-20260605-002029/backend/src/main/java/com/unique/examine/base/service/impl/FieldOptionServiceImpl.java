package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.FieldOption;
import com.unique.examine.base.mapper.FieldOptionMapper;
import com.unique.examine.base.service.IFieldOptionService;
import org.springframework.stereotype.Service;

@Service
public class FieldOptionServiceImpl extends ServiceImpl<FieldOptionMapper, FieldOption> implements IFieldOptionService {
}
