import { describe, expect, it } from 'vitest'

import router from '@/router'

describe('OpenAPI application route access', () => {
  it('registers the frozen system-admin path behind the manage permission', () => {
    const route = router.getRoutes()
      .find((candidate) => candidate.name === 'system-admin-openapi-applications')

    expect(route?.path).toBe('/systems/:systemId/admin/openapi-applications')
    expect(route?.meta.requiredPermissions).toEqual(['openapi.application.manage'])
  })
})
