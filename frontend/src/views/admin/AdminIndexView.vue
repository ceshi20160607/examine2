<script setup lang="ts">
import { ShieldX } from 'lucide-vue-next'
import { computed, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const props = defineProps<{ zone: 'platform' | 'system' }>()
const session = useSessionStore()
const route = useRoute()
const router = useRouter()

interface AdminRouteCandidate {
  permissions: string[]
  path: string
}

const firstRoute = computed(() => {
  const systemId = String(route.params.systemId ?? '')
  const candidates: AdminRouteCandidate[] = props.zone === 'platform'
    ? [
        { permissions: ['platform.system.manage'], path: '/platform/admin/systems' },
        { permissions: ['platform.organization.manage'], path: '/platform/admin/organization' },
        { permissions: ['platform.role.manage'], path: '/platform/admin/roles' },
        { permissions: ['platform.ai.policy.manage'], path: '/platform/admin/agent' },
        { permissions: ['platform.openapi.application.manage'], path: '/platform/admin/openapi-applications' },
        { permissions: ['platform.dashboard.manage'], path: '/platform/admin/dashboards' },
        { permissions: ['platform.flow.manage'], path: '/platform/admin/flows' },
        { permissions: ['platform.audit.view'], path: '/platform/admin/audit-logs' },
        { permissions: ['platform.settings.manage'], path: '/platform/admin/global-settings' },
      ]
    : [
        { permissions: ['system.admin.access', 'module.config.manage', 'system.member.manage'], path: `/systems/${systemId}/admin/onboarding` },
        { permissions: ['system.settings.manage'], path: `/systems/${systemId}/admin/settings` },
        { permissions: ['system.tenant.manage'], path: `/systems/${systemId}/admin/tenants` },
        { permissions: ['system.organization.manage'], path: `/systems/${systemId}/admin/organization` },
        { permissions: ['system.member.manage'], path: `/systems/${systemId}/admin/organization` },
        { permissions: ['system.role.manage'], path: `/systems/${systemId}/admin/roles` },
        { permissions: ['system.audit.view'], path: `/systems/${systemId}/admin/audit-logs` },
        { permissions: ['system.access.review'], path: `/systems/${systemId}/admin/access-requests` },
        { permissions: ['system.admin.access', 'module.config.manage'], path: `/systems/${systemId}/admin/configuration` },
        { permissions: ['event.template.manage'], path: `/systems/${systemId}/admin/message-templates` },
        { permissions: ['ai.policy.manage'], path: `/systems/${systemId}/admin/agent` },
        { permissions: ['openapi.application.manage'], path: `/systems/${systemId}/admin/openapi-applications` },
      ]
  return candidates.find((candidate) => candidate.permissions.every(session.hasPermission))?.path
})

watchEffect(() => {
  if (firstRoute.value) void router.replace(firstRoute.value)
})
</script>

<template>
  <section v-if="!firstRoute" class="admin-empty-state">
    <ShieldX :size="32" />
    <h1>没有可用的管理页面</h1>
    <p>后台入口已授权，但当前权限快照未包含任何管理功能。</p>
  </section>
</template>
