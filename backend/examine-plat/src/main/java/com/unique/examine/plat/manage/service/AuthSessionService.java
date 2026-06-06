package com.unique.examine.plat.manage.service;

import com.unique.examine.plat.manage.bo.LoginBO;
import com.unique.examine.plat.manage.bo.RefreshTokenBO;
import com.unique.examine.plat.manage.bo.RegisterBO;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.AuthTokenVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;

/**
 * 认证会话服务。
 */
public interface AuthSessionService {

    /**
     * 注册平台账号。
     *
     * @param registerBO 注册入参
     * @return 注册账号
     */
    AuthAccountVO register(RegisterBO registerBO);

    /**
     * 平台账号登录。
     *
     * @param loginBO 登录入参
     * @return token 信息
     */
    AuthTokenVO login(LoginBO loginBO);

    /**
     * 刷新 accessToken。
     *
     * @param refreshTokenBO 刷新入参
     * @return 新 token 信息
     */
    AuthTokenVO refresh(RefreshTokenBO refreshTokenBO);

    /**
     * 退出当前会话。
     *
     * @param accessToken accessToken
     */
    void logout(String accessToken);

    /**
     * 查询当前登录用户。
     *
     * @param accessToken accessToken
     * @return 当前用户
     */
    CurrentUserVO me(String accessToken);
}
