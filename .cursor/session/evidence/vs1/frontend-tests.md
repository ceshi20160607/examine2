# VS1 前端构建与单测证据

- checked_at: `2026-07-10T22:46:04+08:00`
- verdict: `pass_with_observation`

| 命令 | 结果 |
|---|---|
| `npm.cmd test` | 1 file，3 tests passed |
| `npm.cmd run build` | TypeScript/Vue typecheck 和 Vite production build passed |
| `npm.cmd run e2e` | desktop/mobile 2 tests passed |

生产构建主 chunk 为 `508.90 kB` raw / `166.12 kB` gzip，Vite 给出超过 500 kB 的性能告警。VS1 首屏和旅程可用，告警不阻断本节点；后续功能扩展前必须继续按路由/组件拆包，不能让主包随业务范围无界增长。

前端使用真实 API、cookie/CSRF、route guard 和 Pinia session；没有 mock server 或占位业务入口。
