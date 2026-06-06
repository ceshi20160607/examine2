# 构建验证报告

验证时间：2026-06-04 23:27-23:28 Asia/Shanghai

复现前置条件：
- 工作目录：`E:\workspace\03_project\unique\java\codex`
- 只读取输入：`backend/`、`frontend/`、`docs/service_info.md`
- service_info 声明后端优先使用 JDK 21，声明 JDK 路径 `D:\Tools\JDK\JDK-21.0.6+7`；该路径本机不可用，`Test-Path=False`
- service_info 未明确声明 Maven 可执行文件路径；声明路径 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd` 本机不可用，`Test-Path=False`
- service_info 声明前端 Node.js 可使用 `D:\Tools\node-v24.15.0-win-x64`；该路径本机不可用，`Test-Path=False`
- 实际回退工具：JDK `D:\java\jdk\jdk21`，Maven `D:\java\apache-maven-3.8.5\bin\mvn.cmd`，Node.js `D:\java\nodejs\node.exe`，npm `D:\java\nodejs\npm.cmd`

## 后端验证命令

工具路径与版本：
- `D:\java\jdk\jdk21\bin\java.exe`：Java 21.0.10
- `D:\java\jdk\jdk21\bin\javac.exe`：javac 21.0.10
- `D:\java\apache-maven-3.8.5\bin\mvn.cmd`：Apache Maven 3.8.5
- Maven 运行时 Java：`D:\java\jdk\jdk21`，Java version 21.0.10

执行目录：`E:\workspace\03_project\unique\java\codex\backend`

执行命令：
```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
if (Test-Path -LiteralPath 'target') { Remove-Item -LiteralPath 'target' -Recurse -Force }
& 'D:\java\apache-maven-3.8.5\bin\mvn.cmd' clean compile
```

## 后端验证结果

结果：通过。

验证范围：
- 已先删除/清理 `backend/target`，随后执行 Maven `clean compile`，避免只命中增量缓存或仅显示 `Nothing to compile`
- Maven 执行了 `maven-clean-plugin:3.3.2:clean`
- Maven 执行了 `maven-resources-plugin:3.3.1:resources`
- Maven 执行了 `maven-compiler-plugin:3.13.0:compile`
- `src/main/java` 下统计到 221 个 Java 源文件
- 编译日志依据：`Recompiling the module because of changed source code.`
- 编译日志依据：`Compiling 221 source files with javac [debug parameters release 21] to target\classes`
- 成功日志依据：`BUILD SUCCESS`
- 编译后 `target\classes` 下生成 224 个文件
- Maven 总耗时：7.782 s

## 前端验证命令

工具路径与版本：
- `D:\java\nodejs\node.exe`：v24.14.0
- `D:\java\nodejs\npm.cmd`：11.9.0
- `D:\java\nodejs\npm.ps1`：存在，但 PowerShell 执行策略拦截

PowerShell `npm.ps1` 检查命令：
```powershell
& 'D:\java\nodejs\npm.ps1' --version
```

`npm.ps1` 拦截日志：
```text
File D:\java\nodejs\npm.ps1 cannot be loaded because running scripts is disabled on this system.
FullyQualifiedErrorId : UnauthorizedAccess
```

实际执行目录：`E:\workspace\03_project\unique\java\codex\frontend`

实际执行命令：
```powershell
if (Test-Path -LiteralPath 'dist') { Remove-Item -LiteralPath 'dist' -Recurse -Force }
if (Test-Path -LiteralPath 'tsconfig.tsbuildinfo') { Remove-Item -LiteralPath 'tsconfig.tsbuildinfo' -Force }
& 'D:\java\nodejs\npm.cmd' run build
```

## 前端验证结果

结果：通过。

验证范围：
- 已删除 `frontend/dist`
- 已删除 `frontend/tsconfig.tsbuildinfo`
- 使用 `D:\java\nodejs\npm.cmd` 执行 clean build，避开 PowerShell `npm.ps1` 执行策略拦截
- `package.json` 的 build 脚本为 `vue-tsc -b && vite build`
- `frontend/src` 下统计到 44 个源码文件
- Vite 构建日志依据：`vite v6.4.3 building for production...`
- Vite 构建日志依据：`1694 modules transformed.`
- 成功日志依据：`built in 6.04s`
- 构建产物：`dist/index.html` 414 bytes
- 构建产物：`dist/assets/index-CQ2nHCns.js` 1174349 bytes
- 构建产物：`dist/assets/index-DVYwlI_R.css` 361880 bytes

## 失败日志摘要

本轮后端与前端 clean 构建均成功，无构建失败日志。

非失败但需记录的信息：
- service_info 中声明或推定的 `D:\Tools\...` 工具路径在当前机器不可用，因此使用本机可用 JDK 21、Maven、Node.js、npm 路径完成验证
- PowerShell `D:\java\nodejs\npm.ps1` 被执行策略拦截，实际使用 `D:\java\nodejs\npm.cmd`
- javac 输出注解处理提示：类路径中发现处理程序，因此启用批注处理；这是编译提示，不影响 `BUILD SUCCESS`
- Vite/Rollup 输出两个 `@vueuse/core` PURE 注释位置警告；这是打包警告，不影响构建成功
- Vite 输出 chunk 超过 500 kB 警告；这是性能优化提示，不影响构建成功

## validator 结论

通过。后端已完成删除 `target` 后的 `clean compile`，并以 221 个源码文件重新编译和 Maven `BUILD SUCCESS` 作为依据；前端已完成删除 `dist` 与 `tsconfig.tsbuildinfo` 后的 `npm.cmd run build`，并以 Vite 成功构建、1694 个模块转换和 `dist` 产物生成作为依据。
