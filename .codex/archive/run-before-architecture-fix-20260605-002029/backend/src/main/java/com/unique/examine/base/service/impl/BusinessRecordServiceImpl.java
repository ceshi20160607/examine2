package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.BusinessRecord;
import com.unique.examine.base.mapper.BusinessRecordMapper;
import com.unique.examine.base.service.IBusinessRecordService;
import org.springframework.stereotype.Service;

@Service
public class BusinessRecordServiceImpl extends ServiceImpl<BusinessRecordMapper, BusinessRecord> implements IBusinessRecordService {
}
