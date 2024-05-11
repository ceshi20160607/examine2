package com.unique.examine.service;

import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.po.ExamineRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.entity.vo.ExamineRecordVO;

/**
 * <p>
 * 审核记录表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
public interface IExamineRecordService extends IService<ExamineRecord> {

    void create(ExamineContext context);

    void process(ExamineContext context);

    ExamineRecordVO queryById(Long recordId);
}
