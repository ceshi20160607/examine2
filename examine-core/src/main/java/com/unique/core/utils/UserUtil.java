package com.unique.core.utils;

import cn.hutool.crypto.SecureUtil;
import com.unique.core.entity.user.bo.SimpleUser;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * @author z
 * 用户操作相关方法
 */
@Slf4j
public class UserUtil {

    private static final ThreadLocal<SimpleUser> threadLocal = new ThreadLocal<>();

    /**
     * 获取用户信息
     *
     * @return user
     */
    public static SimpleUser getUser() {
        return threadLocal.get();
    }

    /**
     * 获取用户id
     *
     * @return token
     */
    public static Long getUserId() {
        return Optional.ofNullable(threadLocal.get()).orElse(new SimpleUser()).getId();
    }

    /**
     * 设置用户
     * @param adminUser user
     */
    public static void setUser(SimpleUser adminUser) {
        threadLocal.set(adminUser);
    }


    /**
     * 退出登录
     */
    public static void removeUser() {
        threadLocal.remove();
    }

    /**
     * 验证签名是否正确
     *
     * @param key  key
     * @param salt 盐
     * @param sign 签名
     * @return 是否正确 true为正确
     */
    public static boolean verify(String key, String salt, String sign) {
        return sign.equals(sign(key, salt));
    }

    /**
     * 签名数据
     *
     * @param key  key
     * @param salt 盐
     * @return 加密后的字符串
     */
    public static String sign(String key, String salt) {
        return SecureUtil.md5(key.concat("wk-game").concat(salt));
    }


}
