package com.unique.unexamine.analytics.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.analytics.base.entity.AnaDashboardPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 仪表盘可见及编辑授权，实际数据权限仍由运行态逐次校验 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface AnaDashboardPermissionMapper extends BaseMapper<AnaDashboardPermission> {

}
