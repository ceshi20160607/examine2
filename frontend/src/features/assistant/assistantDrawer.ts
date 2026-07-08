import {
  confirmSystemAgentWrite,
  confirmWorkAgentDraft,
  createSystemAgentSession,
  createSystemAgentWritePreview,
  loadSystemAgentAuditLogs,
  rejectSystemAgentWrite,
  sendSystemAgentMessage,
  type AgentMessageResultView,
  type AgentSessionView,
  type SystemWriteConfirmationView,
  type WorkDraftConfirmResultView,
} from '../../api/liveData';
import { canEnterSystemAdmin, shellState } from '../../app/state';
import { createButton, createElement, createTraceLine } from '../../shared/components';
import { renderStatusPill } from '../../shared/status';

let activeDrawer: HTMLElement | undefined;

interface AssistantState {
  systemId: string;
  session?: AgentSessionView;
  message?: AgentMessageResultView;
  writePreview?: SystemWriteConfirmationView;
  workDraft?: WorkDraftConfirmResultView;
  status?: string;
  error?: string;
  loading: boolean;
}

const DEFAULT_PROMPT = '请基于当前系统上下文生成处理建议、写入预览和今日日报草稿。';

export function createAssistantButton(systemId: string): HTMLButtonElement {
  const button = createButton('助手', 'ghost', false);
  button.title = '打开当前系统右侧智能助手';
  button.dataset.systemAssistantEntry = 'true';
  button.addEventListener('click', () => openAssistantDrawer(systemId));
  return button;
}

export function openAssistantDrawer(systemId: string): void {
  activeDrawer?.remove();
  const state: AssistantState = { systemId, loading: false };
  const root = createElement('aside', {
    className: 'assistant-drawer',
    ariaLabel: '右侧智能助手',
    dataset: {
      productSurface: 'system-assistant',
      systemAssistantDrawer: 'true',
      systemAssistantSystemId: systemId,
      systemAssistantBoundary: 'system-context-human-confirm',
    },
  });
  activeDrawer = root;
  document.body.append(root);
  render(root, state);
}

function render(root: HTMLElement, state: AssistantState): void {
  const promptInput = createElement('textarea', { ariaLabel: '助手需求' });
  promptInput.value = String(state.message?.audit?.conversationSnapshot?.message ?? DEFAULT_PROMPT);
  promptInput.placeholder = '描述你希望助手分析、起草或预览的内容';

  const closeButton = createButton('关闭', 'ghost', false);
  closeButton.dataset.systemAssistantClose = 'true';
  closeButton.addEventListener('click', () => {
    root.remove();
    activeDrawer = undefined;
  });

  const runButton = createButton(state.session ? '重新生成' : '生成建议', 'primary', state.loading);
  runButton.dataset.systemAssistantRun = 'true';
  runButton.addEventListener('click', () => void runAssistant(root, state, promptInput.value.trim()));

  const adminDraftButton = createButton(
    '后台变更草稿',
    'secondary',
    state.loading || !canEnterSystemAdmin(),
    canEnterSystemAdmin() ? undefined : '当前成员没有系统后台管理权限，不能生成后台配置变更草稿。',
  );
  adminDraftButton.dataset.systemAssistantAdminDraft = 'true';
  adminDraftButton.dataset.systemAssistantAdminDraftDisabled = String(adminDraftButton.disabled);
  adminDraftButton.dataset.systemAssistantAdminDraftReason = canEnterSystemAdmin()
    ? 'SYSTEM_ADMIN_ALLOWED'
    : 'NO_SYSTEM_ADMIN_PERMISSION';
  adminDraftButton.addEventListener('click', () => void runAssistant(
    root,
    state,
    `${promptInput.value.trim() || '生成后台配置变更草稿'}；需要后台配置变更预览。`,
    ['admin_policy_generation'],
  ));

  const children = [
    createElement(
      'header',
      { className: 'assistant-head' },
      createElement('div', {}, createElement('h2', {}, '智能助手'), createElement('small', {}, shellState.currentSystem?.systemName ?? `系统 ${state.systemId}`)),
      closeButton,
    ),
    createContextSummary(state),
    createElement('label', { className: 'assistant-prompt' }, createElement('span', {}, '需求'), promptInput),
    createElement('div', { className: 'assistant-actions' }, runButton, adminDraftButton),
    state.loading ? createElement('section', { className: 'assistant-panel', dataset: { systemAssistantLoading: 'true' } }, createElement('strong', {}, '正在调用系统 Agent...')) : null,
    state.error ? createElement('section', { className: 'assistant-panel danger', dataset: { systemAssistantError: 'true' } }, state.error) : null,
    state.status ? createElement('section', { className: 'assistant-panel', dataset: { systemAssistantStatus: 'true' } }, state.status) : null,
    state.message ? createMessagePanel(state.message) : null,
    state.writePreview ? createWritePreviewPanel(root, state) : null,
    state.workDraft ? createWorkDraftPanel(root, state) : null,
    state.session ? createAuditPanel(state.systemId, state.session, state.writePreview, state.workDraft) : null,
  ].filter((child): child is HTMLElement => child !== null);
  root.replaceChildren(...children);
}

async function runAssistant(root: HTMLElement, state: AssistantState, prompt: string, extraHints: string[] = []): Promise<void> {
  state.loading = true;
  state.error = undefined;
  state.status = undefined;
  render(root, state);
  try {
    const message = prompt || DEFAULT_PROMPT;
    const session = state.session ?? await createSystemAgentSession(state.systemId, message);
    const reply = await sendSystemAgentMessage(state.systemId, session.sessionId, message, [
      'business_write_preview',
      'work_daily_report_draft',
      ...extraHints,
    ]);
    const writePreview = await createSystemAgentWritePreview(state.systemId, {
      sessionId: session.sessionId,
      sourceConversation: message,
      permissionSnapshotId: session.permissionSnapshotId,
    });
    const workDraft = await confirmWorkAgentDraft(state.systemId, {
      sessionId: session.sessionId,
      sourceConversation: message,
      humanConfirmed: false,
    });
    state.session = session;
    state.message = reply;
    state.writePreview = writePreview;
    state.workDraft = workDraft;
    state.status = '已生成可审阅结果。写入和草稿仍需人工确认。';
  } catch (error) {
    state.error = error instanceof Error ? error.message : '助手调用失败。';
  } finally {
    state.loading = false;
    render(root, state);
  }
}

function createContextSummary(state: AssistantState): HTMLElement {
  const system = shellState.currentSystem;
  const roles = shellState.account.systemRoles.join(' / ') || 'SYSTEM_MEMBER';
  return createElement(
    'section',
    { className: 'assistant-context', dataset: { systemAssistantContext: 'true' } },
    createElement('div', {}, createElement('span', {}, '系统'), createElement('strong', {}, system?.systemName ?? state.systemId)),
    createElement('div', {}, createElement('span', {}, '租户'), createElement('strong', {}, system?.tenantId ?? '-')),
    createElement('div', {}, createElement('span', {}, '角色'), createElement('strong', {}, roles)),
    createElement('div', {}, createElement('span', {}, '权限快照'), createElement('strong', {}, state.session?.permissionSnapshotId ?? '生成后显示')),
  );
}

function createMessagePanel(message: AgentMessageResultView): HTMLElement {
  return createElement(
    'section',
    { className: 'assistant-panel', dataset: { systemAssistantMessage: message.operation.traceId, systemAssistantMessageResult: message.operation.result } },
    createElement('div', { className: 'assistant-panel-head' }, createElement('h3', {}, '分析结果'), renderStatusPill(message.operation.result, 'success')),
    createElement('p', {}, message.content),
    createElement(
      'div',
      { className: 'assistant-tool-list' },
      ...message.toolCalls.map((tool) => createElement('span', {}, `${tool.toolName}: ${tool.status}`)),
    ),
    createTraceLine(message.operation.traceId, message.operation.auditLogId),
  );
}

function createWritePreviewPanel(root: HTMLElement, state: AssistantState): HTMLElement {
  const preview = state.writePreview;
  if (!preview) {
    return createElement('section', { className: 'assistant-panel' }, '暂无写入预览');
  }
  const status = preview.confirmation.status;
  const confirmButton = createButton('确认写入', 'primary', state.loading || status !== 'WAITING_HUMAN_CONFIRM');
  const rejectButton = createButton('拒绝写入', 'secondary', state.loading || status !== 'WAITING_HUMAN_CONFIRM');
  confirmButton.dataset.systemAssistantConfirmWrite = preview.confirmation.confirmationId;
  rejectButton.dataset.systemAssistantRejectWrite = preview.confirmation.confirmationId;
  confirmButton.addEventListener('click', () => void handleWriteAction(root, state, true));
  rejectButton.addEventListener('click', () => void handleWriteAction(root, state, false));
  return createElement(
    'section',
    {
      className: 'assistant-panel',
      dataset: {
        systemAssistantWritePreview: preview.confirmation.confirmationId,
        systemAssistantConfirmationStatus: status,
        systemAssistantPermissionClipCount: String(preview.permissionClips.length),
      },
    },
    createElement('div', { className: 'assistant-panel-head' }, createElement('h3', {}, '写入预览'), renderStatusPill(status, status === 'CONFIRMED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning')),
    createElement(
      'div',
      { className: 'assistant-diff-list' },
      ...preview.fieldDiffs.map((diff) =>
        createElement(
          'div',
          { className: `assistant-diff${diff.writable ? '' : ' clipped'}` },
          createElement('strong', {}, diff.fieldName || diff.fieldCode),
          createElement('span', {}, `${String(diff.beforeValue ?? '')} -> ${String(diff.afterValue ?? '')}`),
          diff.writable ? renderStatusPill('可写', 'success') : renderStatusPill(diff.disabledReason ?? '权限裁剪', 'danger'),
        ),
      ),
    ),
    preview.permissionClips.length > 0 ? createElement('p', {}, `权限裁剪：${preview.permissionClips.map((clip) => `${clip.fieldCode} ${clip.reason ?? clip.clipType}`).join('；')}`) : null,
    createElement('small', {}, `补偿方案：${preview.compensationPlan ?? '-'}`),
    createElement('div', { className: 'assistant-actions' }, confirmButton, rejectButton),
    preview.confirmation.operation?.traceId ? createTraceLine(preview.confirmation.operation.traceId, preview.confirmation.operation.auditLogId) : null,
  );
}

async function handleWriteAction(root: HTMLElement, state: AssistantState, confirmed: boolean): Promise<void> {
  if (!state.writePreview) {
    return;
  }
  state.loading = true;
  state.error = undefined;
  render(root, state);
  try {
    state.writePreview = confirmed
      ? await confirmSystemAgentWrite(state.systemId, state.writePreview.confirmation.confirmationId, '右侧助手人工确认写入')
      : await rejectSystemAgentWrite(state.systemId, state.writePreview.confirmation.confirmationId, '右侧助手人工拒绝写入');
    state.status = confirmed ? '写入确认已记录，审计已落库。' : '写入已拒绝，审计已落库。';
  } catch (error) {
    state.error = error instanceof Error ? error.message : '写入处理失败。';
  } finally {
    state.loading = false;
    render(root, state);
  }
}

function createWorkDraftPanel(root: HTMLElement, state: AssistantState): HTMLElement {
  const draft = state.workDraft;
  if (!draft) {
    return createElement('section', { className: 'assistant-panel' }, '暂无草稿');
  }
  const status = draft.confirmation.status;
  const confirmButton = createButton('确认草稿', 'primary', state.loading || status === 'CONFIRMED');
  confirmButton.dataset.systemAssistantConfirmDraft = draft.confirmation.confirmationId;
  confirmButton.addEventListener('click', () => void handleWorkDraftConfirm(root, state));
  return createElement(
    'section',
    {
      className: 'assistant-panel',
      dataset: {
        systemAssistantWorkDraft: draft.confirmation.confirmationId,
        systemAssistantDraftStatus: status,
      },
    },
    createElement('div', { className: 'assistant-panel-head' }, createElement('h3', {}, '日报草稿'), renderStatusPill(status, status === 'CONFIRMED' ? 'success' : 'warning')),
    createElement('p', {}, String(draft.draftPayload.summary ?? draft.draftPayload.content ?? draft.draftPayload.title ?? '已生成草稿')),
    createElement(
      'div',
      { className: 'assistant-tool-list' },
      ...draft.sourceSnapshot.map((source) => createElement('span', {}, `${source.sourceType}: ${source.title}`)),
    ),
    createElement('div', { className: 'assistant-actions' }, confirmButton),
    draft.confirmation.operation?.traceId ? createTraceLine(draft.confirmation.operation.traceId, draft.confirmation.operation.auditLogId) : null,
  );
}

async function handleWorkDraftConfirm(root: HTMLElement, state: AssistantState): Promise<void> {
  if (!state.session || !state.workDraft) {
    return;
  }
  state.loading = true;
  state.error = undefined;
  render(root, state);
  try {
    state.workDraft = await confirmWorkAgentDraft(state.systemId, {
      sessionId: state.session.sessionId,
      sourceConversation: state.workDraft.confirmation.sourceConversation ?? '右侧助手确认日报草稿',
      humanConfirmed: true,
      draftType: state.workDraft.draftType,
      draftPayload: state.workDraft.draftPayload,
      sourceSnapshot: state.workDraft.sourceSnapshot,
    });
    state.status = '日报草稿已人工确认，审计已落库。';
  } catch (error) {
    state.error = error instanceof Error ? error.message : '草稿确认失败。';
  } finally {
    state.loading = false;
    render(root, state);
  }
}

function createAuditPanel(systemId: string, session: AgentSessionView, writePreview?: SystemWriteConfirmationView, workDraft?: WorkDraftConfirmResultView): HTMLElement {
  const panel = createElement(
    'section',
    { className: 'assistant-panel assistant-audit', dataset: { systemAssistantAudit: 'true', systemAssistantSession: session.sessionId } },
    createElement('div', { className: 'assistant-panel-head' }, createElement('h3', {}, '审计追踪'), renderStatusPill(session.status, 'info')),
    createElement('div', { className: 'kv-grid' },
      createElement('dt', {}, '会话'),
      createElement('dd', {}, session.sessionId),
      createElement('dt', {}, '策略'),
      createElement('dd', {}, session.policyVersion ?? '-'),
      createElement('dt', {}, '模型'),
      createElement('dd', {}, session.modelVersion ?? '-'),
      createElement('dt', {}, '边界'),
      createElement('dd', {}, session.boundary?.reason ?? '-'),
    ),
  );
  const auditButton = createButton('读取审计', 'secondary', false);
  auditButton.dataset.systemAssistantLoadAudit = 'true';
  auditButton.addEventListener('click', async () => {
    auditButton.disabled = true;
    try {
      const page = await loadSystemAgentAuditLogs(systemId, {
        sessionId: session.sessionId,
        pageNo: 1,
        pageSize: 6,
      });
      panel.dataset.systemAssistantAuditCount = String(page.records.length);
      panel.append(createElement('p', {}, `审计记录 ${page.records.length} 条；确认 ${writePreview?.confirmation.confirmationId ?? workDraft?.confirmation.confirmationId ?? '-'}`));
    } catch (error) {
      panel.append(createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '审计读取失败。'));
    } finally {
      auditButton.disabled = false;
    }
  });
  panel.append(createElement('div', { className: 'assistant-actions' }, auditButton));
  return panel;
}
