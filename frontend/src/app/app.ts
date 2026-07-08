import { normalizeRoute } from './routes';
import { canEnterPlatformAdmin, initializeShellState, shellState } from './state';
import { renderAuthPage } from '../features/auth/authPages';
import { renderNoMemberAccessPage } from '../features/no-member/noMemberAccess';
import { renderPlatformShell } from '../features/platform/platformShell';
import { renderSystemShell } from '../features/system-shell/systemShell';
import { installCommandCenterShortcut } from '../features/command-center/commandCenter';
import { createButton, createElement } from '../shared/components';

export type Navigate = (path: string) => void;

const AUTH_MESSAGE_KEY = 'unexamine.authMessage';

export function mountApp(root: HTMLElement): void {
  normalizeBrowserLocation();
  const navigate: Navigate = (path) => {
    window.location.hash = path;
  };
  installCommandCenterShortcut(navigate);

  const render = () => {
    const route = normalizeRoute(window.location.hash);
    root.replaceChildren(renderRoute(route, navigate));
  };

  window.addEventListener('hashchange', render);
  root.replaceChildren(createElement('main', { className: 'auth-layout' }, createElement('p', {}, '正在检查登录状态...')));
  if (!localStorage.getItem('unexamine.accessToken')) {
    render();
    return;
  }
  void initializeShellState()
    .catch((error) => {
      console.warn('Shell bootstrap failed, clearing local session.', error);
      clearLocalSession('登录状态已失效，请重新登录。');
    })
    .finally(render);
}

function normalizeBrowserLocation(): void {
  if (window.location.hash) {
    return;
  }
  const directPath = `${window.location.pathname}${window.location.search}`;
  if (directPath !== '/' && directPath !== '/index.html') {
    window.history.replaceState(null, '', `/#${directPath}`);
    return;
  }
  if (localStorage.getItem('unexamine.accessToken')) {
    window.location.hash = '/platform';
  }
}

function renderRoute(route: string, navigate: Navigate): HTMLElement {
  const isPublicAuthRoute = isAuthRoute(route);
  const hasToken = hasSessionToken();
  const hasReadySession = hasToken && Boolean(shellState.account.accountId);

  if (!hasReadySession && !isPublicAuthRoute) {
    if (hasToken) {
      clearLocalSession('登录状态已失效，请重新登录。');
    }
    queueMicrotask(() => navigate('/login'));
    return renderAuthPage('/login', navigate);
  }

  if (hasReadySession && isPublicAuthRoute) {
    queueMicrotask(() => navigate(defaultAuthenticatedRoute()));
    return createElement('main', { className: 'auth-layout' }, createElement('p', {}, '正在进入系统...'));
  }

  if (isPublicAuthRoute) {
    return renderAuthPage(route, navigate);
  }

  if (route.startsWith('/no-member')) {
    return renderNoMemberAccessPage(route, navigate);
  }

  if (route === '/platform/admin' && !canEnterPlatformAdmin()) {
    return renderAccessDenied('平台后台', '当前账号没有平台后台权限。平台后台入口只对平台管理员和平台超管开放。', '/platform', navigate);
  }

  if (route.startsWith('/systems/')) {
    return renderSystemShell(route, navigate);
  }

  return renderPlatformShell(route, navigate);
}

function isAuthRoute(route: string): boolean {
  return route === '/register-with-system' || route === '/forgot-password' || route === '/login';
}

function hasSessionToken(): boolean {
  return Boolean(localStorage.getItem('unexamine.accessToken'));
}

function defaultAuthenticatedRoute(): string {
  if (shellState.account.platformRoles.length > 0) {
    return '/platform';
  }
  if (shellState.currentSystem?.systemId) {
    return `/systems/${shellState.currentSystem.systemId}/dashboard`;
  }
  return shellState.availableSystems[0]?.systemId ? '/platform' : '/platform';
}

function clearLocalSession(message?: string): void {
  localStorage.removeItem('unexamine.accessToken');
  localStorage.removeItem('unexamine.refreshToken');
  localStorage.removeItem('unexamine.accountId');
  if (message) {
    sessionStorage.setItem(AUTH_MESSAGE_KEY, message);
  }
}

function renderAccessDenied(title: string, reason: string, backPath: string, navigate: Navigate): HTMLElement {
  const backButton = createButton('返回可访问页面', 'primary', false);
  backButton.addEventListener('click', () => navigate(backPath));

  return createElement(
    'main',
    { className: 'auth-layout' },
    createElement(
      'section',
      { className: 'auth-card' },
      createElement('div', { className: 'auth-brand' }, 'unexamine'),
      createElement('h1', {}, `无权限访问：${title}`),
      createElement('p', {}, reason),
      backButton,
    ),
  );
}
