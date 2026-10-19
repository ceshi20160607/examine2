package com.unique.unexamine.manage.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.UnPlatAccount;
import com.unique.unexamine.base.mapper.UnPlatAccountMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 全新数据库首次启动时创建平台入口账号；已有账号不会被启动参数覆盖。 */
@Component
public class PlatformAccountBootstrap implements ApplicationRunner {
    private final UnPlatAccountMapper accountMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PlatformAccountBootstrap(UnPlatAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** 仅空账号库执行一次初始化，密码不写入工程文件。 */
    @Override
    public void run(ApplicationArguments args) {
        Long existing = accountMapper.selectCount(new LambdaQueryWrapper<>());
        if (existing > 0) {
            return;
        }
        String password = System.getenv("APP_BOOTSTRAP_PASSWORD");
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("空账号库首次启动需设置至少 12 位的 APP_BOOTSTRAP_PASSWORD");
        }
        String accountName = System.getenv().getOrDefault("APP_BOOTSTRAP_ACCOUNT", "admin").trim();
        if (!accountName.matches("[A-Za-z0-9_.-]{3,64}")) {
            throw new IllegalStateException("APP_BOOTSTRAP_ACCOUNT 只能使用 3 到 64 位字母、数字、点、横线或下划线");
        }
        UnPlatAccount account = new UnPlatAccount();
        account.setAccount(accountName);
        account.setPasswordHash(encoder.encode(password));
        account.setDisplayName("平台管理员");
        account.setStatus((byte) 1);
        accountMapper.insert(account);
    }
}
