# Unexamine 新实现

这里是当前唯一的新产品实现目录。旧的 `backend/`、`frontend/`、`sql/`、`.base/` 和 `.cursor/` 只作为参考，不再承接新功能。

开发顺序由可执行门禁约束：

1. `docs/user_requirement.md` 是唯一正式产品需求源。
2. `requirements/requirement-intake.json` 固定需求区块、行号和哈希。
3. `architecture-baseline.json` 先确定固定内核与运行时配置边界。
4. 只有架构基线、需求分析和验收用例都通过校验，才生成基础工程或编写业务逻辑。
5. 第一条纵向切片必须从浏览器经过真实 API 落到真实数据库，并验证租户、权限和审计读回。

当前阶段只建立新实现边界与架构基线，尚未把任何旧实现声明为可复用 Base。
