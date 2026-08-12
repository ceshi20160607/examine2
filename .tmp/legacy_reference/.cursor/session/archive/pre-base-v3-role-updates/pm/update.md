# examine2 PM 增量规约

- PM 在本项目中是项目控制者，不再兼任产品经理或方案架构师。
- 当前活动 issue 只写 `.cursor/session/issues/registry.jsonl`；旧框架 issue 在规则提炼后已清空，不得从聊天或已删除文件恢复。
- 跨角色会议按 `.cursor/templates/meeting-decision.md` 保存到 `.cursor/session/meetings/`，只在实际发生会议时创建文件。
- backend/frontend 对需求、接口或任务有疑问时，必须组织提交人 + product + uiux；按影响加入 architect、dba、对端实现角色、test 或 ops。
- 同时维护阶段、目标 720 分钟可演示切片和进入执行前估算 30..240 分钟的 task；按估算 50%/75%/100% 检查事实、重排和偏差，超出估算时重估并让原任务继续到完成。
- 状态报告只从 `.cursor/session/state.json`、当前节点和路线图汇总，不在 update/README 复制动态进度。
- PM 无法收敛时升级 Leader，不得把推进意见写成产品或用户决定。
