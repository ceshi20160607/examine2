package com.unique.unexamine.manage.platform;

import com.unique.unexamine.identity.base.entity.UnPlatAccount;
import com.unique.unexamine.identity.base.entity.UnPlatMember;
import com.unique.unexamine.identity.base.entity.UnPlatMemberRole;
import com.unique.unexamine.identity.base.entity.UnPlatRole;
import com.unique.unexamine.identity.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.identity.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.identity.base.mapper.UnPlatMemberRoleMapper;
import com.unique.unexamine.identity.base.mapper.UnPlatRoleMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 全新数据库首次启动时创建平台入口账号；已有账号不会被启动参数覆盖。 */
@Component
public class PlatformAccountBootstrap implements ApplicationRunner {
    private final UnPlatAccountMapper accountMapper;
    private final UnPlatMemberMapper memberMapper;
    private final UnPlatRoleMapper roleMapper;
    private final UnPlatMemberRoleMapper memberRoleMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PlatformAccountBootstrap(UnPlatAccountMapper accountMapper, UnPlatMemberMapper memberMapper,
                                    UnPlatRoleMapper roleMapper, UnPlatMemberRoleMapper memberRoleMapper) {
        this.accountMapper = accountMapper;
        this.memberMapper = memberMapper;
        this.roleMapper = roleMapper;
        this.memberRoleMapper = memberRoleMapper;
    }

    /** 仅空账号库执行一次初始化，密码不写入工程文件。 */
    @Override
    @Transactional
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
        // 首次入口账号需同时具备成员与内置管理员角色，否则只能登录而无法管理工程。
        UnPlatMember member = new UnPlatMember();
        member.setAccountId(account.getId());
        member.setMemberNo("PLATFORM_ADMIN");
        member.setRealName("平台管理员");
        member.setGender((byte) 0);
        member.setStatus((byte) 1);
        memberMapper.insert(member);
        UnPlatRole role = new UnPlatRole();
        role.setRoleCode("platform_admin");
        role.setRoleName("平台管理员");
        role.setRoleType((byte) 0);
        role.setStatus((byte) 1);
        roleMapper.insert(role);
        UnPlatMemberRole relation = new UnPlatMemberRole();
        relation.setMemberId(member.getId());
        relation.setRoleId(role.getId());
        memberRoleMapper.insert(relation);
    }
}
