package com.unique.examine.mapper;

import com.unique.examine.entity.bo.ExamineSearchBO;
import com.unique.examine.entity.po.Examine;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.entity.vo.ExamineVO;
import com.unique.core.common.BasePage;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 审批表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
public interface ExamineMapper extends BaseMapper<Examine> {

    BasePage<ExamineVO> queryPageList(BasePage<Object> parse, @Param("search") ExamineSearchBO searchBO);
}
