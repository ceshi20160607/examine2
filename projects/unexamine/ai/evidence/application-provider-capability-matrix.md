# 外部平台官方能力矩阵（B12）

本矩阵只记录官方开放平台声明的产品边界，用于约束实现，不把本地协议测试冒充真实外部租户联通。

| 平台 | 本项目明确产品形态 | 身份与凭据 | 消息、入口与回调 | 当前实现边界 |
| --- | --- | --- | --- | --- |
| 微信 | `OFFICIAL_ACCOUNT` 公众号、`MINI_PROGRAM` 小程序；两者是不同产品形态 | 各自使用 AppID/Secret 和各自授权范围 | 公众号服务器接入与小程序消息推送分别配置；不定义“微信自建应用”这一虚构通用形态 | B12 后续必须由用户选定产品形态后分别实现 |
| 企业微信 | `INTERNAL_APP` 企业内部自建应用 | CorpID、AgentID、Secret；成员身份和可见范围受企业配置约束 | 回调配置使用 URL、Token、EncodingAESKey；GET 校验和 POST 消息均需验签、解密 | B12.1 实现结构化配置、本地诊断、回调验签解密、去重和应用授权路由；真实企业联通等待外部凭据 |
| 飞书 | `CUSTOM_APP` 企业自建应用 | App ID/App Secret，调用使用 tenant/user access token 并受应用权限与可用范围约束 | 事件订阅支持请求地址或长连接；请求地址模式支持 Verification Token、Encrypt Key、签名和 challenge 校验 | B12.3 已复用共用连接、成员映射、应用网关和消息契约，实现结构化配置、请求地址验签解密、challenge、事件幂等和确定的外部阻塞；真实企业联通等待外部凭据 |
| 钉钉 | `INTERNAL_APP` 企业内部应用 | Client ID（原 AppKey）、Client Secret、CorpID；身份免登和服务端权限分开 | HTTP 事件回调使用 Token、43 位 AES Key、SHA-1 签名、AES-CBC 加解密和加密成功响应；Stream 模式使用 Client ID/Client Secret 建立长连接 | B12.4 已复用共用连接、成员映射、应用网关和消息契约，实现结构化配置、HTTP 回调验签解密、所有者校验、事件幂等和确定的外部阻塞；真实租户需确认 HTTP 或 Stream 模式后验证 |

## 官方资料

- 企业微信：[接收消息与事件](https://developer.work.weixin.qq.com/document/path/90930)、[加解密方案](https://developer.work.weixin.qq.com/document/path/90968)
- 飞书：[配置事件订阅方式](https://open.feishu.cn/document/server-docs/event-subscription-guide/event-subscription-configure-/request-url-configuration-case)、[获取 tenant_access_token](https://open.feishu.cn/document/server-docs/authentication-management/access-token/tenant_access_token_internal)、[发送消息](https://open.feishu.cn/document/server-docs/im-v1/message/create)
- 钉钉：[事件订阅概述](https://open.dingtalk.com/document/orgapp/event-subscription-overview)、[官方 HTTP 回调加解密实现](https://github.com/open-dingtalk/DingTalk-Callback-Crypto)、[官方 Stream SDK](https://github.com/open-dingtalk/dingtalk-stream-sdk-java)
- 微信公众号：[接入概述](https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/Access_Overview.html)
- 微信小程序：[消息推送](https://developers.weixin.qq.com/miniprogram/dev/framework/server-ability/message-push.html)

## 外部阻塞

当前工作区没有四个平台的真实租户、应用凭据或公网回调地址控制权。可以完成并验证本地数据模型、密钥保护、官方算法向量、伪造/重放拒绝、事件去重和应用网关路由；真实授权、平台侧回调配置、实际收发消息只能记为 `blocked_external`，不能计为完成。
