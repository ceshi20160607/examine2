package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.Client;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部客户端，绑定系统、租户和状态。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface ClientMapper extends BaseMapper<Client> {
}
