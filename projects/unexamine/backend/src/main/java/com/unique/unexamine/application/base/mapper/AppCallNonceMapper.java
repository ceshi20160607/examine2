package com.unique.unexamine.application.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.application.base.entity.AppCallNonce;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 已接受应用签名nonce的防重放记录 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface AppCallNonceMapper extends BaseMapper<AppCallNonce> {

}
