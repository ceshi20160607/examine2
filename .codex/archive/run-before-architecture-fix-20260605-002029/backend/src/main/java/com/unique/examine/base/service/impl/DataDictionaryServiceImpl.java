package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.DataDictionary;
import com.unique.examine.base.mapper.DataDictionaryMapper;
import com.unique.examine.base.service.IDataDictionaryService;
import org.springframework.stereotype.Service;

@Service
public class DataDictionaryServiceImpl extends ServiceImpl<DataDictionaryMapper, DataDictionary> implements IDataDictionaryService {
}
