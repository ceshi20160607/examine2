package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.RecordComment;
import com.unique.examine.base.mapper.RecordCommentMapper;
import com.unique.examine.base.service.IRecordCommentService;
import org.springframework.stereotype.Service;

@Service
public class RecordCommentServiceImpl extends ServiceImpl<RecordCommentMapper, RecordComment> implements IRecordCommentService {
}
