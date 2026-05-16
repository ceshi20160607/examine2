# 手机端 API 覆盖映射（a4）

> 用于核对 `mobile/uniapp` 页面与 `examine-web` 系统态 API 的对应关系。  
> 发版主流程应覆盖「已覆盖」项；「未覆盖」为 v1 有意省略或仅后端/OpenAPI 使用。

## 图例

| 状态 | 含义 |
|------|------|
| ✅ | 手机端有页面且已接 API |
| ⚠️ | 后端有 API，手机端未做或仅部分 |
| — | 非手机端范围（OpenAPI / 平台运维） |

## 平台态

| 后端路径 | 手机端 | 页面 / API 模块 |
|----------|--------|-----------------|
| `POST /v1/platform/auth/login` | ✅ | `pages/auth/login` · `api/platformAuth` |
| `GET /v1/platform/auth/me` | ✅ | `pages/tabs/me` |
| `POST /v1/platform/auth/refresh` | ✅ | `http.ts` 自动刷新 · Me 页 |
| `POST /v1/platform/auth/logout` | ✅ | Me 页 |
| `GET /v1/platform/systems` | ✅ | `pages/platform/systems` · `api/platform` |
| `POST /v1/platform/systems` | ✅ | 系统列表创建 |
| `POST /v1/platform/context/enter-system` | ✅ | 进入系统 |
| `GET /ping` | ✅ | `pages/boot/health` · `api/misc` |
| `GET/POST /v1/platform/apps` | — | 对外应用 accessKey（运维/OpenAPI） |
| `GET /v1/platform/inbox/*` | — | 平台级收件箱（系统内用 flow inbox） |

## 低代码元数据（module/meta）

| 后端路径 | 手机端 | 页面 |
|----------|--------|------|
| Apps/Models/Fields CRUD | ✅ | `pages/system/module/meta/*` · `api/meta` |
| Dicts / Dict items | ✅ | `pages/system/module/dict/*` · `api/module` |
| List views / cols / filter tpls | ✅ | `pages/system/module/listviews/*` |
| Export tpls / fields / jobs | ✅ | `pages/system/module/export/*` |
| RBAC roles/menus/members/perms | ✅ | `pages/system/module/rbac/*` |
| `GET /v1/system/auth/perm-preview` | ✅ | RBAC 权限验证 |

## 业务数据（records）

| 后端路径 | 手机端 | 页面 |
|----------|--------|------|
| `POST /v1/system/records` | ✅ | `records/form` 创建 |
| `GET /v1/system/records/{id}` | ✅ | `records/detail`、`records/list` 摘要 |
| `POST /v1/system/records/{id}/update` | ✅ | `records/form` 编辑 |
| `DELETE /v1/system/records/{id}` | ✅ | `records/detail` |
| `POST /v1/system/records/query` | ✅ | `records/list` |
| Record 变更历史 | ⚠️ | 后端 EAV 写历史；无系统态查询 API / 无手机页 |

## 流程（flow）

| 后端路径 | 手机端 | 页面 |
|----------|--------|------|
| 模板 / 版本 / 节点 / 线 / 条件 / 设置 | ✅ | `pages/system/flow/temp_*` · `api/flow` |
| `POST .../instances/start` | ✅ | `flow/start` |
| Inbox pending/cc | ✅ | `flow/inbox`、Tab 待办预览 |
| 实例分页 / 详情 / tasks / actions / traces | ✅ | `flow/instances`、`flow/instance` |
| 我的实例 | ✅ | `flow/my_instances` |
| Task act | ✅ | `flow/task` |
| by-biz 查询 | ✅ | `flow/by_biz` |

## 上传（upload）

| 后端路径 | 手机端 | 页面 |
|----------|--------|------|
| `POST /v1/system/uploads` | ✅ | `upload/index`（跨端选文件） |
| `GET .../page` | ✅ | 列表 |
| view / download / delete | ✅ | 预览页 + 列表操作 |

## OpenAPI（有意不做手机端）

| 后端路径 | 状态 |
|----------|------|
| `/v1/open/records/*` | — |
| `/v1/open/flow/*` | — |

## 发版前主流程（与 README 一致）

1. Health → Login → 系统列表 → 进入系统 → 工作台  
2. Apps → Models → Fields → Records 增删改查  
3. Dict（下拉）→ Records 表单字典字段  
4. Flow：模板 → 发布 → 发起 → 待办处理  
5. Upload：上传 / 列表 / 预览  
6. Export：模板 → 任务 → 详情下载  
7. RBAC：角色 / 菜单 / 成员 / URI 权限预览  

## v1 已知缺口（可后续版本）

- 记录变更历史查看（需系统态 history API + 手机页）  
- 平台对外应用 ` /v1/platform/apps` 管理  
- 图形化流程设计器（当前以列表编辑 + graphJson 为主）  
- 更完整字段类型与表单校验  
