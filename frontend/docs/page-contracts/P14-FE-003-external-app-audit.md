# P14-FE-003 对外应用与日志分层体验记录

状态：`done`

## 输入

- `docs/ui/p14-integrated-ui.md`
- `docs/issues/replies/development/p14_analyst_app_concept_reply.md`

## 修改范围

- `frontend/src/App.ts`
- `frontend/src/styles.css`

## 关键变化

1. 对外应用页面增加接入路径：创建对外应用、授权范围、安全策略、调用追踪。
2. 对外应用创建说明从技术客户端调整为外部系统接入主体，强调密钥只展示一次。
3. secret 展示区域增加保存提醒，说明离开页面后不能再明文查看。
4. 增加授权与安全说明，解释 scope、IP 白名单、限流和对外交付前确认范围。
5. 审计页增加平台层日志和业务系统层日志说明，区分平台账号/对外应用/运维诊断与系统内成员/建模/数据/流程/文件日志。

## 验证

前端构建命令：

```powershell
$env:Path='D:\Tools\node-v24.15.0-win-x64;'+$env:Path
npm.cmd run build
```

结果：pass。

## 后续任务

P14-FE-003 不声明完整 P14 验收完成。后续必须继续执行：

- `P14-TEST-001`：管理员到普通业务用户完整链路。
- `P14-TEST-002`：平台级对外应用创建、授权、调用和日志追踪链路。
- `P14-TEST-003`：平台日志、系统日志和错误恢复链路。
