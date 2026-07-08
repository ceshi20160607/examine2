const fs = require('fs');

const checklistPath = process.env.R98_CHECKLIST_PATH;
const baseUrl = process.env.R98_BASE_URL || 'http://127.0.0.1:18131';
const generatedAt = process.env.R98_GENERATED_AT || new Date().toISOString();
const trial = JSON.parse(process.env.R98_TRIAL_PACK || '{}');
const r97 = JSON.parse(process.env.R98_R97_RESULT || '{}');
const coverage = JSON.parse(process.env.R98_COVERAGE || '{}');
const browser = JSON.parse(process.env.R98_BROWSER_AUDIT || '{}');
const assets = JSON.parse(process.env.R98_ASSETS || '[]');

if (!checklistPath) {
  throw new Error('R98_CHECKLIST_PATH is required.');
}

const runtime = trial.runtimeDailyUse || {};
const workflow = trial.workflowTodoMessage || {};
const admin = trial.admin || {};
const assetLine = Array.isArray(assets) && assets.length ? assets.join(', ') : '未读取到前端资源清单';
const browserStatus = browser.status || 'NOT_RUN';
const browserCount = browser.resultCount || 0;
const coverageNotClosed = coverage.notClosedCount ?? 'unknown';

const lines = [
  '# R98 用户试用与签字清单',
  '',
  `生成时间：${generatedAt}`,
  `当前地址：${baseUrl}`,
  `前端资源：${assetLine}`,
  '',
  '## 先看边界',
  '',
  '- 这份清单是工程就绪证据，不是最终用户验收。',
  '- 当前 `.cursor/session/state.json` 仍保持 `gates.user_script_passed=false`。',
  `- 覆盖审计仍有未关闭行：\`${coverageNotClosed}\`。只有你明确说“试用通过/可以签字”后，后续任务才允许把用户签字 gate 改为 true。`,
  `- R98 浏览器烟测状态：\`${browserStatus}\`，路线数：\`${browserCount}\`。`,
  '',
  '## 试用账号',
  '',
  '| 角色 | 账号 | 密码 | 建议入口 |',
  '|---|---|---|---|',
  `| 平台管理员 | \`${admin.loginName || ''}\` | \`${admin.password || ''}\` | \`${baseUrl}/#/platform\` |`,
  `| 运行态普通成员 | \`${runtime.normalLoginName || ''}\` | \`${runtime.password || ''}\` | \`${baseUrl}/#/systems/${runtime.systemId || ''}/modules\` |`,
  `| 运行态只读成员 | \`${runtime.readonlyLoginName || ''}\` | \`${runtime.password || ''}\` | \`${baseUrl}/#/systems/${runtime.systemId || ''}/modules\` |`,
  `| 流程申请人 | \`${workflow.requesterLoginName || ''}\` | \`${workflow.password || ''}\` | \`${baseUrl}/#/systems/${workflow.systemId || ''}/modules\` |`,
  `| 流程审批人 | \`${workflow.approverLoginName || ''}\` | \`${workflow.password || ''}\` | \`${baseUrl}/#/systems/${workflow.systemId || ''}/todos\` 与 \`${baseUrl}/#/systems/${workflow.systemId || ''}/messages\` |`,
  '',
  '## 建议人工试用顺序',
  '',
  '1. 用平台管理员登录，打开 `/platform`，确认这是“系统入口工作台”，可以搜索并进入系统。',
  '2. 同一账号打开 `/platform/apps` 和 `/platform/flow`，确认 Application/Flow 是独立平台模块，不再混成系统入口。',
  `3. 用平台管理员打开 R97 新系统 \`${r97.systemId || ''}\` 的 \`/systems/${r97.systemId || ''}/dashboard\`，确认空系统只提示初始化；再打开 \`/systems/${r97.systemId || ''}/admin\`，确认 C1 十步初始化清单存在。`,
  `4. 用运行态普通成员打开系统 \`${runtime.systemId || ''}\` 的 \`/systems/${runtime.systemId || ''}/modules\`，确认能看到业务运行态页面和记录数据。`,
  '5. 用运行态只读成员打开同一路线，确认可看但不能新增/编辑越权数据。',
  `6. 用流程申请人打开系统 \`${workflow.systemId || ''}\` 的运行态模块，确认能看到流程终态记录 \`${workflow.terminalRecordId || ''}\` 相关页面。`,
  '7. 用流程审批人打开待办和消息中心，确认待办/消息路线可进入，历史流程处理结果能被理解。',
  '',
  '## 如何反馈',
  '',
  '- 如果所有步骤符合预期，请明确回复：`R98 用户试用通过，可以进入最终签字处理`。',
  '- 如果有问题，请给出账号、入口、页面现象和你期望看到的样子。下一批必须从这个问题建 recovery task card，再编码。',
  '',
  '## 机器证据',
  '',
  '- R97 结果：`docs/evidence/recovery/r97-c1-fresh-system-initialization-path-result.json`',
  '- R98 结果：`docs/evidence/recovery/r98-final-user-trial-readiness-and-signoff-path-result.json`',
  '- R98 浏览器烟测：`docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/final-user-trial-readiness-browser-audit.json`',
  '- R98 截图/日志目录：`docs/evidence/recovery/screenshots/r98-final-user-trial-readiness-and-signoff-path/`',
  '',
];

fs.writeFileSync(checklistPath, lines.join('\n'), 'utf8');
console.log(JSON.stringify({ status: 'PASS', checklistPath }));
