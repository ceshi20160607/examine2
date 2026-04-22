# mobile/uniapp

UniApp（uni-ui）手机端工程。

## 本地开发

- **安装依赖**：

```powershell
corepack pnpm -C mobile/uniapp install
```

- **启动 H5**：

```powershell
corepack pnpm -C mobile/uniapp dev:h5
```

启动后打开终端输出的本地地址（类似 `http://localhost:5175/`）。

## 目前进度（Task 0）

- 已引入 `@dcloudio/uni-ui`（`pages.json` 已配置 easycom 自动扫描）
- 启动页：`pages/boot/health`
  - 通过 `src/api/http.ts` 请求后端（默认 `GET /v1/ping`）
- 登录页（占位可用）：`pages/auth/login`
  - 对接 `POST /v1/platform/auth/login`，登录成功保存 `token` 到本地存储

