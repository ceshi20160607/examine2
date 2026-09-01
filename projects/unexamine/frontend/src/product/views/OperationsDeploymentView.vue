<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, api } from '../api'
import { deployConfirmation, deploymentOutcome, passedDeploymentSteps, rollbackConfirmation, runtimeRegistrationReady } from '../operations-deployment'
import { systemTokens } from '../session'
import type { OperationsDeployment, OperationsDeploymentOverview, OperationsRelease } from '../types'

const basePath = '/api/admin/system/operations/deployments'
const token = computed(() => systemTokens.value?.accessToken)
const overview = ref<OperationsDeploymentOverview>()
const selected = ref<OperationsDeployment>()
const loading = ref(false)
const busy = ref('')
const error = ref('')
const success = ref('')
const releaseForm = reactive({ notes: '本次发布已完成固定包、环境隔离、真实入口与回滚兼容验证。', databaseRollbackStrategy: 'FORWARD_FIX' })
const deployForm = reactive({ releaseId: undefined as number | undefined, databaseStrategy: 'FORWARD_FIX', approvalReference: '', confirmation: '' })
const rollbackForm = reactive({ deploymentId: undefined as number | undefined, targetReleaseId: undefined as number | undefined, databaseStrategy: 'FORWARD_FIX', approvalReference: '', confirmation: '' })

const runtime = computed(() => overview.value?.runtime)
const matchingRelease = computed(() => overview.value?.releases.find(item => item.versionName === runtime.value?.backendVersion))
const successfulDeployments = computed(() => overview.value?.deployments.filter(item => item.deploymentType === 'DEPLOY' && item.status === 'SUCCESS') ?? [])
const selectedRelease = computed(() => overview.value?.releases.find(item => item.id === deployForm.releaseId))
const expectedDeployConfirmation = computed(() => selectedRelease.value && runtime.value
  ? deployConfirmation(selectedRelease.value.versionName, runtime.value.environmentCode) : '')
const expectedRollbackConfirmation = computed(() => rollbackForm.deploymentId ? rollbackConfirmation(rollbackForm.deploymentId) : '')

function explain(cause: unknown, fallback: string) {
  return cause instanceof ApiError
    ? `${cause.message}（${cause.code}${cause.traceId ? ` · requestId ${cause.traceId}` : ''}）`
    : fallback
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<OperationsDeploymentOverview>(basePath, {}, token.value)
    const release = matchingRelease.value ?? overview.value.releases[0]
    if (release && !deployForm.releaseId) deployForm.releaseId = release.id
    const successful = successfulDeployments.value[0]
    if (successful && !rollbackForm.deploymentId) rollbackForm.deploymentId = successful.id
    if (release && !rollbackForm.targetReleaseId) rollbackForm.targetReleaseId = release.id
    if (!selected.value && overview.value.deployments[0]) selected.value = overview.value.deployments[0]
  } catch (cause) {
    error.value = explain(cause, '发布与回滚数据加载失败')
  } finally {
    loading.value = false
  }
}

async function registerRelease() {
  if (!token.value || !runtime.value || !runtimeRegistrationReady(runtime.value)) return
  busy.value = 'register'
  error.value = ''
  success.value = ''
  try {
    const created = await api<OperationsRelease>(`${basePath}/releases`, {
      method: 'POST', body: JSON.stringify({
        versionName: runtime.value.backendVersion,
        artifactSha256: runtime.value.artifactSha256,
        databaseVersion: runtime.value.databaseVersion,
        configVersion: runtime.value.configVersion,
        frontendVersion: runtime.value.frontendVersion,
        databaseRollbackStrategy: releaseForm.databaseRollbackStrategy,
        releaseNotes: releaseForm.notes,
      }),
    }, token.value)
    deployForm.releaseId = created.id
    rollbackForm.targetReleaseId = created.id
    success.value = `不可变发布 ${created.versionName} 已登记并读回，SHA-256 ${created.artifactSha256.slice(0, 12)}…。`
    await load()
  } catch (cause) { error.value = explain(cause, '当前运行清单登记失败') } finally { busy.value = '' }
}

async function executeDeployment() {
  if (!token.value || !runtime.value || !selectedRelease.value) return
  busy.value = 'deploy'
  error.value = ''
  success.value = ''
  try {
    selected.value = await api<OperationsDeployment>(`${basePath}/execute`, {
      method: 'POST', body: JSON.stringify({
        releaseId: selectedRelease.value.id,
        environmentCode: runtime.value.environmentCode,
        expectedBackendVersion: selectedRelease.value.versionName,
        expectedFrontendVersion: selectedRelease.value.frontendVersion,
        databaseStrategy: deployForm.databaseStrategy,
        approvalReference: deployForm.approvalReference,
        confirmation: deployForm.confirmation,
      }),
    }, token.value)
    success.value = selected.value.status === 'SUCCESS'
      ? `发布 #${selected.value.id} 已持久化读回；全部 ${selected.value.steps.length} 步通过。`
      : `发布 #${selected.value.id} 已被门禁阻断：${selected.value.failureCode}。`
    await load()
  } catch (cause) { error.value = explain(cause, '发布执行失败') } finally { busy.value = '' }
}

async function prepareRollback() {
  if (!token.value || !rollbackForm.deploymentId || !rollbackForm.targetReleaseId) return
  busy.value = 'rollback'
  error.value = ''
  success.value = ''
  try {
    selected.value = await api<OperationsDeployment>(`${basePath}/${rollbackForm.deploymentId}/rollback`, {
      method: 'POST', body: JSON.stringify({
        targetReleaseId: rollbackForm.targetReleaseId,
        databaseStrategy: rollbackForm.databaseStrategy,
        approvalReference: rollbackForm.approvalReference,
        confirmation: rollbackForm.confirmation,
      }),
    }, token.value)
    success.value = selected.value.status === 'ROLLBACK_READY'
      ? '回滚方案已生成：应用、静态、配置与数据库恢复计划均已固化。'
      : `回滚被门禁阻断：${selected.value.failureCode}。`
    await load()
  } catch (cause) { error.value = explain(cause, '回滚准备失败') } finally { busy.value = '' }
}

function selectDeployment(item: OperationsDeployment) {
  selected.value = item
  if (item.deploymentType === 'DEPLOY' && item.status === 'SUCCESS') rollbackForm.deploymentId = item.id
}

function shortHash(value?: string) { return value ? `${value.slice(0, 12)}…${value.slice(-8)}` : '未配置' }
function statusColor(status?: string) { return status === 'SUCCESS' || status === 'ROLLBACK_READY' || status === 'PASSED' ? 'green' : status === 'FAILED' ? 'red' : 'blue' }
function environmentLabel(value?: string) {
  return ({ development: '开发', test: '测试', production: '生产' } as Record<string, string>)[String(value || '').toLowerCase()] || '当前'
}

onMounted(load)
</script>

<template>
  <section class="operations-deployment" :class="{ 'is-loading': loading }">
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" :type="selected?.status === 'FAILED' ? 'warning' : 'success'" show-icon :message="success" closable @close="success = ''" />

    <div class="panel-card deployment-hero" :data-ready="runtime?.ready ? 'true' : 'false'">
      <div><p class="eyebrow">发布门禁 · 固定制品 · 可恢复</p><h2>{{ environmentLabel(runtime?.environmentCode) }}环境发布与回滚</h2><p>前后端、配置、数据库迁移、审批、真实入口与回滚点必须形成同一份持久化证据。</p></div>
      <a-tag :color="runtime?.ready ? 'green' : 'red'">{{ runtime?.ready ? '清单可登记' : '清单未就绪' }}</a-tag>
    </div>

    <div class="deployment-manifest">
      <article class="panel-card"><small>后端 / 前端</small><strong>{{ runtime?.backendVersion || '—' }}</strong><span>{{ runtime?.frontendVersion || '—' }}</span></article>
      <article class="panel-card"><small>数据库 / 配置</small><strong>V{{ runtime?.databaseVersion || '—' }}</strong><span>{{ runtime?.configVersion || '—' }}</span></article>
      <article class="panel-card"><small>固定制品 SHA-256</small><strong>{{ shortHash(runtime?.artifactSha256) }}</strong><span>{{ runtime?.artifactDigestConfigured ? '已绑定运行制品' : '缺少 APP_ARTIFACT_SHA256' }}</span></article>
      <article class="panel-card"><small>真实入口</small><strong>{{ runtime?.frontendSmokeUrl || '—' }}</strong><span>发布成功前必须返回 2xx/3xx</span></article>
    </div>

    <div class="deployment-workbench">
      <section class="panel-card deployment-form">
        <div class="panel-title"><div><strong>1. 登记当前不可变发布</strong><small>运行值必须与清单逐项相等；同一版本不能替换摘要。</small></div></div>
        <a-radio-group v-model:value="releaseForm.databaseRollbackStrategy" button-style="solid"><a-radio-button value="FORWARD_FIX">数据库前向修复</a-radio-button><a-radio-button value="REVERSIBLE">可逆迁移</a-radio-button></a-radio-group>
        <a-textarea v-model:value="releaseForm.notes" :rows="2" :maxlength="2000" />
        <a-button type="primary" :disabled="Boolean(matchingRelease) || !runtimeRegistrationReady(runtime)" :loading="busy === 'register'" @click="registerRelease">
          {{ matchingRelease ? `已登记 ${matchingRelease.versionName}` : '登记当前运行清单' }}
        </a-button>
      </section>

      <section class="panel-card deployment-form">
        <div class="panel-title"><div><strong>2. 执行发布验收</strong><small>不可恢复迁移、版本错配、真实入口失败均只会得到 FAILED。</small></div></div>
        <a-select v-model:value="deployForm.releaseId" placeholder="选择发布版本"><a-select-option v-for="item in overview?.releases" :key="item.id" :value="item.id">{{ item.versionName }} · V{{ item.databaseVersion }}</a-select-option></a-select>
        <a-select v-model:value="deployForm.databaseStrategy"><a-select-option value="FORWARD_FIX">FORWARD_FIX</a-select-option><a-select-option value="REVERSIBLE">REVERSIBLE</a-select-option><a-select-option value="UNRECOVERABLE">UNRECOVERABLE（必定阻断）</a-select-option></a-select>
        <a-input v-model:value="deployForm.approvalReference" placeholder="审批单号，例如 CAB-2026-054" />
        <a-input v-model:value="deployForm.confirmation" :placeholder="expectedDeployConfirmation" />
        <small>确认短语：<code>{{ expectedDeployConfirmation || '请先选择版本' }}</code></small>
        <a-button type="primary" :loading="busy === 'deploy'" :disabled="!deployForm.releaseId || !deployForm.approvalReference || !deployForm.confirmation" @click="executeDeployment">执行并读回逐步证据</a-button>
      </section>

      <section class="panel-card deployment-form">
        <div class="panel-title"><div><strong>3. 生成回滚执行点</strong><small>应用、静态、配置按兼容组恢复；数据库只能前向修复或可逆。</small></div></div>
        <a-select v-model:value="rollbackForm.deploymentId" placeholder="选择成功发布"><a-select-option v-for="item in successfulDeployments" :key="item.id" :value="item.id">#{{ item.id }} · {{ item.releaseVersion }}</a-select-option></a-select>
        <a-select v-model:value="rollbackForm.targetReleaseId" placeholder="选择回滚版本"><a-select-option v-for="item in overview?.releases" :key="item.id" :value="item.id">{{ item.versionName }} · {{ item.databaseRollbackStrategy }}</a-select-option></a-select>
        <a-select v-model:value="rollbackForm.databaseStrategy"><a-select-option value="FORWARD_FIX">FORWARD_FIX</a-select-option><a-select-option value="REVERSIBLE">REVERSIBLE</a-select-option><a-select-option value="UNRECOVERABLE">UNRECOVERABLE（必定阻断）</a-select-option></a-select>
        <a-input v-model:value="rollbackForm.approvalReference" placeholder="回滚审批单号" />
        <a-input v-model:value="rollbackForm.confirmation" :placeholder="expectedRollbackConfirmation" />
        <small>确认短语：<code>{{ expectedRollbackConfirmation || '请先选择发布记录' }}</code></small>
        <a-button danger :loading="busy === 'rollback'" :disabled="!rollbackForm.deploymentId || !rollbackForm.targetReleaseId || !rollbackForm.approvalReference || !rollbackForm.confirmation" @click="prepareRollback">校验并固化回滚点</a-button>
      </section>
    </div>

    <section class="panel-card deployment-history">
      <div class="panel-title"><div><strong>发布与回滚记录</strong><small>环境、摘要、审批、结果、失败原因和回滚证据均从数据库读回。</small></div></div>
      <div class="deployment-history__body">
        <div class="deployment-run-list">
          <button v-for="item in overview?.deployments" :key="item.id" type="button" :class="{ active: selected?.id === item.id }" @click="selectDeployment(item)">
            <span><a-tag :color="statusColor(item.status)">{{ item.deploymentType }}</a-tag><strong>#{{ item.id }} · {{ item.releaseVersion }}</strong></span>
            <small>{{ deploymentOutcome(item) }} · {{ passedDeploymentSteps(item) }}/{{ item.steps.length }} 步 · {{ item.finishedAt }}</small>
          </button>
          <a-empty v-if="!overview?.deployments.length" description="尚无发布执行记录" />
        </div>
        <div v-if="selected" class="deployment-detail">
          <header><div><strong>#{{ selected.id }} · {{ deploymentOutcome(selected) }}</strong><small>审批 {{ selected.approval.reference }} · 账号 {{ selected.approval.approvedByAccountId }} · {{ selected.approval.approvedAt }}</small></div><a-tag :color="statusColor(selected.status)">{{ selected.status }}</a-tag></header>
          <a-alert v-if="selected.failureCode" type="error" show-icon :message="selected.failureCode" :description="selected.failureMessage" />
          <div class="deployment-steps">
            <article v-for="(step, index) in selected.steps" :key="`${step.code}-${index}`" :data-status="step.status">
              <span>{{ step.status === 'PASSED' ? '✓' : '×' }}</span><div><strong>{{ step.name }}</strong><small>{{ step.code }} · {{ step.version }} · {{ step.durationMillis }}ms · health {{ step.health }} · smoke {{ step.realEntrySmoke }}</small><p>{{ step.evidence }}</p></div>
            </article>
          </div>
          <details><summary>回滚点与兼容证据</summary><pre>{{ JSON.stringify(selected.rollbackPoint, null, 2) }}</pre></details>
        </div>
        <a-empty v-else description="选择记录查看逐步证据" />
      </div>
    </section>
  </section>
</template>
