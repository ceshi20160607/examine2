# 身份、系统与公共资源 API 契约

本文记录当前后端已经实现的接口，供前端直接调用。基础地址为 `http://localhost:9999`。除文件上传外，请求体使用 JSON；登录后由服务端保存会话，浏览器请求需携带会话 Cookie。同一个浏览器会话可分别持有平台和系统登录态。

## 通用约定

- 本文件列出的分页列表均使用 MyBatis-Plus 分页响应：请求参数 `page` 从 1 开始，`size` 为 1～100，默认分别为 1 和 20；响应字段为 `records`、`total`、`size`、`current`、`pages`，没有额外业务包装层。可排序列表的 `sort` 只接受下文列出的值。项目其他业务域中有自定义列表响应，使用 `page` 而非 `current`；前端应按各域接口契约读取页码，不能把本文件的响应结构套用到所有业务接口。
- `status` 为 `0`（停用）或 `1`（启用）。部门、角色、菜单停用会保留历史关联。授权全量替换使用 `{ "ids": [1, 2] }`；空数组表示清空普通授权。数据范围使用数组，例如 `[{"scopeType":5,"departmentId":0}]`；`scopeType=6` 时 `departmentId` 必须是同范围的有效部门。
- `401` 表示未登录或会话失效，`403` 表示无管理权限或不属于目标租户，`400` 表示参数或状态不合法，`404` 表示记录不存在，`409` 表示编码重复或状态冲突。接口直接返回 HTTP 错误状态，不依赖响应体中的成功标志。
- 下文的“平台管理员”指有效平台账号关联的有效成员拥有启用的内置管理员角色；“租户管理员”指有效系统成员在目标租户中拥有启用的内置管理员角色。接口不信任客户端提交的成员或系统身份。

## 平台会话与组织

| 方法与路径 | 请求或查询 | 响应与权限 |
| --- | --- | --- |
| `POST /api/platform/session` | `{account,password}` | 登录成功返回 `{id,account,displayName}` 并建立会话；失败 `401`。 |
| `GET /api/platform/session` / `DELETE /api/platform/session` | 无 | 当前账号公开信息 / 退出；平台登录态。 |
| `GET /api/platform/accounts` | `page,size,sort,keyword`；`sort=account,displayName,createTime` | 分页账号公开信息 `{id,account,displayName,status}`，不返回密码摘要；平台管理员。 |
| `POST /api/platform/accounts` | `{account,password,displayName}`，密码至少 12 位 | 新账号公开信息；平台管理员。 |
| `PUT /api/platform/accounts/{id}` | `{name}` | 修改显示名；平台管理员。 |
| `PUT /api/platform/accounts/{id}/status` | `{status}` | 停启账号；不能停用当前账号；平台管理员。 |
| `PUT /api/platform/accounts/{id}/password` | `{password}`，至少 12 位 | 重置密码，不回传摘要；平台管理员。 |
| `GET /api/platform/departments` | 无 | 按 `parentId,sortNo,id` 排列的部门数组；平台登录态。 |
| `POST /api/platform/departments` / `PUT /api/platform/departments/{id}` | `{parentId,departmentCode,name,managerMemberId,sortNo,remark}` | 新增/编辑部门，`parentId=0` 或空表示根部门；平台管理员。 |
| `PUT /api/platform/departments/{id}/status` | `{status}` | 停启部门；平台管理员。 |
| `GET /api/platform/members` | `page,size,sort,departmentId,keyword`；`sort=memberNo,realName,createTime` | 分页成员；平台管理员。 |
| `POST /api/platform/members` / `PUT /api/platform/members/{id}` | `{accountId,memberNo,realName,departmentId,leaderMemberId,mobile,email,gender,positionName}` | 新增/编辑成员。`accountId` 可空；一个账号只能关联一个成员；平台管理员。 |
| `PUT /api/platform/members/{id}/status` | `{status}` | 停启成员，不能停用当前管理员成员；平台管理员。 |
| `GET /api/platform/config` | 无 | 平台配置数组；平台管理员。 |
| `PUT /api/platform/config/{key}` | `{value,description}` | 按键新增或更新平台配置；平台管理员。 |

## 平台角色、菜单与授权

| 方法与路径 | 请求或查询 | 响应与权限 |
| --- | --- | --- |
| `GET /api/platform/roles` | `page,size,sort`；`sort=roleCode,roleName,createTime` | 分页角色；平台管理员。 |
| `POST /api/platform/roles` / `PUT /api/platform/roles/{id}` | `{roleCode,roleName,remark}` | 新增自定义角色/编辑角色；内置管理员角色编码不可改；平台管理员。 |
| `PUT /api/platform/roles/{id}/status` | `{status}` | 停启角色；内置管理员角色不可停用。 |
| `GET /api/platform/menus` | 无 | 按父级和排序号排列的菜单数组；平台登录态。 |
| `POST /api/platform/menus` / `PUT /api/platform/menus/{id}` | `{parentId,menuCode,menuName,menuType,routePath,permissionCode,sortNo}`；`menuType` 为 1 目录、2 页面、3 操作 | 新增/编辑菜单；平台管理员。 |
| `PUT /api/platform/menus/{id}/status` | `{status}` | 停启菜单；平台管理员。 |
| `GET /api/platform/members/{memberId}/roles` / `PUT /api/platform/members/{memberId}/roles` | 写入 `{ids:[...]}` | 读取/全量替换成员角色主键数组；当前管理员不能移除自己的管理角色。 |
| `GET /api/platform/roles/{roleId}/menus` / `PUT /api/platform/roles/{roleId}/menus` | 写入 `{ids:[...]}` | 读取/全量替换角色菜单主键数组；平台管理员。 |
| `GET /api/platform/roles/{roleId}/data-scope` / `PUT /api/platform/roles/{roleId}/data-scope` | 写入数据范围数组 | 读取/全量替换角色数据范围；平台管理员。 |

## 平台创建系统、租户与首位管理员

以下路径由平台管理员调用，适合系统开通页面。先创建系统，再创建租户和系统成员，最后把系统成员加入租户；首位加入租户的成员自动获得 `tenant_admin` 角色。

| 方法与路径 | 请求或查询 | 响应 |
| --- | --- | --- |
| `GET /api/platform/systems` | `page,size,sort`；`sort=systemCode,systemName,createTime` | 分页业务系统。 |
| `POST /api/platform/systems` / `PUT /api/platform/systems/{id}` | `{systemCode,systemName,description,ownerPlatMemberId}` | 新增/编辑业务系统；负责人可空，非空时必须是平台成员。 |
| `PUT /api/platform/systems/{id}/status` | `{status}` | 停启系统，停用后系统登录失效。 |
| `GET /api/platform/systems/{systemId}/config` / `PUT /api/platform/systems/{systemId}/config/{key}` | 写入 `{value,description}` | 读取/按键保存系统基础配置。 |
| `GET /api/platform/systems/{systemId}/tenants` | `page,size,sort`；`sort=tenantCode,tenantName,createTime` | 分页租户。 |
| `POST /api/platform/systems/{systemId}/tenants` | `{tenantCode,tenantName,contactName,contactMobile}` | 创建租户及内置管理员角色。 |
| `PUT /api/platform/tenants/{id}` / `PUT /api/platform/tenants/{id}/status` | 编辑同创建请求；状态请求为 `{status}` | 编辑/停启租户。 |
| `GET /api/platform/systems/{systemId}/members` | `page,size,sort`；`sort=account,displayName,createTime` | 分页系统成员公开信息，不返回密码摘要。 |
| `POST /api/platform/systems/{systemId}/members` | `{account,password,displayName,realName,mobile,email}`；密码至少 12 位 | 创建可登录的系统成员。 |
| `PUT /api/platform/system-members/{id}` | `{displayName,realName,mobile,email}` | 编辑系统成员公开资料。 |
| `PUT /api/platform/system-members/{id}/status` / `PUT /api/platform/system-members/{id}/password` | `{status}` / `{password}` | 停启系统成员/重置密码。 |
| `GET /api/platform/tenants/{tenantId}/members` | `page,size,sort`；`sort=memberNo,joinTime,createTime` | 分页租户成员。 |
| `POST /api/platform/tenants/{tenantId}/members` | `{memberId,memberNo,positionName}`，`memberId` 为系统成员主键 | 将同系统成员加入租户；首位成员自动授权租户管理员。 |

## 系统会话与租户内组织

| 方法与路径 | 请求或查询 | 响应与权限 |
| --- | --- | --- |
| `POST /api/system/session` | `{systemCode,account,password}` | 返回 `{systemId,systemCode,memberId,account,displayName}` 并建立系统会话；失败 `401`。 |
| `GET /api/system/session` / `DELETE /api/system/session` | 无 | 当前系统成员公开信息 / 退出。 |
| `GET /api/system/session/tenants` | 无 | 当前成员可进入的租户 `{id,code,name,tenantMemberId}` 数组。 |
| `GET /api/system/tenants/{tenantId}/departments` | 无 | 该租户部门树数组；有效租户成员。 |
| `POST /api/system/tenants/{tenantId}/departments` / `PUT /api/system/tenants/{tenantId}/departments/{id}` | `{parentId,departmentCode,name,managerTenantMemberId,sortNo,remark}` | 新增/编辑租户部门；租户管理员。 |
| `PUT /api/system/tenants/{tenantId}/departments/{id}/status` | `{status}` | 停启部门；租户管理员。 |
| `GET /api/system/tenants/{tenantId}/members` | `page,size,sort,departmentId`；`sort=memberNo,joinTime,createTime` | 分页租户成员；有效租户成员。 |
| `POST /api/system/tenants/{tenantId}/members` | `{memberId,departmentId,memberNo,positionName,leaderTenantMemberId}`，`memberId` 为同系统成员主键 | 加入现有系统成员；租户管理员。 |
| `PUT /api/system/tenants/{tenantId}/members/{id}` | `{departmentId,memberNo,positionName,leaderTenantMemberId}` | 编辑成员在该租户中的资料；租户管理员。 |
| `PUT /api/system/tenants/{tenantId}/members/{id}/status` | `{status}` | 停启租户成员；当前管理员不能停用自身。 |

## 租户角色、菜单与授权

以下路径均以 `/api/system/tenants/{tenantId}` 开头。列表读取需要有效租户成员；写操作、授权读取需要租户管理员。

| 方法与后缀 | 请求或查询 | 响应 |
| --- | --- | --- |
| `GET /roles` | `page,size,sort`；`sort=roleCode,roleName,createTime` | 分页租户角色。 |
| `POST /roles` / `PUT /roles/{id}` | `{roleCode,roleName,remark}` | 新增自定义角色/编辑角色；内置管理员编码不可改。 |
| `PUT /roles/{id}/status` | `{status}` | 停启角色；内置管理员角色不可停用。 |
| `GET /menus` | 无 | 租户菜单数组。 |
| `POST /menus` / `PUT /menus/{id}` | `{parentId,menuCode,menuName,menuType,routePath,permissionCode,sortNo}` | 新增/编辑菜单；`menuType` 为 1 目录、2 页面、3 操作。 |
| `PUT /menus/{id}/status` | `{status}` | 停启菜单。 |
| `GET /members/{memberId}/roles` / `PUT /members/{memberId}/roles` | 写入 `{ids:[...]}` | 读取/全量替换租户成员角色；当前管理员不能移除自身管理角色。 |
| `GET /roles/{roleId}/menus` / `PUT /roles/{roleId}/menus` | 写入 `{ids:[...]}` | 读取/全量替换角色菜单授权。 |
| `GET /roles/{roleId}/data-scope` / `PUT /roles/{roleId}/data-scope` | 写入数据范围数组 | 读取/全量替换角色数据范围；指定部门必须属于当前租户。 |

## 公共资源范围

公共资源使用 `scopeType=platform|system|tenant` 和 `scopeId`。平台范围固定 `scopeId=0`；系统和租户范围使用其真实主键。资源读取需对应范围身份，平台管理员可以读取系统和租户资源。文件上传允许对应范围的有效成员；删除需要管理员。公告和 AI 配置写入：平台及系统范围由平台管理员负责，租户范围由租户管理员或平台管理员负责。

| 方法与路径 | 请求或查询 | 响应与说明 |
| --- | --- | --- |
| `GET /api/common/files` | `scopeType,scopeId,page,size,sort`；`sort=originalName,fileSize,createTime` | 分页文件元数据，不返回存储路径。 |
| `POST /api/common/files` | `multipart/form-data`：`scopeType,scopeId,file` | 上传实际文件并保存元数据；单文件上限 25 MB，响应含 `id,originalName,contentType,fileSize,createTime`。 |
| `GET /api/common/files/{id}/download` | 无 | 附件二进制；下载前重新校验文件所属范围。 |
| `DELETE /api/common/files/{id}` | 无 | 删除文件和元数据；范围管理员。 |
| `GET /api/common/notices` | `scopeType,scopeId,page,size,sort,includeDrafts`；`sort=title,createTime,publishTime` | 默认只返回当前有效发布公告；`includeDrafts=true` 需要管理员。 |
| `GET /api/common/notices/{id}` | 无 | 已发布且未过期公告对范围成员可见；草稿、撤销、过期公告仅管理员可见。 |
| `POST /api/common/notices` | `{scopeType,scopeId,title,content,expireTime}` | 新建草稿，`status=0`。 |
| `PUT /api/common/notices/{id}` | `{title,content,expireTime}` | 仅可编辑草稿。 |
| `POST /api/common/notices/{id}/publish` / `POST /api/common/notices/{id}/revoke` | 无 | 草稿→发布 `status=1` / 发布→撤销 `status=2`。 |
| `GET /api/common/ai-configs` | `scopeType,scopeId,page,size,sort`；`sort=configCode,providerName,createTime` | 管理员分页读取，只有 `credentialConfigured` 标志，不返回凭据或密文。 |
| `POST /api/common/ai-configs` | `{scopeType,scopeId,configCode,providerName,modelName,apiBaseUrl,credential}` | 新建配置；有凭据时以 AES-GCM 加密存储。 |
| `PUT /api/common/ai-configs/{id}` | `{configCode,providerName,modelName,apiBaseUrl,credential}` | 修改配置；省略 `credential` 则保留原凭据。 |
| `PUT /api/common/ai-configs/{id}/status` | `{status}` | 停启配置。 |

写入 AI 凭据前必须设置 32 字节密钥的 Base64 环境变量 `APP_AI_ENCRYPTION_KEY`；缺失或格式错误时返回 `503`，不会保存明文。当前没有 AI 服务连通性测试接口。

## 日志与消息

| 方法与路径 | 查询或请求 | 响应与权限 |
| --- | --- | --- |
| `GET /api/common/logs/platform-login` | `page,size,sort,account,success`；`sort=loginTime,loginAccount` | 平台登录日志；平台管理员。 |
| `GET /api/common/logs/platform-operation` | `page,size,sort,accountId,actionCode`；`sort=createTime,actionCode` | 平台操作日志；平台管理员。 |
| `GET /api/common/logs/system-login` | 必填 `systemId`，可选 `page,size,sort,account`；`sort=loginTime,loginAccount` | 系统登录日志；平台管理员。 |
| `GET /api/common/logs/system-operation` | 必填 `systemId`，可选 `tenantId,page,size,sort`；`sort=createTime,actionCode` | 系统操作日志；平台管理员。 |
| `GET /api/common/messages/platform` | `page,size,sort,unread`；`sort=createTime,title` | 当前平台成员的消息，`unread=true/false` 筛选已读状态。 |
| `POST /api/common/messages/platform` | `{recipientMemberId,title,content,messageType,relatedType,relatedId}` | 平台管理员发送给有效且有启用账号的平台成员；返回 `201` 和消息记录。关联类型、主键可同时省略。 |
| `PUT /api/common/messages/platform/{id}/read` | 无 | 当前平台接收成员标记已读，返回含 `readTime` 的消息。 |
| `GET /api/common/messages/system` | 可选 `tenantId`，以及 `page,size,sort,unread`；`sort=createTime,title` | 当前系统成员消息；省略 `tenantId` 只看系统级消息，指定时必须属于该租户。 |
| `POST /api/common/messages/system` | `{tenantId,recipientTenantMemberId,title,content,messageType,relatedType,relatedId}` | 租户管理员发送给同租户有效成员；服务端从租户成员关系确定系统成员，返回 `201`。 |
| `PUT /api/common/messages/system/{id}/read` | 无 | 当前接收成员标记已读；租户消息再次检查租户身份。 |

消息发送只接受接收成员、内容和可选的关联对象，不接受客户端指定已读时间或系统消息的系统主键。`relatedType` 和 `relatedId` 要么同时提供，要么同时省略。

## 当前实现边界

上述为本批已存在的接口，不代表 94 张表的其余业务均已完成。角色菜单和数据范围可配置，但普通角色的细粒度权限执行仍需在各业务接口接入；当前公共 AI 配置未提供服务连通性检查。前端开始实现相关页面前，应按后端实际返回的 JSON 和错误状态联调。
