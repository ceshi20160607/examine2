package com.unique.unexamine.plat.manage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 首次启动时创建可登录的平台管理员，不重置已有账号。 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AuthManageService auth;

    /** 复用账号业务服务的密码哈希和唯一性规则。 */
    public AdminBootstrap(AuthManageService auth) {
        this.auth = auth;
    }

    /** 启动时保证初始管理员存在。 */
    @Override
    public void run(ApplicationArguments args) {
        auth.ensureInitialAdmin();
    }
}
