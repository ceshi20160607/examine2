# 移动端测试（预留）

当前主流程以 **API 冒烟**（`tests/api`）+ **Web Playwright**（`tests/web`）为主。

后续可选：

1. **uniapp 编译 H5** → 用 Playwright 访问 H5 地址（与 `tests/web` 共用工具链）
2. **Appium** → 真机/模拟器（成本高）

发版前仍建议按 `doc/docs/mobile-api-coverage.md` 手工走主流程。
