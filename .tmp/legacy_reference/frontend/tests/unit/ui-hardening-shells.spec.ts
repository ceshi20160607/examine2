import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Component } from 'vue'

import AuthLayout from '@/layouts/AuthLayout.vue'
import PlatformAdminLayout from '@/layouts/PlatformAdminLayout.vue'
import PlatformLayout from '@/layouts/PlatformLayout.vue'
import SystemAdminLayout from '@/layouts/SystemAdminLayout.vue'
import SystemLayout from '@/layouts/SystemLayout.vue'
import { useSessionStore } from '@/stores/session'

import {
  expectAccessibleName,
  expectContainedByViewport,
  expectOwnedHorizontalOverflow,
  installElementBox,
  setTestViewport,
  UI_HARDENING_VIEWPORTS,
  type TestViewport,
} from './ui-hardening-testkit'

const route = vi.hoisted(() => ({ params: { systemId: 'system-10' } }))
const router = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))
const todoCounts = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('@/services/todo', () => ({
  TODO_COUNTS_CHANGED_EVENT: 'examine2:todo-counts-changed',
  todoApi: { counts: todoCounts },
}))

const RouterLinkStub = {
  props: ['to'],
  template: '<a :href="typeof to === \'string\' ? to : to?.path"><slot /></a>',
}

const commonStubs = {
  RouterLink: RouterLinkStub,
  RouterView: { template: '<section data-testid="route-outlet" />' },
  AccountSecurityDialog: true,
  'a-badge': { template: '<span><slot /></span>' },
  'a-dropdown': { template: '<div class="dropdown-stub"><slot /></div>' },
  'a-menu': { template: '<div><slot /></div>' },
  'a-menu-divider': true,
  'a-menu-item': { template: '<div><slot /></div>' },
}

type ShellKind = 'auth' | 'platform' | 'platform-admin' | 'system' | 'system-admin'

interface ShellCase {
  kind: ShellKind
  component: Component
  root: string
  scrollOwner?: string
  administration: boolean
}

const shells: readonly ShellCase[] = [
  { kind: 'auth', component: AuthLayout, root: '.auth-shell', administration: false },
  { kind: 'platform', component: PlatformLayout, root: '.app-shell', scrollOwner: '.topnav', administration: false },
  { kind: 'platform-admin', component: PlatformAdminLayout, root: '.admin-shell', scrollOwner: '.admin-sidebar nav', administration: true },
  { kind: 'system', component: SystemLayout, root: '.app-shell', scrollOwner: '.topnav', administration: false },
  { kind: 'system-admin', component: SystemAdminLayout, root: '.system-admin-shell', scrollOwner: '.admin-sidebar nav', administration: true },
]

function prepareSession(kind: ShellKind) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const session = useSessionStore()
  if (kind === 'auth') return pinia

  if (kind === 'platform' || kind === 'platform-admin') {
    session.$patch({
      initialized: true,
      context: {
        type: 'PLATFORM',
        account: { id: 'account-1', username: 'owner', displayName: '平台管理员' },
        permissionVersion: '12',
        permissions: [
          'platform.runtime.access',
          'platform.ai.agent.use',
          'platform.system.manage',
          'platform.organization.manage',
          'platform.role.manage',
          'platform.ai.policy.manage',
        ],
        shells: ['PLATFORM_RUNTIME', 'PLATFORM_ADMIN'],
      },
    })
    return pinia
  }

  session.$patch({
    initialized: true,
    context: {
      type: 'SYSTEM',
      account: { id: 'account-2', username: 'member', displayName: '系统成员' },
      systemId: 'system-10',
      systemName: '订单系统',
      tenantId: 'tenant-20',
      tenantName: '华东租户',
      memberId: 'member-30',
      permissionVersion: '18',
      permissions: [
        'system.admin.access', 'module.config.manage', 'system.settings.manage',
        'system.tenant.manage', 'system.organization.manage', 'system.member.manage',
        'system.role.manage', 'system.access.review', 'event.template.manage',
        'event.message.access', 'ai.policy.manage', 'ai.agent.use',
        'openapi.application.manage', 'work.task.access', 'file.read',
        'flow.instance.read',
      ],
      shells: ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
    },
    tenants: [{ id: 'tenant-20', name: '华东租户', status: 'ACTIVE' }],
  })
  vi.spyOn(session, 'loadTenants').mockResolvedValue(undefined)
  return pinia
}

async function renderShell(shell: ShellCase, viewport: TestViewport): Promise<VueWrapper> {
  setTestViewport(viewport)
  const pinia = prepareSession(shell.kind)
  const wrapper = mount(shell.component, {
    global: { plugins: [pinia], stubs: commonStubs },
  })
  await flushPromises()
  return wrapper
}

const matrix = shells.flatMap(shell => UI_HARDENING_VIEWPORTS.map(viewport => ({ shell, viewport })))

describe('UI hardening: five application shells', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    todoCounts.mockResolvedValue({ openCount: 3 })
  })

  it.each(matrix)('$shell.kind owns its layout and overflow at $viewport.name width', async ({ shell, viewport }) => {
    const wrapper = await renderShell(shell, viewport)
    const root = wrapper.get(shell.root).element

    expect(wrapper.find('[data-testid="route-outlet"]').exists()).toBe(true)
    expect(wrapper.findAll('main')).toHaveLength(1)
    expect(wrapper.findAll('nav').every(nav => Boolean(nav.attributes('aria-label')))).toBe(true)
    expect(wrapper.find('header').exists()).toBe(shell.kind !== 'auth')
    expect(wrapper.find('aside').exists()).toBe(shell.administration)

    installElementBox(root, { width: viewport.width, scrollWidth: viewport.width })
    expectContainedByViewport(root, viewport)

    if (shell.scrollOwner && viewport.width <= 768) {
      const scrollOwner = wrapper.get(shell.scrollOwner).element
      installElementBox(scrollOwner, {
        width: Math.max(240, viewport.width - 24),
        clientWidth: Math.max(240, viewport.width - 24),
        scrollWidth: viewport.width + 320,
      })
      expectOwnedHorizontalOverflow(scrollOwner)
      expectContainedByViewport(root, viewport)
    }

    for (const command of wrapper.findAll('button.admin-icon-command, button.icon-command')) {
      expect(command.attributes('aria-label')).toBeTruthy()
      expectAccessibleName(command.element)
    }

    wrapper.unmount()
  })

  it('keeps shell-level overflow detection capable of rejecting a leaking surface', async () => {
    const viewport = UI_HARDENING_VIEWPORTS[0]!
    const wrapper = await renderShell(shells[1]!, viewport)
    const root = wrapper.get('.app-shell').element
    installElementBox(root, { width: viewport.width, scrollWidth: viewport.width + 1 })

    expect(() => expectContainedByViewport(root, viewport)).toThrow(/leaks child overflow/u)
    wrapper.unmount()
  })
})
