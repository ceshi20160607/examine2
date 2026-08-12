package com.unique.examine.plat.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.plat.base.entity.PasswordRecoveryToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PlatPasswordRecoveryTokenMapper extends BaseMapper<PasswordRecoveryToken> {

    @Select("""
            select *
              from un_plat_password_recovery_token
             where token_hash = #{tokenHash}
             limit 1
             for update
            """)
    PasswordRecoveryToken lockByHash(@Param("tokenHash") String tokenHash);

    @Update("""
            update un_plat_password_recovery_token
               set status = 'USED',
                   consumed_at = #{now},
                   updated_at = #{now},
                   version = version + 1
             where id = #{id}
               and status = 'ACTIVE'
               and version = #{version}
            """)
    int consume(@Param("id") long id, @Param("version") long version,
                @Param("now") LocalDateTime now);

    @Update("""
            update un_plat_password_recovery_token
               set status = 'REVOKED',
                   revoked_at = #{now},
                   updated_at = #{now},
                   version = version + 1
             where id = #{id}
               and status = 'ACTIVE'
            """)
    int revoke(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            update un_plat_password_recovery_token
               set status = 'REVOKED',
                   revoked_at = #{now},
                   updated_at = #{now},
                   version = version + 1
             where account_id = #{accountId}
               and status = 'ACTIVE'
               and (#{exceptId} is null or id <> #{exceptId})
            """)
    int revokeActiveForAccount(@Param("accountId") long accountId,
                               @Param("exceptId") Long exceptId,
                               @Param("now") LocalDateTime now);
}
