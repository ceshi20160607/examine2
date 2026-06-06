package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.BusinessSystem;
import com.unique.examine.base.mapper.BusinessSystemMapper;
import com.unique.examine.base.service.IBusinessSystemService;
import org.springframework.stereotype.Service;

@Service
public class BusinessSystemServiceImpl extends ServiceImpl<BusinessSystemMapper, BusinessSystem> implements IBusinessSystemService {
}
