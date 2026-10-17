package com.unique.unexamine.print.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.print.base.entity.PrintTemplatePublication;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 打印模板当前生效版本指针，可回滚至历史版本 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface PrintTemplatePublicationMapper extends BaseMapper<PrintTemplatePublication> {

}
