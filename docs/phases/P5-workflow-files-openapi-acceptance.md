# P5 流程文件导出 OpenAPI 期验收记录

- phaseId: P5-workflow-files-openapi
- 验收时间: 2026-06-07
- 验收角色: pm
- 结论: pass
- 下一期: P6-final-acceptance

## 验收范围

| 任务 | 状态 | 产物 |
| --- | --- | --- |
| BE-009 | done | 流程模板、流程实例、待办任务、审批动作和运行记录提交流程联动 |
| BE-010 | done | 文件上传、列表、详情、预览、下载、删除和引用计数规则 |
| BE-011 | done | 导出模板、导出任务、重试、取消、状态日志和结果文件生成 |
| BE-012 | done | OpenAPI 客户端管理、AK/SK 签名、nonce、scope、限流、幂等和外部接口转发 |
| BE-013 | done | 审计与运维只读 API、OpenAPI 日志桥接、健康、配置、版本和 migration 状态 |
| FE-009 | done | 流程工作台页面模型与契约证据 |
| FE-010 | done | 文件与导出页面模型与契约证据 |
| FE-011 | done | OpenAPI、审计、运维页面模型与契约证据 |
| FE-012 | done | 前端 API 契约映射汇总与前端自检报告 |

## 验收依据

1. 后端已分别执行并记录：
   - `mvn -pl examine-flow -am test`
   - `mvn -pl examine-upload -am test`
   - `mvn -pl examine-module -am test`
   - `mvn -pl examine-app -am test`
   - `mvn -pl examine-web -am test`
2. 前端页面模型均通过静态契约检查，新增页面目录未出现 `fetch`、`axios`、`XMLHttpRequest`、`new Request` 或硬编码 URL 旁路请求。
3. `frontend/docs/api-contract-map.md` 已汇总 174 个 SDK 端点、26 条路由映射和 FE-002 至 FE-011 页面证据；路由与页面证据中的真实 API ID 均存在于 `API_ENDPOINTS`。
4. `frontend/docs/frontend-self-check.md` 已记录前端正式 build/typecheck 的环境限制：当前 `frontend/` 缺少 `package.json`、`tsconfig.json`，本机无 `tsc` 命令。

## PM 结论

P5 的流程、文件、导出、OpenAPI、审计运维和前端契约闭环已达到本期退出标准，允许进入 P6 最终验收链路。

P6 启动后优先执行 BE-015 后端自检，再进入 TEST、VAL 和 REV 链路。前端 clean build 在 VAL-002 阶段需要继续记录工程入口缺口，除非先补充前端构建工程。
