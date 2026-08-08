<script setup lang="ts">
import { CheckCircle2, CircleAlert, Eye, RefreshCw, Rocket, ShieldCheck } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { systemAdminApi } from '@/services/admin'
import { ApiRequestError } from '@/services/api'
import { configApi } from '@/services/config'
import type { SystemMember, Tenant } from '@/types/admin'
import type { CheckIssue, CheckReport, ConfigRoot, PermissionPreview } from '@/types/config'

type StepState = 'PENDING' | 'PASS' | 'FAIL'

interface AssessmentStep {
  state: StepState
  title: string
  summary: string
  details: string[]
}

const route = useRoute()
const router = useRouter()
const systemId = computed(() => String(route.params.systemId))
const running = ref(false)
const error = ref('')
const root = ref<ConfigRoot | null>(null)
const checkReport = ref<CheckReport | null>(null)
const memberPreview = ref<PermissionPreview | null>(null)
const previewMember = ref<SystemMember | null>(null)
const previewTenant = ref<Tenant | null>(null)
const evaluated = ref(false)

const minimumStep = ref<AssessmentStep>(pending('最低配置检查', '尚未检查系统的最低可运行配置。'))
const previewStep = ref<AssessmentStep>(pending('普通成员预览', '尚未验证普通成员看到的草稿导航。'))
const publicationStep = ref<AssessmentStep>(pending('发布检查', '尚未执行当前草稿修订的服务端发布检查。'))

const steps = computed(() => [minimumStep.value, previewStep.value, publicationStep.value])
const ready = computed(() => evaluated.value && steps.value.every(step => step.state === 'PASS'))
const failedCount = computed(() => steps.value.filter(step => step.state === 'FAIL').length)

function pending(title: string, summary: string): AssessmentStep {
  return { state: 'PENDING', title, summary, details: [] }
}

function failure(title: string, summary: string, details: string[] = []): AssessmentStep {
  return { state: 'FAIL', title, summary, details }
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '上线检查失败，请稍后重试'
}

function activeTenantFor(member: SystemMember, tenants: Tenant[]) {
  return tenants.find(tenant => tenant.status === 'ACTIVE' && member.tenantIds.includes(tenant.id)) ?? null
}

async function findOrdinaryMemberPreview(members: SystemMember[], tenants: Tenant[]) {
  const candidates = members.filter(member => member.status === 'ACTIVE')
  for (const member of candidates) {
    const tenant = activeTenantFor(member, tenants)
    if (!tenant) continue
    try {
      const preview = await configApi.preview(systemId.value, member.id, tenant.id)
      if (!preview.root) return { member, tenant, preview }
    } catch (cause) {
      if (!(cause instanceof ApiRequestError) || ![403, 404].includes(cause.status)) throw cause
    }
  }
  return null
}

async function runAssessment() {
  if (running.value) return
  running.value = true
  evaluated.value = false
  error.value = ''
  checkReport.value = null
  memberPreview.value = null
  previewMember.value = null
  previewTenant.value = null
  minimumStep.value = pending('最低配置检查', '正在读取当前草稿结构…')
  previewStep.value = pending('普通成员预览', '等待最低配置检查完成…')
  publicationStep.value = pending('发布检查', '等待普通成员预览完成…')

  try {
    const [configRoot, groups, modules, memberPage, tenantPage] = await Promise.all([
      configApi.root(systemId.value),
      configApi.groups(systemId.value),
      configApi.modules(systemId.value),
      systemAdminApi.listMembers(systemId.value, { page: 1, size: 100 }),
      systemAdminApi.listTenants(systemId.value, { page: 1, size: 100 }),
    ])
    root.value = configRoot

    const enabledGroups = groups.filter(group => group.status === 'ENABLED')
    const enabledModules = modules.filter(module => module.status === 'ENABLED'
      && enabledGroups.some(group => group.id === module.groupId))
    const firstModule = enabledModules[0]
    const [fields, pages] = firstModule
      ? await Promise.all([
          configApi.fields(systemId.value, firstModule.id),
          configApi.pages(systemId.value, firstModule.id),
        ])
      : [[], []]
    const enabledFields = fields.filter(field => field.status === 'ENABLED')
    const enabledPages = pages.filter(page => page.status === 'ENABLED')
    const pageTypes = new Set(enabledPages.map(page => page.type))
    const minimumIssues = [
      !tenantPage.items.some(tenant => tenant.status === 'ACTIVE') ? '至少需要一个启用租户' : '',
      !enabledGroups.length ? '至少需要一个启用模块组' : '',
      !enabledModules.length ? '至少需要一个属于启用模块组的启用模块' : '',
      firstModule && !enabledFields.length ? `${firstModule.name} 至少需要一个启用字段` : '',
      firstModule && !['LIST', 'FORM', 'DETAIL'].every(type => pageTypes.has(type as 'LIST' | 'FORM' | 'DETAIL'))
        ? `${firstModule.name} 需要 LIST、FORM、DETAIL 三类启用页面`
        : '',
    ].filter(Boolean)
    minimumStep.value = minimumIssues.length
      ? failure('最低配置检查', `发现 ${minimumIssues.length} 个最低配置缺口。`, minimumIssues)
      : {
          state: 'PASS',
          title: '最低配置检查',
          summary: '系统已具备可发布的最小业务结构。',
          details: [
            `${tenantPage.items.filter(tenant => tenant.status === 'ACTIVE').length} 个启用租户`,
            `${enabledGroups.length} 个启用模块组 / ${enabledModules.length} 个启用模块`,
            `${firstModule?.name ?? '首个模块'}：${enabledFields.length} 个字段，LIST/FORM/DETAIL 页面齐全`,
          ],
        }

    const ordinary = await findOrdinaryMemberPreview(memberPage.items, tenantPage.items)
    if (!ordinary) {
      previewStep.value = failure('普通成员预览', '没有可用于验收的启用普通成员。', [
        '请先在组织成员中创建或启用一个非 Root 成员，并为其分配租户和已发布角色。',
      ])
    } else {
      previewMember.value = ordinary.member
      previewTenant.value = ordinary.tenant
      memberPreview.value = ordinary.preview
      const draftGroups = ordinary.preview.draft.groups
      const draftModules = draftGroups.flatMap(group => group.modules)
      previewStep.value = draftModules.length
        ? {
            state: 'PASS',
            title: '普通成员预览',
            summary: `${ordinary.member.displayName} 在草稿中可见 ${draftModules.length} 个模块。`,
            details: [
              `租户：${ordinary.tenant.name}`,
              `角色来源：${ordinary.preview.sourceRoles.map(role => role.name).join('、') || '无角色来源'}`,
              `草稿导航：${draftGroups.map(group => `${group.name}（${group.modules.length}）`).join('、')}`,
            ],
          }
        : failure('普通成员预览', `${ordinary.member.displayName} 无法看到任何草稿模块。`, [
            '请检查角色发布版本、模块查看权限和数据范围。',
          ])
    }

    const report = await configApi.check(systemId.value, configRoot.draftRevision)
    checkReport.value = report
    publicationStep.value = report.blockerCount === 0
      ? {
          state: 'PASS',
          title: '发布检查',
          summary: `修订 ${report.draftRevision} 可以发布。`,
          details: [
            `检查号：${report.id}`,
            `${report.warningCount} 个警告，快照 ${report.snapshotSizeBytes} bytes`,
          ],
        }
      : failure('发布检查', `存在 ${report.blockerCount} 个发布阻断。`, issueDetails(report.issues))
    evaluated.value = true
  } catch (cause) {
    error.value = message(cause)
  } finally {
    running.value = false
  }
}

function issueDetails(issues: CheckIssue[]) {
  return issues.filter(issue => issue.severity === 'BLOCKER').slice(0, 8)
    .map(issue => `${issue.code}：${issue.message}`)
}

function openMemberRuntime() {
  const first = memberPreview.value?.draft.groups.flatMap(group => group.modules)[0]
  if (!first) return
  void router.push({
    path: `/systems/${systemId.value}/workbench`,
    query: { module: first.code },
  })
}
</script>

<template>
  <section class="onboarding-page">
    <header class="onboarding-heading">
      <div>
        <h1><Rocket :size="24" />系统上线引导</h1>
        <p>用同一份当前草稿依次验证最低配置、普通成员权限预览和服务端发布检查。</p>
      </div>
      <a-button class="onboarding-run" type="primary" :loading="running" @click="runAssessment">
        <RefreshCw :size="16" />{{ evaluated ? '重新检查' : '开始上线检查' }}
      </a-button>
    </header>

    <a-alert v-if="error" class="onboarding-error" type="error" show-icon :message="error" />

    <div class="onboarding-summary" :data-state="ready ? 'ready' : evaluated ? 'blocked' : 'pending'">
      <CheckCircle2 v-if="ready" :size="26" />
      <CircleAlert v-else :size="26" />
      <div>
        <strong v-if="ready">上线检查通过，可以进入发布操作</strong>
        <strong v-else-if="evaluated">上线检查未通过，仍有 {{ failedCount }} 个步骤需要处理</strong>
        <strong v-else>尚未生成上线结论</strong>
        <span>本页只执行检查，不会自动发布，也不会让草稿污染成员运行态。</span>
      </div>
    </div>

    <ol class="onboarding-steps">
      <li v-for="(step, index) in steps" :key="step.title" :data-state="step.state">
        <div class="step-index">{{ index + 1 }}</div>
        <div class="step-content">
          <header><strong>{{ step.title }}</strong><a-tag :color="step.state === 'PASS' ? 'green' : step.state === 'FAIL' ? 'red' : 'default'">{{ step.state }}</a-tag></header>
          <p>{{ step.summary }}</p>
          <ul v-if="step.details.length"><li v-for="detail in step.details" :key="detail">{{ detail }}</li></ul>
        </div>
      </li>
    </ol>

    <section class="onboarding-actions">
      <div><ShieldCheck :size="20" /><span><strong>修复与复核</strong><small>按失败步骤进入现有管理页面处理，不创建第二套配置入口。</small></span></div>
      <div class="action-buttons">
        <a-button @click="router.push(`/systems/${systemId}/admin/configuration`)">模块配置</a-button>
        <a-button @click="router.push(`/systems/${systemId}/admin/organization`)">组织成员</a-button>
        <a-button @click="router.push(`/systems/${systemId}/admin/roles`)">角色权限</a-button>
        <a-button v-if="memberPreview?.draft.groups.length" class="onboarding-preview-runtime" @click="openMemberRuntime"><Eye :size="15" />查看成员运行入口</a-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.onboarding-page{display:grid;gap:18px;max-width:1180px;margin:0 auto;padding:28px}.onboarding-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.onboarding-heading h1{display:flex;align-items:center;gap:9px;margin:0}.onboarding-heading p{margin:7px 0 0;color:#596576}.onboarding-run{display:inline-flex;align-items:center;gap:7px}.onboarding-summary{display:flex;align-items:center;gap:13px;padding:16px 18px;border:1px solid #d7e0e5;border-radius:10px;background:#fff}.onboarding-summary>div{display:grid;gap:3px}.onboarding-summary span{color:#596576}.onboarding-summary[data-state=ready]{border-color:#8fcbbb;background:#f1faf6;color:#126b59}.onboarding-summary[data-state=blocked]{border-color:#e3b6a1;background:#fff7f2;color:#9c432b}.onboarding-steps{display:grid;gap:12px;margin:0;padding:0;list-style:none}.onboarding-steps>li{display:grid;grid-template-columns:40px minmax(0,1fr);gap:14px;padding:18px;border:1px solid #dbe3e7;border-radius:10px;background:#fff}.onboarding-steps>li[data-state=PASS]{border-left:4px solid #22846e}.onboarding-steps>li[data-state=FAIL]{border-left:4px solid #c9523d}.step-index{width:34px;height:34px;display:grid;place-items:center;border-radius:50%;background:#edf2f4;color:#455660;font-weight:750}.step-content{display:grid;gap:8px}.step-content header{display:flex;align-items:center;justify-content:space-between;gap:12px}.step-content p,.step-content ul{margin:0}.step-content p{color:#52636d}.step-content ul{padding-left:20px;color:#5b6872}.onboarding-actions{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:18px;border-radius:10px;background:#eef5f8}.onboarding-actions>div:first-child{display:flex;align-items:center;gap:10px}.onboarding-actions span{display:grid}.onboarding-actions small{color:#53636d}.action-buttons{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.action-buttons .ant-btn{display:inline-flex;align-items:center;gap:6px}@media(max-width:760px){.onboarding-page{padding:16px}.onboarding-heading,.onboarding-actions{align-items:stretch;flex-direction:column}.onboarding-run{align-self:flex-start}.action-buttons{justify-content:flex-start}}
</style>
