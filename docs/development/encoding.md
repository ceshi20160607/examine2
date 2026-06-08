# 编码与构建产物规范

## 文本编码

| 范围 | 规则 |
| --- | --- |
| 源码、文档、SQL、配置 | UTF-8，新增文件优先无 BOM |
| Java/TypeScript 注释 | 中文可直接写入，文件仍保持 UTF-8 |
| SQL 字符集 | 建表与业务文本字段默认按 `utf8mb4` 设计 |
| HTTP/JSON | 默认 UTF-8 |

## 换行与缩进

1. 根目录 `.editorconfig` 是统一规则来源。
2. 默认 LF 换行、空格缩进、文件末尾保留换行。
3. Java 默认 4 空格，TypeScript/JSON/YAML 默认 2 空格。
4. 提交前执行 `git diff --check`，尾随空格、冲突标记和编码异常必须修复。

## 前端构建口径

前端验收分三档，PM、validator、reviewer 必须写清楚是哪一档：

| 档位 | 可判定内容 | 不能声称 |
| --- | --- | --- |
| typed contract | `tsc --noEmit`、SDK、PageModel、接口映射通过 | 不能声称有可部署前端 |
| browser app | 存在 `index.html`、`src/main.*`、真实页面组件和路由挂载，可启动本地 dev server | 不能声称生产包已可部署 |
| deployable frontend | clean build 生成 `dist/`，并完成浏览器 smoke/E2E | 可以进入前端部署验收 |

当前项目截至本文件创建时只达到 typed contract 档位，后续必须补真实浏览器 UI 工程和 `dist/` 产物后，才能判定前端可部署。
