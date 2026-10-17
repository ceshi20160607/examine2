package com.unique.unexamine.backgroundjobs.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 后台作业业务申请与最终审计；运行状态和重试事实保存在 Redis Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface JobBackgroundMapper extends BaseMapper<JobBackground> {

}
