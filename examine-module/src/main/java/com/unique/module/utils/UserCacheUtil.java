package com.unique.module.utils;

import cn.hutool.core.util.ObjectUtil;
import com.unique.core.config.ApplicationContextHolder;
import com.unique.core.context.BaseConst;
import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.core.redis.Redis;
import com.unique.module.entity.po.ModuleUser;
import com.unique.module.service.IModuleUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * @author UNIQUE
 * 用户缓存相关方法
 */
@Component
public class UserCacheUtil {
    static UserCacheUtil ME;

    @PostConstruct
    public void init() {
        ME = this;
    }

    @Autowired
    Redis redis;
    @Autowired
    private IModuleUserService moduleUserService;

    /**
     * 获取redis
     *
     * @return redis
     */
    public static Redis getRedis() {
        return UserCacheUtil.ME.redis;
    }

    /**
     * 根据用户ID获取用户名
     * @param userId 用户ID
     * @return data
     */
    public static SimpleUser getUserInfo(Long userId) {
        if (ObjectUtil.isNotEmpty(ME.redis.get(BaseConst.CACHE_USER_KEY + userId))) {
            return ME.redis.get(BaseConst.CACHE_USER_KEY + userId);
        }
        ModuleUser queryOne = ApplicationContextHolder.getBean(IModuleUserService.class).getById(userId);
        if (ObjectUtil.isNotEmpty(queryOne)) {
            SimpleUser item = new SimpleUser();
            item.setId(queryOne.getId());
            item.setUsername(queryOne.getUsername());
            item.setRealname(queryOne.getRealname());
            item.setMobile(queryOne.getMobile());
            item.setEmail(queryOne.getEmail());
            ME.redis.setNx(BaseConst.CACHE_USER_KEY + queryOne.getId(), BaseConst.MAX_USER_EXIST_TIME,item);
            return item;
        }
        return null;
    }


}
