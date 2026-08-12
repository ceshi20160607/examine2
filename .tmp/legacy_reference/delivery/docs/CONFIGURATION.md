# 配置与多环境

配置入口是包根 `.env`。样例只给键名，任何密码、token、OpenAPI secret、SMTP/S3 凭证都不得提交到源码、包清单或日志。

## 必填项

- `EXAMINE_DB_NAME/USERNAME/PASSWORD/ROOT_PASSWORD`：MySQL 库和账户。
- `EXAMINE_REDIS_PASSWORD`：Redis 认证。
- `EXAMINE_BOOTSTRAP_ROOT_USERNAME/PASSWORD`：仅首次空库创建 root；首次登录后轮换。
- `EXAMINE_DATA_ROOT`：绝对持久路径，承载文件、日志和备份；必须独立于不可变版本目录并由所有升级版本共享。
- `EXAMINE_WEB_BIND/PORT`、`EXAMINE_BACKEND_BIND/PORT`：主机监听。生产建议绑定内网或 loopback。

## 环境策略

- LOCAL：允许 loopback HTTP，适合开发/验收。
- TEST：使用独立数据库、Redis、文件根和 OpenAPI SecretRef，不复用生产凭证。
- PRODUCTION：设置 `EXAMINE_DEPLOYMENT_MODE=PRODUCTION`、安全 Cookie、TLS MySQL/Redis、可信 HTTPS 公网地址及文件型敏感字段 key ring；缺项时后端 fail-closed。

同一主机多环境必须使用不同 Compose project name、端口和 `EXAMINE_DATA_ROOT`。随包脚本固定 project name 为 `examine2`，因此标准交付是一机一套；多套部署应复制脚本并经变更评审修改 project name，禁止共用数据卷。

## 外部集成

- SMTP/Webhook、S3、外部数据源和 AI provider 在管理页面只保存 `SecretRef`。
- `env://NAME` 指向运行环境变量；页面/API 不回显明文。
- JDBC 目标需进入精确 allowlist，生产 TLS 使用身份校验。
- Webhook 仅允许 HTTPS 公网目标、禁止重定向，并对请求签名。

修改 `.env` 后执行 `restart.ps1` 并再次执行 `health.ps1`。配置变更应记录变更单、操作者、时间、旧值引用和验证结果；秘密值本身不写入变更单。
