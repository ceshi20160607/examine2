package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.RecordValue;
import com.unique.examine.base.mapper.RecordValueMapper;
import com.unique.examine.base.service.IRecordValueService;
import org.springframework.stereotype.Service;

@Service
public class RecordValueServiceImpl extends ServiceImpl<RecordValueMapper, RecordValue> implements IRecordValueService {
}
