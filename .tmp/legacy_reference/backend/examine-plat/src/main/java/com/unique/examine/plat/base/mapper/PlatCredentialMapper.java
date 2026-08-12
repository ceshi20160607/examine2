package com.unique.examine.plat.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.plat.base.entity.Credential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@Mapper
public interface PlatCredentialMapper extends BaseMapper<Credential> {

    @Update("""
            update un_plat_credential
               set password_hash = #{passwordHash},
                   password_algorithm = #{passwordAlgorithm},
                   password_parameters = #{passwordParameters},
                   failed_attempts = 0,
                   locked_until = null,
                   password_changed_at = #{passwordChangedAt},
                   updated_at = #{updatedAt},
                   version = version + 1
             where id = #{id}
               and version = #{version}
            """)
    int replacePassword(Credential credential);

}
