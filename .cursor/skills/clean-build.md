# Skill: clean-build

## 触发

Build 批次末；或 frontend/backend 大改后。

## 命令

```powershell
# backend
$env:JAVA_HOME='D:\dev\jdk21-temurin'
$env:Path="$env:JAVA_HOME\bin;D:\dev\maven\bin;$env:Path"
mvn.cmd -pl examine-web -am clean compile test

# frontend
cd frontend
npm.cmd run build
```

## 输出

`docs/evidence/build-{date}.md` — 命令、结果、JDK/Node 版本、dist 路径清单

## 规则

- 必须 clean，不得用增量「Nothing to compile」糊弄
- frontend 必须有 `frontend/dist/`
