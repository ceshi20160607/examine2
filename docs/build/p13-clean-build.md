# P13 Clean Build 记录

## 前端

命令：

```powershell
$env:Path='D:\java\nodejs;'+$env:Path
npm.cmd run build
```

结果：pass。

产物：

- `frontend/dist/index.html`
- `frontend/dist/assets/index-DmtoX7mS.css`
- `frontend/dist/assets/index-Cn0cifVF.js`

## 后端

命令：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn.cmd -pl examine-web -am clean package -DskipTests
```

结果：pass。

产物：

- `backend/examine-web/target/unexamine.jar`

## VAL-009 结论

P13 clean build/package gate 通过。后续仍需 REV-009 审查通过后，才允许 PKG-002 刷新最终部署包。
