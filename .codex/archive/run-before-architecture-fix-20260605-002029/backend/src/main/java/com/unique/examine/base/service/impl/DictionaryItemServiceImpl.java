package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.DictionaryItem;
import com.unique.examine.base.mapper.DictionaryItemMapper;
import com.unique.examine.base.service.IDictionaryItemService;
import org.springframework.stereotype.Service;

@Service
public class DictionaryItemServiceImpl extends ServiceImpl<DictionaryItemMapper, DictionaryItem> implements IDictionaryItemService {
}
