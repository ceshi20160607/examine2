import { API_CONTRACT_VERSION } from '../../api/types';
import { confirmPasswordReset, loginWithPassword, registerWithSystem, requestPasswordReset } from '../../api/liveData';
import type { Navigate } from '../../app/app';
import { initializeShellState } from '../../app/state';
import { createButton, createElement, createTraceLine } from '../../shared/components';

const AUTH_MESSAGE_KEY = 'unexamine.authMessage';

export function renderAuthPage(route: string, navigate: Navigate): HTMLElement {
  return createElement(
    'main',
    { className: 'auth-layout', dataset: { authRoute: route } },
    createElement(
      'section',
      { className: 'auth-card auth-card-single', dataset: { authCard: 'entry' } },
      createElement('div', { className: 'auth-logo-row' }, createElement('span', { className: 'auth-logo-mark' }, 'U'), createElement('strong', {}, 'unexamine')),
      createElement(
        'div',
        { className: 'auth-card-head' },
        createElement('span', { className: 'eyebrow' }, '统一入口'),
        createElement('h1', {}, authTitle(route)),
      ),
      createAuthTabs(route, navigate),
      route === '/register-with-system'
        ? createRegisterPanel(navigate)
        : route === '/forgot-password'
          ? createPasswordResetPanel(navigate)
          : createLoginPanel(navigate),
      createElement('footer', { className: 'auth-footer' }, `API ${API_CONTRACT_VERSION}`),
    ),
  );
}

function authTitle(route: string): string {
  if (route === '/register-with-system') {
    return '注册账号并创建系统';
  }
  if (route === '/forgot-password') {
    return '找回密码';
  }
  return '账号登录';
}

function createAuthTabs(route: string, navigate: Navigate): HTMLElement {
  return createElement(
    'nav',
    { className: 'auth-tabs', ariaLabel: '认证入口', dataset: { authTabs: 'true' } },
    createTab('登录', '/login', route, navigate),
    createTab('注册', '/register-with-system', route, navigate),
    createTab('找回密码', '/forgot-password', route, navigate),
  );
}

function createTab(label: string, path: string, route: string, navigate: Navigate): HTMLButtonElement {
  const button = createButton(label, route === path ? 'primary' : 'ghost', false);
  button.type = 'button';
  button.dataset.authTab = path;
  button.addEventListener('click', () => navigate(path));
  return button;
}

function createLoginPanel(navigate: Navigate): HTMLElement {
  const status = createElement('p', { className: 'field-error', dataset: { authStatus: 'login' } }, consumeAuthMessage() ?? '请输入账号和密码。');
  const loginButton = createButton('登录', 'primary', false);
  loginButton.type = 'button';
  loginButton.dataset.authAction = 'login';
  const form = createElement(
    'form',
    { className: 'auth-form', dataset: { authForm: 'login' } },
    createField('账号', '手机号 / 邮箱 / 用户名', 'text', 'loginName'),
    createField('密码', '请输入密码', 'password', 'password'),
    status,
    loginButton,
    createTraceLine('trace_login_ready', 'aud_login_ready'),
  );
  form.addEventListener('submit', (event) => event.preventDefault());
  loginButton.addEventListener('click', async () => {
    await runButtonAction(loginButton, status, '正在登录...', async () => {
      const loginName = valueOf(form, 'loginName');
      const password = valueOf(form, 'password');
      if (!loginName || !password) {
        throw new Error('账号和密码不能为空。');
      }
      const login = await loginWithPassword(loginName, password);
      await initializeShellState();
      status.textContent = `登录成功，traceId=${login.traceId}`;
      navigate(defaultLandingPath(login.defaultLanding?.route));
    });
  });
  return form;
}

function createRegisterPanel(navigate: Navigate): HTMLElement {
  const status = createElement('p', { className: 'field-error', dataset: { authStatus: 'register' } }, '注册会同时创建第一个业务系统，创建人自动成为该系统超级管理员。');
  const createButtonElement = createButton('创建账号并初始化系统', 'primary', false);
  createButtonElement.type = 'button';
  createButtonElement.dataset.authAction = 'registerWithSystem';
  const form = createElement(
    'form',
    { className: 'auth-form', dataset: { authForm: 'registerWithSystem' } },
    createField('姓名', '系统创建人姓名', 'text', 'accountName'),
    createField('手机号', '用于登录和找回密码', 'tel', 'mobile'),
    createField('邮箱', '可选，用于通知', 'email', 'email'),
    createField('密码', '至少 8 位', 'password', 'password'),
    createField('系统名称', '例如：业务管理系统', 'text', 'systemName'),
    createField('系统编码', 'business', 'text', 'systemCode'),
    createElement(
      'section',
      { className: 'result-panel', dataset: { registerResult: 'guidePreview' } },
      createElement('strong', {}, '创建后进入初始化向导'),
      createElement('p', {}, '创建成功后会进入系统后台，继续完成系统信息、组织架构、角色权限、模块字段和发布检查。'),
      createElement(
        'ol',
        {},
        createElement('li', {}, '系统信息'),
        createElement('li', {}, '组织架构'),
        createElement('li', {}, '角色权限'),
        createElement('li', {}, '模块字段'),
        createElement('li', {}, '发布检查'),
      ),
    ),
    status,
    createButtonElement,
  );
  form.addEventListener('submit', (event) => event.preventDefault());
  createButtonElement.addEventListener('click', async () => {
    await runButtonAction(createButtonElement, status, '正在创建系统...', async () => {
      const accountName = valueOf(form, 'accountName');
      const mobile = valueOf(form, 'mobile');
      const password = valueOf(form, 'password');
      const systemName = valueOf(form, 'systemName');
      const systemCode = valueOf(form, 'systemCode');
      if (!accountName || !mobile || !password || !systemName || !systemCode) {
        throw new Error('姓名、手机号、密码、系统名称和系统编码不能为空。');
      }
      const result = await registerWithSystem({
        accountName,
        mobile,
        email: valueOf(form, 'email'),
        password,
        systemName,
        systemCode,
      });
      await initializeShellState();
      status.textContent = `创建成功，初始化步骤 ${result.initGuideSteps.length} 项，auditLogId=${result.auditLogId}`;
      navigate(`/systems/${result.systemId}/admin`);
    });
  });
  return form;
}

function createPasswordResetPanel(navigate: Navigate): HTMLElement {
  const status = createElement('p', { className: 'field-error', dataset: { authStatus: 'forgotPassword' } }, '先申请重置凭证，再用验证码确认新密码。');
  const requestButton = createButton('发送验证码', 'secondary', false);
  requestButton.type = 'button';
  requestButton.dataset.authAction = 'requestPasswordReset';
  const confirmButton = createButton('确认重置', 'primary', false);
  confirmButton.type = 'button';
  confirmButton.dataset.authAction = 'confirmPasswordReset';
  const backButton = createButton('返回登录', 'ghost', false);
  backButton.type = 'button';
  backButton.dataset.authAction = 'backToLogin';
  backButton.addEventListener('click', () => navigate('/login'));

  const form = createElement(
    'form',
    { className: 'auth-form two-column', dataset: { authForm: 'forgotPassword' } },
    createElement(
      'section',
      { className: 'result-panel' },
      createElement('strong', {}, '申请重置'),
      createField('账号', '手机号 / 邮箱', 'text', 'loginName'),
      requestButton,
    ),
    createElement(
      'section',
      { className: 'result-panel' },
      createElement('strong', {}, '确认新密码'),
      createField('重置凭证', 'reset ticket', 'text', 'resetTicket'),
      createField('验证码', '6 位验证码', 'text', 'verifyCode'),
      createField('新密码', '至少 8 位', 'password', 'newPassword'),
      confirmButton,
    ),
    status,
    backButton,
  );
  form.addEventListener('submit', (event) => event.preventDefault());
  requestButton.addEventListener('click', async () => {
    await runButtonAction(requestButton, status, '正在发送验证码...', async () => {
      const loginName = valueOf(form, 'loginName');
      if (!loginName) {
        throw new Error('账号不能为空。');
      }
      const result = await requestPasswordReset(loginName);
      setValue(form, 'resetTicket', result.resetTicket);
      if (result.verifyCode) {
        setValue(form, 'verifyCode', result.verifyCode);
      }
      status.textContent = `验证码已发送，凭证 ${result.resetTicket}，有效期至${result.expiresAt}`;
    });
  });
  confirmButton.addEventListener('click', async () => {
    await runButtonAction(confirmButton, status, '正在重置密码...', async () => {
      const resetTicket = valueOf(form, 'resetTicket');
      const verifyCode = valueOf(form, 'verifyCode');
      const newPassword = valueOf(form, 'newPassword');
      if (!resetTicket || !verifyCode || !newPassword) {
        throw new Error('重置凭证、验证码和新密码不能为空。');
      }
      await confirmPasswordReset(resetTicket, verifyCode, newPassword);
      status.textContent = '密码已重置，可以返回登录。';
    });
  });
  return form;
}

function createField(label: string, placeholder: string, type: string, fieldName: string): HTMLElement {
  const input = createElement('input', { ariaLabel: label, dataset: { fieldName } });
  input.placeholder = placeholder;
  input.type = type;
  input.autocomplete = type === 'password' ? 'current-password' : 'username';
  return createElement('label', { className: 'form-field', dataset: { authField: fieldName } }, createElement('span', {}, label), input);
}

async function runButtonAction(button: HTMLButtonElement, status: HTMLElement, busyText: string, action: () => Promise<void>): Promise<void> {
  const originalText = button.textContent ?? '';
  button.disabled = true;
  button.textContent = busyText;
  status.textContent = busyText;
  try {
    await action();
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : '操作失败，请稍后重试。';
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

function consumeAuthMessage(): string | undefined {
  const message = sessionStorage.getItem(AUTH_MESSAGE_KEY) ?? undefined;
  if (message) {
    sessionStorage.removeItem(AUTH_MESSAGE_KEY);
  }
  return message;
}

function valueOf(form: HTMLElement, fieldName: string): string {
  return form.querySelector<HTMLInputElement>(`input[data-field-name="${fieldName}"]`)?.value.trim() ?? '';
}

function setValue(form: HTMLElement, fieldName: string, value: string): void {
  const input = form.querySelector<HTMLInputElement>(`input[data-field-name="${fieldName}"]`);
  if (input) {
    input.value = value;
  }
}

function defaultLandingPath(route?: string): string {
  if (route === '/platform/dashboard') {
    return '/platform';
  }
  if (route?.startsWith('/')) {
    return route;
  }
  return '/platform';
}
