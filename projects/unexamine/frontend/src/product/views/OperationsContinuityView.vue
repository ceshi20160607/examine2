<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, api } from '../api'
import { backupConfirmation, restoreDrillConfirmation, upgradeConfirmation, upgradeOutcome, verifiedBackup } from '../operations-continuity'
import { systemTokens } from '../session'
import type { OperationsBackup, OperationsContinuityOverview, OperationsDeploymentOverview, OperationsRelease, OperationsRestoreDrill, OperationsUpgrade, OperationsUpgradePreflight } from '../types'

const basePath = '/api/admin/system/operations/continuity'
const token = computed(() => systemTokens.value?.accessToken)
const overview = ref<OperationsContinuityOverview>()
const releases = ref<OperationsRelease[]>([])
const preflight = ref<OperationsUpgradePreflight>()
const selectedUpgrade = ref<OperationsUpgrade>()
const selectedManifest = ref<Record<string, unknown>>()
const loading = ref(false)
const busy = ref('')
const error = ref('')
const success = ref('')
const backupForm = reactive({ sourceReleaseId: undefined as number | undefined, consistencyPoint: '', encryptionKeyReference: 'kms://unexamine/backup/current', retentionDays: 30, requiredBytes: 1, availableBytes: 1, confirmation: '', artifactJson: '[]' })
const drillForm = reactive({ backupId: undefined as number | undefined, environmentCode: 'restore-drill', confirmation: '', verificationJson: '' })
const upgradeForm = reactive({ releaseId: undefined as number | undefined, backupId: undefined as number | undefined, approvalReference: '', confirmation: '', mappingJson: '{\n  "module": "module-current",\n  "flow": "flow-current",\n  "print": "print-current",\n  "permission": "permission-current"\n}' })

const sourceRelease = computed(() => releases.value.find(item => item.id === upgradeForm.releaseId))
const selectedBackup = computed(() => overview.value?.backups.find(item => item.id === upgradeForm.backupId))
const expectedBackup = computed(() => backupForm.sourceReleaseId ? backupConfirmation(backupForm.sourceReleaseId) : '')
const expectedDrill = computed(() => drillForm.backupId ? restoreDrillConfirmation(drillForm.backupId, drillForm.environmentCode) : '')
const expectedUpgrade = computed(() => upgradeForm.releaseId && upgradeForm.backupId ? upgradeConfirmation(upgradeForm.releaseId, upgradeForm.backupId) : '')

function explain(cause: unknown, fallback: string) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}${cause.traceId ? ` · requestId ${cause.traceId}` : ''}）` : fallback
}

async function load() {
  if (!token.value) return
  loading.value = true
  try {
    const [continuity, deployment] = await Promise.all([
      api<OperationsContinuityOverview>(basePath, {}, token.value),
      api<OperationsDeploymentOverview>('/api/admin/system/operations/deployments', {}, token.value),
    ])
    overview.value = continuity
    releases.value = deployment.releases
    const release = deployment.releases[0]
    const backup = continuity.backups.find(verifiedBackup) ?? continuity.backups[0]
    if (release && !backupForm.sourceReleaseId) backupForm.sourceReleaseId = release.id
    if (release && !upgradeForm.releaseId) upgradeForm.releaseId = release.id
    if (backup && !drillForm.backupId) drillForm.backupId = backup.id
    if (backup && !upgradeForm.backupId) upgradeForm.backupId = backup.id
    if (!selectedUpgrade.value && continuity.upgrades[0]) selectedUpgrade.value = continuity.upgrades[0]
  } catch (cause) { error.value = explain(cause, '备份恢复与升级数据加载失败') } finally { loading.value = false }
}

function artifacts() {
  const value = JSON.parse(backupForm.artifactJson) as unknown
  if (!Array.isArray(value) || value.length !== 4) throw new Error('artifact manifest must contain four items')
  return value
}

function mapping() {
  const value = JSON.parse(upgradeForm.mappingJson) as unknown
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('mapping must be an object')
  return value
}

function restoreVerification() {
  const value = JSON.parse(drillForm.verificationJson) as unknown
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('restore verification must be an object')
  return value
}

async function createBackup() {
  if (!token.value || !backupForm.sourceReleaseId) return
  busy.value = 'backup'; error.value = ''; success.value = ''
  try {
    const created = await api<OperationsBackup>(`${basePath}/backups`, { method: 'POST', body: JSON.stringify({
      sourceReleaseId: backupForm.sourceReleaseId, backupType: 'FULL', consistencyPoint: backupForm.consistencyPoint,
      encryptionKeyReference: backupForm.encryptionKeyReference, retentionDays: backupForm.retentionDays,
      requiredBytes: backupForm.requiredBytes, availableBytes: backupForm.availableBytes,
      confirmation: backupForm.confirmation, items: artifacts(),
    }) }, token.value)
    drillForm.backupId = created.id; upgradeForm.backupId = created.id
    success.value = `备份 #${created.id} 已验证并读回：${created.items.length}/4 类制品，清单 ${created.manifestHash.slice(0, 12)}…。`
    await load()
  } catch (cause) { error.value = explain(cause, '备份清单登记失败，请检查脚本输出 JSON') } finally { busy.value = '' }
}

async function downloadManifest(item: OperationsBackup) {
  if (!token.value) return
  busy.value = `manifest-${item.id}`
  try {
    selectedManifest.value = await api<Record<string, unknown>>(`${basePath}/backups/${item.id}/manifest`, {}, token.value)
    success.value = `备份 #${item.id} 清单已按下载权限读回；其中不包含密钥明文。`
  } catch (cause) { error.value = explain(cause, '备份清单读取失败') } finally { busy.value = '' }
}

async function runDrill() {
  if (!token.value || !drillForm.backupId) return
  busy.value = 'drill'; error.value = ''; success.value = ''
  try {
    const drill = await api<OperationsRestoreDrill>(`${basePath}/backups/${drillForm.backupId}/restore-drills`, {
      method: 'POST', body: JSON.stringify({ environmentCode: drillForm.environmentCode, confirmation: drillForm.confirmation, verification: restoreVerification() }),
    }, token.value)
    success.value = `恢复演练 #${drill.id} 已通过：数据库、文件引用、配置与密钥引用均可读。`
    await load()
  } catch (cause) { error.value = explain(cause, '恢复演练被阻断') } finally { busy.value = '' }
}

function upgradePayload() {
  if (!sourceRelease.value || !overview.value || !upgradeForm.backupId) throw new Error('missing upgrade selection')
  return { releaseId: sourceRelease.value.id, backupId: upgradeForm.backupId,
    sourceDatabaseVersion: sourceRelease.value.databaseVersion, targetDatabaseVersion: overview.value.runtimeDatabaseVersion,
    sourceConfigVersion: sourceRelease.value.configVersion, targetConfigVersion: overview.value.runtimeConfigVersion,
    approvalReference: upgradeForm.approvalReference, confirmation: upgradeForm.confirmation,
    configurationMapping: mapping() }
}

async function runPreflight() {
  if (!token.value) return
  busy.value = 'preflight'; error.value = ''; success.value = ''
  try {
    preflight.value = await api<OperationsUpgradePreflight>(`${basePath}/upgrades/preflight`, {
      method: 'POST', body: JSON.stringify(upgradePayload()),
    }, token.value)
    success.value = preflight.value.ready ? '升级预检全部通过，可以执行。' : '升级预检发现阻断项，尚未创建升级任务。'
  } catch (cause) { error.value = explain(cause, '升级预检失败') } finally { busy.value = '' }
}

async function executeUpgrade() {
  if (!token.value) return
  busy.value = 'upgrade'; error.value = ''; success.value = ''
  try {
    selectedUpgrade.value = await api<OperationsUpgrade>(`${basePath}/upgrades`, {
      method: 'POST', body: JSON.stringify(upgradePayload()),
    }, token.value)
    success.value = selectedUpgrade.value.status === 'SUCCESS'
      ? `升级 #${selectedUpgrade.value.id} 已完成并读回 ${selectedUpgrade.value.steps.length} 个步骤。`
      : `升级 #${selectedUpgrade.value.id} 中途失败，系统已保存明确可恢复状态。`
    await load()
  } catch (cause) { error.value = explain(cause, '升级执行被阻断') } finally { busy.value = '' }
}

function color(status?: string) { return status === 'VERIFIED' || status === 'PASSED' || status === 'SUCCESS' ? 'green' : status === 'FAILED' || status === 'RECOVERABLE_FAILED' ? 'red' : 'blue' }

onMounted(load)
</script>

<template>
  <section class="operations-continuity" :class="{ 'is-loading': loading }">
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />
    <div class="panel-card continuity-hero"><div><p class="eyebrow">一致性备份 · 隔离恢复 · 版本化升级</p><h2>备份、恢复演练与升级</h2><p>数据库、文件、配置和密钥引用必须来自同一时间点；失败升级保持可恢复，不开放混合版本。</p></div><a-tag color="blue">DB V{{ overview?.runtimeDatabaseVersion || '—' }} · {{ overview?.runtimeConfigVersion || '—' }}</a-tag></div>

    <div class="continuity-workbench">
      <section class="panel-card continuity-form"><div class="panel-title"><div><strong>1. 登记脚本生成的四类备份</strong><small>页面只接收真实制品 URI、大小和 SHA-256；密钥仅保存引用。</small></div></div>
        <a-select v-model:value="backupForm.sourceReleaseId" placeholder="源发布"><a-select-option v-for="item in releases" :key="item.id" :value="item.id">#{{ item.id }} · {{ item.versionName }} · V{{ item.databaseVersion }} · {{ item.configVersion }}</a-select-option></a-select>
        <a-input v-model:value="backupForm.consistencyPoint" placeholder="脚本一致性时间点（带时区 ISO-8601）" />
        <a-input v-model:value="backupForm.encryptionKeyReference" placeholder="KMS/密钥引用" />
        <div class="continuity-inline"><a-input-number v-model:value="backupForm.requiredBytes" :min="1" addon-before="所需 bytes" /><a-input-number v-model:value="backupForm.availableBytes" :min="0" addon-before="可用 bytes" /><a-input-number v-model:value="backupForm.retentionDays" :min="1" addon-before="保留天" /></div>
        <a-textarea v-model:value="backupForm.artifactJson" :rows="7" placeholder="粘贴 backup.ps1 输出的 items JSON（恰好四项）" />
        <a-input v-model:value="backupForm.confirmation" :placeholder="expectedBackup" /><small>确认词：<code>{{ expectedBackup || '请选择源发布' }}</code></small>
        <a-button type="primary" :loading="busy === 'backup'" :disabled="!backupForm.sourceReleaseId || !backupForm.consistencyPoint || !backupForm.confirmation" @click="createBackup">校验并持久化备份</a-button>
      </section>

      <section class="panel-card continuity-form"><div class="panel-title"><div><strong>2. 隔离恢复演练</strong><small>不能在当前运行环境执行；四类制品缺一或校验失败都会阻断。</small></div></div>
        <a-select v-model:value="drillForm.backupId" placeholder="已验证备份"><a-select-option v-for="item in overview?.backups" :key="item.id" :value="item.id">#{{ item.id }} · {{ item.status }} · {{ item.consistencyPoint }}</a-select-option></a-select>
        <a-input v-model:value="drillForm.environmentCode" placeholder="隔离环境编码" />
        <a-textarea v-model:value="drillForm.verificationJson" :rows="7" placeholder="粘贴 restore-drill.ps1 输出的 verification.json" />
        <a-input v-model:value="drillForm.confirmation" :placeholder="expectedDrill" /><small>确认词：<code>{{ expectedDrill || '请选择备份' }}</code></small>
        <a-button type="primary" :loading="busy === 'drill'" :disabled="!drillForm.backupId || !drillForm.confirmation || !drillForm.verificationJson" @click="runDrill">校验物理演练证据并读回</a-button>
        <div class="continuity-drills"><article v-for="item in overview?.restoreDrills.slice(0, 4)" :key="item.id"><a-tag :color="color(item.status)">{{ item.status }}</a-tag><strong>#{{ item.id }} · backup #{{ item.backupId }}</strong><small>{{ item.environmentCode }} · {{ item.finishedAt }}</small><details><summary>四类恢复校验结果</summary><pre>{{ JSON.stringify(item.verification, null, 2) }}</pre></details></article></div>
      </section>

      <section class="panel-card continuity-form"><div class="panel-title"><div><strong>3. 预检并执行版本升级</strong><small>备份、演练、源/目标版本、映射和审批全部通过才开始。</small></div></div>
        <a-select v-model:value="upgradeForm.releaseId"><a-select-option v-for="item in releases" :key="item.id" :value="item.id">源 #{{ item.id }} · DB {{ item.databaseVersion }} · {{ item.configVersion }}</a-select-option></a-select>
        <a-select v-model:value="upgradeForm.backupId"><a-select-option v-for="item in overview?.backups" :key="item.id" :value="item.id">backup #{{ item.id }} · {{ item.status }}</a-select-option></a-select>
        <a-textarea v-model:value="upgradeForm.mappingJson" :rows="5" />
        <a-input v-model:value="upgradeForm.approvalReference" placeholder="升级审批单号" />
        <a-input v-model:value="upgradeForm.confirmation" :placeholder="expectedUpgrade" /><small>确认词：<code>{{ expectedUpgrade || '请选择发布与备份' }}</code></small>
        <div class="continuity-actions"><a-button :loading="busy === 'preflight'" @click="runPreflight">只运行预检</a-button><a-button type="primary" :loading="busy === 'upgrade'" :disabled="!preflight?.ready" @click="executeUpgrade">执行升级</a-button></div>
        <div v-if="preflight" class="continuity-checks"><article v-for="item in preflight.checks" :key="item.code"><a-tag :color="color(item.status)">{{ item.status }}</a-tag><strong>{{ item.name }}</strong><small>{{ item.evidence }}</small></article></div>
      </section>
    </div>

    <section class="panel-card continuity-history"><div class="panel-title"><div><strong>备份清单</strong><small>一致性时间点、范围、校验值、加密位置和恢复结果均可读回。</small></div></div>
      <div class="continuity-backups"><article v-for="item in overview?.backups" :key="item.id"><header><span><a-tag :color="color(item.status)">{{ item.status }}</a-tag><strong>#{{ item.id }} · release #{{ item.sourceReleaseId }}</strong></span><a-button size="small" :loading="busy === `manifest-${item.id}`" @click="downloadManifest(item)">按下载权限读取清单</a-button></header><small>{{ item.consistencyPoint }} · 保留至 {{ item.retentionUntil }} · {{ item.encryptionKeyReference }}</small><div><span v-for="artifact in item.items" :key="artifact.id">{{ artifact.itemType }} · {{ artifact.sizeBytes }} B · {{ artifact.sha256.slice(0, 10) }}…</span></div></article></div>
      <details v-if="selectedManifest"><summary>已授权清单读回</summary><pre>{{ JSON.stringify(selectedManifest, null, 2) }}</pre></details>
    </section>

    <section class="panel-card continuity-history"><div class="panel-title"><div><strong>升级与恢复状态</strong><small>成功、已回滚和待处理步骤不能互相混淆。</small></div></div>
      <div class="continuity-upgrades"><button v-for="item in overview?.upgrades" :key="item.id" type="button" :class="{ active: selectedUpgrade?.id === item.id }" @click="selectedUpgrade = item"><a-tag :color="color(item.status)">{{ item.status }}</a-tag><strong>#{{ item.id }} · {{ upgradeOutcome(item) }}</strong><small>release #{{ item.releaseId }} · backup #{{ item.backupId }} · {{ item.finishedAt }}</small></button></div>
      <div v-if="selectedUpgrade" class="continuity-steps"><article v-for="step in selectedUpgrade.steps" :key="step.stepNumber" :data-status="step.status"><span>{{ step.stepNumber }}</span><div><strong>{{ step.stepType }}</strong><small>{{ step.sourceVersion }} → {{ step.targetVersion }} · {{ step.status }}</small><p v-if="step.errorMessage">{{ step.errorMessage }}</p></div></article><details><summary>影响报告与回滚点</summary><pre>{{ JSON.stringify({ impactReport: selectedUpgrade.impactReport, rollbackPoint: selectedUpgrade.rollbackPoint }, null, 2) }}</pre></details></div>
    </section>
  </section>
</template>
