import { normalizeRoute } from './routes';
import { canEnterPlatformAdmin, initializeShellState } from './state';
import { renderAuthPage } from '../features/auth/authPages';
import { renderNoMemberAccessPage } from '../features/no-member/noMemberAccess';
import { renderPlatformShell } from '../features/platform/platformShell';
import { renderSystemShell } from '../features/system-shell/systemShell';
import { createButton, createElement } from '../shared/components';

export type Navigate = (path: string) => void;

export function mountApp(root: HTMLElement): void {
  normalizeBrowserLocation();
  const navigate: Navigate = (path) => {
    window.location.hash = path;
  };

  const render = () => {
    const route = normalizeRoute(window.location.hash);
    root.replaceChildren(renderRoute(route, navigate));
  };

  window.addEventListener('hashchange', render);
  root.replaceChildren(createElement('main', { className: 'auth-layout' }, createElement('p', {}, 'Loading...')));
  if (!localStorage.getItem('unexamine.accessToken')) {
    render();
    return;
  }
  void initializeShellState()
    .catch((error) => {
      console.warn('Shell bootstrap failed, rendering with local state.', error);
      localStorage.removeItem('unexamine.accessToken');
      localStorage.removeItem('unexamine.refreshToken');
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
  if (route === '/register-with-system' || route === '/forgot-password' || route === '/login') {
    return renderAuthPage(route, navigate);
  }

  if (route.startsWith('/no-member')) {
    return renderNoMemberAccessPage(route, navigate);
  }

  if (route === '/platform/admin' && !canEnterPlatformAdmin()) {
    return renderAccessDenied('平台后台', '当前账号未配置平台后台权限，入口在平台工作台中对未授权角色隐藏。', '/platform', navigate);
  }

  if (route.startsWith('/systems/')) {
    return renderSystemShell(route, navigate);
  }

  return renderPlatformShell(route, navigate);
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
      createElement('h1', {}, `无权限访问${title}`),
      createElement('p', {}, reason),
      backButton,
    ),
  );
}
