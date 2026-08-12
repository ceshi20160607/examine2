import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformAdminLayout from '@/layouts/PlatformAdminLayout.vue'
import router from '@/router'
import { enterpriseIdentityAdminApi } from '@/services/enterpriseIdentity'
import { useSessionStore } from '@/stores/session'
import type { IdentityProviderCommand } from '@/types/enterpriseIdentity'

vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>()
  return { ...original, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }
})

function envelope(data: unknown) {
  return new Response(JSON.stringify({
    code: 'OK', message: '', data, requestId: 'request-1', traceId: 'trace-1', errors: [],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

const command: IdentityProviderCommand = {
  providerCode: 'corp-oidc', name: '企业 OIDC', protocol: 'OIDC', issuerUri: 'https://id.example.com',
  authorizationEndpoint: 'https://id.example.com/authorize', tokenEndpoint: 'https://id.example.com/token',
  jwksUri: 'https://id.example.com/jwks', directoryEndpoint: null, clientId: 'examine2',
  secretRef: 'env://CORP_OIDC_SECRET', secretVersion: 'v1', callbackUri: 'https://app.example.com/auth/callback',
  scopes: 'openid profile email', allowedDomains: ['example.com'], attributeMapping: { email: 'email' },
  jitAccount: true, jitSystemMember: false, systemId: null, tenantId: null, mfaPolicy: 'REQUIRED',
  expectedVersion: null,
}

describe('enterprise identity administration', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    setActivePinia(createPinia())
    document.cookie = 'EXAMINE_CSRF=csrf-token; path=/'
  })

  it('uses the provider lifecycle endpoints and sends optimistic versions', async () => {
    const provider = {
      id: 'provider/1', ...command, expectedVersion: undefined, secretRef: undefined,
      secretRefMasked: 'env://********', status: 'DRAFT', preflightStatus: 'PASSED', preflightVersion: 2,
      preflightFailureCode: null, preflightAt: '2026-08-07T00:00:00Z', publishedAt: null, version: 2,
    }
    const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(envelope(provider)))
    vi.stubGlobal('fetch', fetchMock)

    await enterpriseIdentityAdminApi.list()
    await enterpriseIdentityAdminApi.create(command)
    await enterpriseIdentityAdminApi.update('provider/1', { ...command, expectedVersion: 2 })
    await enterpriseIdentityAdminApi.preflight('provider/1', 2)
    await enterpriseIdentityAdminApi.publish('provider/1', 3)
    await enterpriseIdentityAdminApi.disable('provider/1', 4)

    const root = '/api/v1/platform/admin/identity-providers'
    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      root, root, `${root}/provider%2F1`, `${root}/provider%2F1:preflight`,
      `${root}/provider%2F1:publish`, `${root}/provider%2F1:disable`,
    ])
    expect(JSON.parse(String((fetchMock.mock.calls[2]![1] as RequestInit).body))).toMatchObject({
      providerCode: 'corp-oidc', secretRef: 'env://CORP_OIDC_SECRET', expectedVersion: 2,
    })
    expect(JSON.parse(String((fetchMock.mock.calls[3]![1] as RequestInit).body))).toEqual({ expectedVersion: 2 })
  })

  it('registers and permission-gates the enterprise identity page', () => {
    const routeRecord = router.getRoutes().find(item => item.name === 'platform-admin-identity-providers')
    expect(routeRecord?.path).toBe('/platform/admin/identity-providers')
    expect(routeRecord?.meta.requiredPermissions).toEqual(['platform.organization.manage'])

    useSessionStore().applyAuth({
      account: { id: '1', username: 'root', displayName: 'Root' },
      context: {
        type: 'PLATFORM', account: { id: '1', username: 'root', displayName: 'Root' },
        permissionVersion: '1', permissions: ['platform.organization.manage'], shells: ['PLATFORM_ADMIN'],

        roleIds: [],
        dataScope: null,
        restrictedMode: 'NONE',
      },
      systems: [],

      tenants: [],
      firstSystemId: null,
    })
    const wrapper = mount(PlatformAdminLayout, { global: { stubs: {
      RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' }, RouterView: true,
    } } })
    expect(wrapper.get('a[data-to="/platform/admin/identity-providers"]').text()).toContain('企业身份源')

    useSessionStore().applyAuth({
      account: { id: '2', username: 'reader', displayName: 'Reader' },
      context: {
        type: 'PLATFORM', account: { id: '2', username: 'reader', displayName: 'Reader' },
        permissionVersion: '1', permissions: [], shells: ['PLATFORM_ADMIN'],

        roleIds: [],
        dataScope: null,
        restrictedMode: 'NONE',
      },
      systems: [],

      tenants: [],
      firstSystemId: null,
    })
    const denied = mount(PlatformAdminLayout, { global: { stubs: {
      RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' }, RouterView: true,
    } } })
    expect(denied.find('a[data-to="/platform/admin/identity-providers"]').exists()).toBe(false)
  })
})
