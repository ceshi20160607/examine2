package com.unique.unexamine.authentication.manage.repository;

import com.unique.unexamine.platform.base.entity.PlatPasswordReset;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 密码找回令牌的事务锁定查询；普通读写统一交给生成的 Base Service。 */
@Mapper
public interface PasswordResetLockMapper {
    /** 串行化同一账号的申请和确认，并读取投递前必须复核的账号状态及邮箱。 */
    @Select("SELECT id, `status`, email FROM un_plat_account WHERE id = #{accountId} FOR UPDATE")
    @Results({
            @Result(column = "id", property = "id", id = true),
            @Result(column = "status", property = "status"),
            @Result(column = "email", property = "email")
    })
    PlatformAccount findAccountByIdForUpdate(@Param("accountId") Long accountId);

    /** 锁定令牌记录，串行化同一链接的消费并保留现有事务内的过期校验。 */
    @Select("SELECT id, account_id, expire_time, used_time "
            + "FROM un_plat_password_reset WHERE token_hash = #{tokenHash} FOR UPDATE")
    @Results({
            @Result(column = "id", property = "id", id = true),
            @Result(column = "account_id", property = "accountId"),
            @Result(column = "expire_time", property = "expireTime"),
            @Result(column = "used_time", property = "usedTime")
    })
    PlatPasswordReset findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
