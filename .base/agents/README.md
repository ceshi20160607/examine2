# 角色总册

角色用于需要理解、权衡、讨论和修正的工作。技能用于输入输出确定、可重复或可脚本化的检查。角色不会因为目录存在而常驻，只在工程节点需要时由 Leader/PM 启动。

## 角色结构

```text
agents/{role}/role.md
agents/{role}/update.md  # 项目实例按需创建
```

每份 `role.md` 必须完整定义：我是谁、专业画像、我不是什么、必读输入、职责、工作模式、技能、输出规格、协作上报、会话隔离、完成标准和禁止清单。

## 角色关系

```text
用户/业务负责人
       ^
       | 仅用户专属决策
     Leader
       |
       v
      PM  <-> Product / Analyst
       |
       +---- Architect / UIUX / Planner / DBA
       +---- Backend / Frontend / Ops
       +---- Test（独立验收，不归实现者控制）
```

## 启动协议

每个角色必须收到 `templates/context` 等价信息，至少包含：

```yaml
role: "{{ROLE}}"
task_id: "{{TASK_ID}}"
phase: "{{PHASE}}"
node_id: "{{NODE_ID}}"
objective: "{{OBJECTIVE}}"
role_spec: "agents/{{ROLE}}/role.md"
role_update: "agents/{{ROLE}}/update.md | not-present"
inputs: []
outputs: []
allowed_writes: []
forbidden_reads: []
forbidden_writes: []
required_skills: []
acceptance: []
evidence_paths: []
```

角色开始时必须复述自己的身份、任务、输入、输出和 Gate。任务结束时只报告声明输出、证据、问题和建议下一责任人。

## 协作硬规则

- 不读取其他角色聊天历史，不接收口头摘要。
- 不修改未声明输出，不替相邻角色完成核心职责。
- 结论必须有来源路径；不确定项进入 issue。
- 实现者可以自检，不能自判验收通过。
- 多角色对同一主文件的意见通过 review/issue/meeting 汇总，由主责角色单写。
- `update.md` 只能补充项目增量，不能复制公共契约或扩大角色决策权。
