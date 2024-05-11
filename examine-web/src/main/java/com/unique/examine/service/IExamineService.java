package com.unique.examine.service;

import com.unique.examine.entity.bo.ExamineSaveBO;
import com.unique.examine.entity.bo.ExamineSearchBO;
import com.unique.examine.entity.po.Examine;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.entity.vo.ExamineVO;
import com.unique.core.common.BasePage;

import java.util.List;

/**
 * <p>
 * 审批表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
public interface IExamineService extends IService<Examine> {

    void addOrUpdate(ExamineSaveBO saveBO);

    BasePage<ExamineVO> queryPageList(ExamineSearchBO searchBO);

    void deteleByIds(List<Long> ids);
}
