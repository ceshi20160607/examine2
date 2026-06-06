package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.ClientCredential;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AK/SK 凭证、密钥密文、轮换和过期状态。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface ClientCredentialMapper extends BaseMapper<ClientCredential> {
}
