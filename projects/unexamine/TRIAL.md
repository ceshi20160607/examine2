# 本地试用入口

## 当前入口

- 登录页：http://127.0.0.1:15173/login
- 注册页：http://127.0.0.1:15173/register
- 后端健康检查：http://127.0.0.1:18080/actuator/health

## 可查看完整示例系统的账号

- 用户名：`e2e_admin_0812`
- 密码：`correct-password`
- 登录后在“我的系统”进入“验收业务系统”。

该系统已有“客户”模块、两个发布版本和一条示例客户数据，可以查看模块配置、业务运行页和操作审计。

## 默认平台管理员

- 用户名：`admin`
- 密码：`123123aa`

默认平台管理员当前没有加入任何业务系统，所以登录成功后“我的系统”为空；它用于验证平台管理员初始化和登录，不适合查看完整示例业务。

## 服务停止后的启动方式

先启动 MySQL 和 Redis：

```powershell
cd D:\workspace\01_project\snow\cursor\examine2\projects\unexamine\deploy
docker compose up -d
```

再启动后端（本机 Maven 默认 Java 版本不符合要求，必须显式使用 JDK 21）：

```powershell
cd D:\workspace\01_project\snow\cursor\examine2\projects\unexamine\backend
$env:JAVA_HOME='D:\dev\jdk21'
$env:Path='D:\dev\jdk21\bin;' + $env:Path
mvn spring-boot:run
```

最后另开一个终端启动前端：

```powershell
cd D:\workspace\01_project\snow\cursor\examine2\projects\unexamine\frontend
npm.cmd run dev
```

看到前端监听 `15173`、后端健康状态为 `UP` 后，再打开登录页。
