# 本次重新执行的验证结果

日期：2026-09-05。代码范围：当前工作区，包括审查前已有未提交修改。没有修改产品源码。

| 检查 | 本次结果 | 解释 |
|---|---|---|
| workspace-validate | 通过 | 工作区边界有效，不代表业务完成 |
| npm run build | 通过 | TypeScript 检查与 Vite 构建成功；主 JS 3,072.22 kB，gzip 938.34 kB，有大包警告 |
| npm test | 34 文件、100 测试通过 | 包括源码字符串断言和函数测试；不能替代页面业务验收 |
| Java 21 mvn -q -DskipTests package | 通过 | 后端可打包 |
| 本地容器启动 | 成功 | 数据库 V39，数据库、Redis、文件和迁移自检通过 |
| Java 21 mvn -q test | 退出码 1；62 测试，12 通过，3 断言失败，47 错误，0 跳过 | 25 个测试类全部运行结束；不能报告为全绿 |

三项断言失败：

1. IdentityOrganizationContextIntegrationTest.java:102：预期 1 个 ActionDecision，实际 3 个，其中另两个是拒绝结果。这证明测试与当前接口契约不一致；不能据此认定发生了越权。
2. PlatformFoundationMigrationTest.java:39：预期 155 张表，实际 160 张。
3. RuntimeMetadataMigrationTest.java:37：同样预期 155，实际 160。两者体现迁移断言没有随当前结构同步，不证明迁移本身失败。

47 项错误来自若干测试 ApplicationContext 初始化失败及连带错误。报告中的根因包括 Windows loopback SocketException、XNIO provider 初始化、Undertow 启动以及 Redis 启动预检错误。当前本地 Linux 容器中的实际后端可以运行，因此这些错误必须列为测试环境/测试启动问题，不能全部计为 47 个业务功能故障。需要单独修复环境与断言后才能形成可信的后端全量绿色结论。

原始结果：`projects/unexamine/backend/target/surefire-reports/`。本摘要读取的是此次开始时间之后更新的报告，不混用旧报告。

页面实测结果与截图见同目录 review.md。没有更改历史周期证据、完成状态或产品需求。
