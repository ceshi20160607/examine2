package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.SerialSequence;
import com.unique.examine.base.mapper.SerialSequenceMapper;
import com.unique.examine.base.service.ISerialSequenceService;
import org.springframework.stereotype.Service;

@Service
public class SerialSequenceServiceImpl extends ServiceImpl<SerialSequenceMapper, SerialSequence> implements ISerialSequenceService {
}
