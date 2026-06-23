# 部署与本地运行

## 前置

1. JDK **21**、Maven **3.8+**、Node **18+**、MySQL **8**
2. 执行 `sql/init.sql` 建库建表
3. 修改 `backend/examine-web/src/main/resources/application.yml` 数据库连接（如需要）

## 1. 启动后端

```powershell
# 或一键构建：powershell -File scripts/build.ps1
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
cd backend
mvn clean package -DskipTests
java -jar examine-web\target\examine-web-0.0.1-SNAPSHOT.jar
```

健康检查：`http://localhost:9999/actuator/health`

## 2. 启动前端（开发）

```powershell
cd frontend
npm install
npm run dev
```

浏览器：`http://localhost:5173`

## 3. 生产构建

```powershell
cd frontend
npm run build
```

静态文件：`frontend/dist/`

## 4. Nginx 示例

```nginx
location / {
    root /path/to/frontend/dist;
    try_files $uri $uri/ /index.html;
}
location /api/ {
    proxy_pass http://127.0.0.1:9999/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

## 5. 车系统验收剧本

1. 登录 `plat_admin` 或注册新用户建系统  
2. 系统后台：字典 → 模块组 → 模块 → 建模发布  
3. 添加成员 `che`，授权角色  
4. `che` 登录：仪表盘 → 车辆管理 → 列表 CRUD  
5. 列表：场景、筛选、导出、右侧详情  

## 6. 当前 Build 状态（2026-06-22）

| 层 | 状态 |
|----|------|
| 后端 compile | ✅ BUILD SUCCESS（约 4min） |
| 前端 build | ✅ `frontend/dist/` 已生成 |
| 构建脚本 | ✅ `scripts/build.ps1`（自动用 `D:\dev\jdk21` + `.tools` Maven/Node） |
| 本地运行 | ✅ 后端 `:9999` + 前端 `:5173` 已启动 |

详见 [`design/build-readiness.md`](./design/build-readiness.md)。
