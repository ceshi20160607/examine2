# Template Base

工程框架是让需求理解、拆分、实现和验证有序衔接的开发方法与可复用工具。产品代码在业务工程中，产品需求在该工程的最终理解文件中；框架不保存产品业务、副本需求或开发历史。

## 工作方式

1. **理解全局**：读取用户只读原始输入、唯一最终需求理解及当前代码。先明确用户主流程、业务域、权限和数据关系；有歧义时指出具体问题，不从旧实现猜需求。开发方只修改最终理解文件，原始资料由用户维护。
2. **按业务结果拆分**：每批说明目标行为、所属业务模块、前置依赖、数据/接口、成功/无权/失败结果及验证方式，估算不超过 240 分钟。任务安排在当前工作上下文中，不生成任务、状态、交接或证据文件。任务完成后必须能回到整体主流程核对。
3. **按依赖实施**：有表变更时先完善当前 SQL、执行并核对数据库，再生成该业务模块的 base，最后在 manage 实现业务和薄 Controller。页面使用实际接口。前置依赖和共享数据库保持串行，独立代码可在获得授权且有收益时分工。
4. **按需使用 agents 与 skills**：agents 承担[职责](agents/README.md)，skills 提供具体执行方法。同一执行者可承担多项职责；不用固定的 agent 数量或调度层作为开发前提。各执行者使用同一最终需求理解，不能另存自己的产品解释。
5. **验证真实结果**：遵守[代码规则](rules/README.md)，运行相关编译和有意义的测试，再验证实际入口、数据持久化、权限及失败处理。测试应来自用户要求的行为，不能用自设算法的测试代替需求正确性。集成后核对整条业务过程。
6. **维护最终结果**：补充需求时直接更新最终理解对应正文及实现，不追加变更历史。回复说明当前结果和未完成范围；工具不存储完成标记，也不据此宣称业务验收。

## 工具入口

`workspace.json` 只配置最终资料、源码和本机工具链路径，不包含项目状态或历史。工具直接读取当前文件和源码：

```powershell
python template_base/tools/tb.py execution-context --workspace workspace.json
python template_base/tools/tb.py project-status --workspace workspace.json
python template_base/tools/tb.py execution-verify --workspace workspace.json --target generator
python template_base/tools/tb.py execution-verify --workspace workspace.json --target backend
```

- `execution-context` 展示原始资料边界、当前最终需求理解及工程流程，帮助执行者接续工作。
- `project-status` 检查当前源码中实际存在的基础文件和业务代码，不读取进度档案，不输出产品完成率。
- `execution-verify` 实际执行目标工程的 Maven 编译/测试并返回真实退出码，不接受文档证据或写入验收状态。业务入口验证仍由本批执行者完成。
- `mybatis-plus-generator/` 从真实业务表生成 Entity、Mapper、IService、ServiceImpl 及五个基础方法。生成归属使用业务工程的代码生成配置；具体业务只写在对应模块 manage，不手工修补生成代码替代模板修正。
