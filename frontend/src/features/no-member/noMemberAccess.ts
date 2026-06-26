import {
  createNoMemberAccessRequest,
  loadNoMemberAccessRequests,
  type NoMemberAccessRequestView,
} from '../../api/liveData';
import type { PageResult } from '../../api/types';
import type { Navigate } from '../../app/app';
import { shellState } from '../../app/state';
import { createButton, createElement, createTraceLine } from '../../shared/components';
import { renderStatusPill } from '../../shared/status';

export function renderNoMemberAccessPage(route: string, navigate: Navigate): HTMLElement {
  const params = new URLSearchParams(route.split('?')[1] ?? '');
  const targetSystemId = params.get('systemId') ?? shellState.availableSystems.find((system) => !system.enabled)?.systemId ?? '';
  const targetTenantId = params.get('tenantId') ?? shellState.availableSystems.find((system) => system.systemId === targetSystemId)?.tenantId;
  const root = createElement(
    'main',
    { className: 'page-grid' },
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '申请系统成员映射'),
      createElement('p', {}, '账号已登录，但还没有目标系统的成员、角色和数据范围。提交申请后由系统管理员审核。'),
    ),
    createLoadingPanel('正在读取申请记录...'),
  );

  void loadRequests(targetSystemId)
    .then((page) => root.replaceChildren(...createNoMemberContent(targetSystemId, targetTenantId, page, navigate)))
    .catch((error) => root.replaceChildren(...createNoMemberContent(targetSystemId, targetTenantId, undefined, navigate, error)));

  return root;
}

function createNoMemberContent(
  targetSystemId: string,
  targetTenantId: string | undefined,
  page: PageResult<NoMemberAccessRequestView> | undefined,
  navigate: Navigate,
  error?: unknown,
): HTMLElement[] {
  const targetSystem = shellState.availableSystems.find((system) => system.systemId === targetSystemId);
  const result = createElement('p', { className: 'field-error' }, error instanceof Error ? error.message : '申请会写入 NoMemberAccessRequest，审批通过前不会进入系统业务页。');
  const backButton = createButton('返回平台工作台', 'secondary', false);
  const submitButton = createButton('提交映射申请', 'primary', !targetSystemId, targetSystemId ? undefined : '没有可申请的目标系统。');
  backButton.addEventListener('click', () => navigate('/platform'));
  submitButton.addEventListener('click', async () => {
    const reason = window.prompt('请填写申请原因', '需要进入该系统处理我的业务数据')?.trim();
    if (!reason) {
      return;
    }
    submitButton.disabled = true;
    submitButton.textContent = '提交中...';
    try {
      const request = await createNoMemberAccessRequest(targetSystemId, {
        tenantId: targetTenantId,
        requestRole: 'SYSTEM_MEMBER',
        requestReason: reason,
      });
      result.textContent = `申请已提交：${request.status}，traceId=${request.traceId}`;
      submitButton.textContent = '已提交';
    } catch (submitError) {
      submitButton.disabled = false;
      submitButton.textContent = '重新提交';
      result.textContent = submitError instanceof Error ? submitError.message : '提交申请失败。';
    }
  });

  return [
    createElement(
      'div',
      { className: 'page-heading' },
      createElement('h1', {}, '申请系统成员映射'),
      createElement('p', {}, '账号已登录，但还没有目标系统的成员、角色和数据范围。提交申请后由系统管理员审核。'),
    ),
    createElement(
      'section',
      { className: 'panel' },
      createElement('h2', {}, '目标系统'),
      createElement(
        'div',
        { className: 'simple-stack' },
        createElement('div', { className: 'list-line' }, createElement('span', {}, '账号'), createElement('strong', {}, shellState.account.displayName)),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '系统'), createElement('strong', {}, targetSystem?.systemName ?? (targetSystemId || '-'))),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '租户'), createElement('span', {}, targetSystem?.tenantName ?? targetTenantId ?? '-')),
        createElement('div', { className: 'list-line' }, createElement('span', {}, '不能进入原因'), createElement('span', {}, targetSystem?.disabledReason ?? 'NO_SYSTEM_MEMBER_MAPPING')),
      ),
      result,
      createElement('div', { className: 'inline-actions' }, submitButton, backButton),
    ),
    createRequestHistoryPanel(page),
  ];
}

async function loadRequests(systemId: string): Promise<PageResult<NoMemberAccessRequestView> | undefined> {
  if (!systemId) {
    return undefined;
  }
  return loadNoMemberAccessRequests(systemId);
}

function createRequestHistoryPanel(page: PageResult<NoMemberAccessRequestView> | undefined): HTMLElement {
  if (!page) {
    return createElement('section', { className: 'panel' }, createElement('h2', {}, '申请记录'), createElement('p', {}, '当前没有可读取的目标系统。'));
  }
  return createElement(
    'section',
    { className: 'panel' },
    createElement('h2', {}, '申请记录'),
    page.records.length === 0
      ? createElement('p', {}, '暂无申请记录。')
      : createElement(
          'div',
          { className: 'simple-stack' },
          ...page.records.map((request) =>
            createElement(
              'article',
              { className: 'runtime-card' },
              createElement('div', { className: 'list-line' }, createElement('strong', {}, request.requestRole ?? 'SYSTEM_MEMBER'), renderStatusPill(request.status, request.status === 'APPROVED' ? 'success' : 'warning')),
              request.rejectReason ? createElement('p', {}, request.rejectReason) : null,
              request.disabledReason ? createElement('p', {}, request.disabledReason) : null,
              createTraceLine(request.traceId),
            ),
          ),
        ),
  );
}

function createLoadingPanel(text: string): HTMLElement {
  return createElement('section', { className: 'panel' }, createElement('strong', {}, text));
}
