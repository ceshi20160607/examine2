# T050 · 运行态 module-rail 壳

| 项 | 值 |
|----|-----|
| taskId | T050 |
| 阶段 | P4–P5 |
| 责任 | frontend |
| 依赖 | T010, T040 |
| 并行 | 与 T060 路径不重叠 |
| 状态 | pending |

## 目标

前端运行态对齐 `runtime-list.html` + ui-spec §2.5 九点。

## 输入

- `docs/design/ui-spec.md` §2.4–§2.5
- `docs/design/prototypes/system/runtime-list.html`
- `docs/design/config-spec.md` §1.1
- `frontend/src/pages/runtime/`

## 输出

- 顶栏：仪表盘 + 模块组 Tab
- 左栏：组内模块（module-rail）
- 右区：列表 | 详情分栏
- 工具栏：搜索+场景+导出
- 详情：摘要+编辑删除+Tab+审批侧栏

## 完成标准

- [ ] 与原型结构对照 9/9
- [ ] che 剧本 B4–B8 可走
- [ ] 无 localhost 写死 API

## 不做

- Vue 全量重写（首期改现有 TS DOM）
