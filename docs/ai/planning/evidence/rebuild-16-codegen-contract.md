# 第 16 次重构：基础 CRUD 生成合同验证

2026-10-21 在仓库根目录使用本机 JDK 21 执行：

```powershell
$env:JAVA_HOME='D:\dev\jdk21'
$env:Path="${env:JAVA_HOME}\bin;${env:Path}"
mvn -f template_base/mybatis-plus-generator/pom.xml clean test -q
mvn -f back/pom.xml clean package -q
```

两条命令均以退出码 0 结束。生成器的 `TemplateContractTest` 运行 2 个测试，失败 0、错误 0：

- 渲染并编译含五方法的 `IService/ServiceImpl`，执行生成的 `information`：宽度 50%、50% 的字段同在第 1 行，随后宽度 100% 的字段在第 2 行；扩展字段值来自传入的模块数据。
- 在独立 H2 样表上运行真正的 MyBatis-Plus 生成器并编译输出；将生成的 Mapper/Service 接入 H2，实际执行新增、带等值条件的 SQL 分页、按主键读取、更新、删除。两条符合条件记录在页大小 1 时返回 1 条、总数 2。

只验证通用模板及空后端骨架。当前 `unexamine_r16` 还没有业务表，业务持久化、授权、HTTP 成功/越权/失败结果及前端均未验证，也不计入本项通过。
