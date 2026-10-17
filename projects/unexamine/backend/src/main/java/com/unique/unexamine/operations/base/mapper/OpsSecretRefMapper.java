package com.unique.unexamine.operations.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.operations.base.entity.OpsSecretRef;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 受控密钥引用及当前版本，不保存密钥明文 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface OpsSecretRefMapper extends BaseMapper<OpsSecretRef> {

}
