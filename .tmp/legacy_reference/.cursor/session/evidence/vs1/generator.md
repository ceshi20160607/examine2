# VS1 MyBatis-Plus Generator 证据

- checked_at: `2026-07-10T22:46:04+08:00`
- verdict: `pass`
- generator: `backend/examine-generator`

## 可重复执行

生成器只从 `EXAMINE_DB_URL`、`EXAMINE_DB_USERNAME`、`EXAMINE_DB_PASSWORD` 读取连接信息；非敏感参数显式传入：

```text
--backend-root=<backend> --module-name=<module> --table-prefix=<prefix> --base-package=<package> --execute
```

在最终 schema 上删除四个声明的 generated base 目录后重新执行三次：

| module/prefix | tables | 本次报告文件 |
|---|---:|---:|
| `examine-plat / un_plat_` | 11 | 55 |
| `examine-core / un_sys_` | 3 | 15 |
| `examine-core / un_audit_` | 2 | 10 |

总计 `80` 个 generated 文件；generated controller `0`；generated entity `toString` `0`。重新生成后 Maven reactor 测试通过。

报告位于 `backend/generator-reports/*.json`，每份只列当前前缀实际生成的文件，不包含手写 manage 代码和凭据。
