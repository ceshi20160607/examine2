# nginx 部署说明

## 部署目标

前端 `frontend/dist/` 作为静态资源部署到 nginx，后端 `unexamine.jar` 运行在内网端口，浏览器只访问同一个站点域名。

正常访问形态：

- 页面入口：`http://服务器:端口/`
- 业务接口：`http://服务器:端口/api/v1/...`
- 接口文档：`http://服务器:端口/doc.html` 或 `http://服务器:端口/swagger-ui/index.html`

前端默认不会写死 `localhost`、局域网 IP 或后端端口；代码中的接口契约已经是 `/api/v1/...` 相对路径，因此 nginx 必须把 `/api/` 原样转发给后端。

## 后端启动

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -jar unexamine.jar
```

默认后端端口：`9999`。

## 推荐 nginx 配置

下面示例假设：

- 前端 dist 目录：`D:/deploy/unexamine/frontend-dist`
- 后端地址：`http://127.0.0.1:9999`
- nginx 对外端口：`19999`

```nginx
server {
    listen 19999;
    server_name _;

    root D:/deploy/unexamine/frontend-dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:9999;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /doc.html {
        proxy_pass http://127.0.0.1:9999;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /swagger-ui/ {
        proxy_pass http://127.0.0.1:9999;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /v3/api-docs {
        proxy_pass http://127.0.0.1:9999;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /webjars/ {
        proxy_pass http://127.0.0.1:9999;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

注意：`location /api/` 中的 `proxy_pass http://127.0.0.1:9999;` 后面不能加 `/`。如果写成 `proxy_pass http://127.0.0.1:9999/;`，nginx 会剥掉 `/api` 前缀，后端收到 `/v1/...`，接口会路由失败。

## 验证命令

部署后建议先执行：

```powershell
curl.exe -i http://192.168.0.211:19999/
curl.exe -i http://192.168.0.211:19999/api/v1/platform/audit/operation-logs?pageNo=1&pageSize=10
curl.exe -I http://192.168.0.211:19999/doc.html
curl.exe -I http://192.168.0.211:19999/v3/api-docs
```

未登录访问业务接口返回 `401 COMMON_UNAUTHORIZED` 属于正常结果，说明请求已经经过 nginx 到达后端。若响应中的 `meta.path` 变成 `/v1/...`，说明 nginx 仍在剥 `/api` 前缀，需要调整 `proxy_pass`。
