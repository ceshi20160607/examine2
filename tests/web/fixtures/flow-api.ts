import type { APIRequestContext } from '@playwright/test';
import type { Page } from '@playwright/test';

const apiBase = () => (process.env.EXAMINE_HOST || 'http://127.0.0.1:9999').replace(/\/$/, '');

function patchLongIds(text: string): string {
  return text.replace(/"([A-Za-z]*[iI]d|[iI]d)":\s*(\d{15,})/g, '"$1":"$2"');
}

async function apiCall<T = Record<string, unknown>>(
  request: APIRequestContext,
  token: string,
  method: 'GET' | 'POST',
  path: string,
  body?: unknown
): Promise<T> {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const res =
    method === 'GET'
      ? await request.get(`${apiBase()}${path}`, { headers })
      : await request.post(`${apiBase()}${path}`, { headers, data: body });
  const json = JSON.parse(patchLongIds(await res.text()));
  if (!json || json.code !== 0) {
    throw new Error(`${json?.message || 'API error'} [${method} ${path}]`);
  }
  return json.data as T;
}

export async function tokenFromPage(page: Page): Promise<string> {
  const token = await page.evaluate(() => window.localStorage.getItem('examine_token') || '');
  if (!token) throw new Error('examine_token missing in localStorage');
  return token;
}

/** 用当前页 token 在服务端进入第一个系统（与 UI enterFirstSystem 一致） */
export async function ensureSystemContext(request: APIRequestContext, token: string): Promise<void> {
  const systems = await apiCall<Array<{ id: string }>>(request, token, 'GET', '/v1/platform/systems');
  if (!systems?.length) throw new Error('no systems');
  await apiCall(request, token, 'POST', '/v1/platform/context/enter-system', { systemId: systems[0].id });
}

const GRAPH_BODY = {
  nodes: [
    { nodeKey: 'start_1', nodeType: 'start', nodeName: 'Start', x: 120, y: 120, configJson: '{}' },
    { nodeKey: 'approve_1', nodeType: 'approve', nodeName: 'Approve', x: 320, y: 120, configJson: '{}' },
    { nodeKey: 'end_1', nodeType: 'end', nodeName: 'End', x: 520, y: 120, configJson: '{}' },
  ],
  edges: [
    { fromNodeKey: 'start_1', toNodeKey: 'approve_1', priority: 1, isDefault: 0, cond: '' },
    { fromNodeKey: 'approve_1', toNodeKey: 'end_1', priority: 1, isDefault: 0, cond: '' },
  ],
};

export async function publishMinimalFlow(
  request: APIRequestContext,
  token: string,
  tempCode: string,
  tempName: string
): Promise<{ tempCode: string }> {
  await ensureSystemContext(request, token);
  const temp = await apiCall<{ id: string }>(request, token, 'POST', '/v1/system/flow/temps/upsert', {
    tempCode,
    tempName,
    status: 1,
  });
  const ver = await apiCall<{ id: string }>(request, token, 'POST', '/v1/system/flow/temp-vers/upsert', {
    tempId: temp.id,
    publishStatus: 1,
    formJson: '{}',
  });
  await apiCall(request, token, 'POST', `/v1/system/flow/temp-vers/${ver.id}/graph-designer`, GRAPH_BODY);
  await apiCall(request, token, 'POST', `/v1/system/flow/temp-vers/${ver.id}/publish`);
  return { tempCode };
}

export async function startFlowInstance(
  request: APIRequestContext,
  token: string,
  tempCode: string,
  title: string,
  bizId: string
): Promise<{ instanceId: string; taskId: string }> {
  await ensureSystemContext(request, token);
  return apiCall(request, token, 'POST', '/v1/system/flow/instances/start', {
    defCode: tempCode,
    title,
    bizType: 'ui_test',
    bizId,
  });
}
