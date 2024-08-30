package com.unique.core.utils;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.hutool.core.util.RandomUtil;
import com.unique.core.context.Const;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 加密工具类
 *
 * @author UNIQUE
 * @create 2023-03-07
 * @verson 1.0.0
 */
@Slf4j
@Component
public class EncryptUtil {

    /**
     * 新建用户  设置密码
     *
     * @param username
     * @param password
     * @return {@link String }
     */
    public static String encryUserPwd(String username,String password) {
        String salt = RandomUtil.randomString(32);
        return encryUserPwdSalt(username,password,salt);
    }

    /**
     * 更新 用户密码
     *
     * @param username
     * @param password
     * @param salt
     * @return { com.unique.admin.entity.po.AdminUser}
     */
    public static String encryUserPwdSalt(String username,String password,String salt) {
        return SaSecureUtil.md5BySalt(username + Const.SEPARATOR_COLON + password, salt);
    }

    /**
     * 校验用户密码
     *
     * @param username
     * @param password
     * @param salt
     * @param encryPwd
     * @return { java.lang.Boolean}
     */
    public static Boolean checkUserPwd(String username,String password,String salt,String encryPwd) {
        String md5Password = encryUserPwdSalt(username,password,salt);
        return md5Password.equals(encryPwd);
    }
}
