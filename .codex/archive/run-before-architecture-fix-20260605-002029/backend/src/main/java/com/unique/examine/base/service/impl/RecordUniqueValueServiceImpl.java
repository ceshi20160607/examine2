package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.RecordUniqueValue;
import com.unique.examine.base.mapper.RecordUniqueValueMapper;
import com.unique.examine.base.service.IRecordUniqueValueService;
import org.springframework.stereotype.Service;

@Service
public class RecordUniqueValueServiceImpl extends ServiceImpl<RecordUniqueValueMapper, RecordUniqueValue> implements IRecordUniqueValueService {
}
