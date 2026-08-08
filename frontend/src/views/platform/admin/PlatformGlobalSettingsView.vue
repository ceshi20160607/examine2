<script setup lang="ts">
import { message } from 'ant-design-vue'
import { RefreshCw, Save } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'

import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { platformSettingsApi } from '@/services/platformSettings'
import type { PlatformGlobalSettings } from '@/types/platformSettings'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const form = reactive<PlatformGlobalSettings>({
  profile: { name: '', description: '' },
  storage: { defaultMode: 'LOCAL', maximumUploadBytes: 20_971_520, retentionDays: 365 },
  security: { sessionIdleMinutes: 30, passwordMinimumLength: 12, requireMfaForAdmins: true },
  quota: { defaultMemberLimit: 1000, defaultModuleLimit: 200, defaultStorageBytes: 107_374_182_400 },
  backup: { enabled: false, retentionDays: 30, intervalHours: 24 },
  release: { maintenanceMode: false, channel: 'STABLE', approvalRequired: true },
  version: '0',
})

function report(cause: unknown) {
  error.value = cause instanceof ApiRequestError ? cause.message : '平台设置服务暂不可用'
}

async function load() {
  loading.value = true
  error.value = ''
  try { Object.assign(form, await platformSettingsApi.get()) } catch (cause) { report(cause) } finally { loading.value = false }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    const result = await platformSettingsApi.update({
      profile: { ...form.profile }, storage: { ...form.storage }, security: { ...form.security },
      quota: { ...form.quota }, backup: { ...form.backup }, release: { ...form.release },
      expectedVersion: form.version,
    })
    Object.assign(form, result)
    message.success('平台全局策略已保存')
  } catch (cause) { report(cause) } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="platform-global-settings">
    <AdminPageHeader title="平台信息与全局策略" description="统一维护平台展示信息以及存储、安全、默认配额、备份和发布策略。连接串、凭据和 SecretRef 不在此处保存。">
      <template #actions><a-button :loading="loading" @click="load"><RefreshCw :size="15" />刷新</a-button><a-button type="primary" :loading="saving" @click="save"><Save :size="15" />保存策略</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-alert type="info" show-icon message="这里保存的是可执行的全局治理策略" description="基础设施地址与秘密仍由部署环境管理；修改采用版本检查，避免覆盖其他管理员的更新。" />
    <a-tabs>
      <a-tab-pane key="profile" tab="平台信息"><div class="settings-grid"><label>平台名称<input v-model="form.profile.name" maxlength="80"></label><label class="wide">平台说明<textarea v-model="form.profile.description" maxlength="500" rows="4" /></label></div></a-tab-pane>
      <a-tab-pane key="storage" tab="存储"><div class="settings-grid"><label>默认模式<select v-model="form.storage.defaultMode"><option value="LOCAL">LOCAL</option><option value="S3">S3</option></select></label><label>单文件上限（字节）<input v-model.number="form.storage.maximumUploadBytes" type="number" min="1048576"></label><label>保留天数<input v-model.number="form.storage.retentionDays" type="number" min="1" max="3650"></label></div></a-tab-pane>
      <a-tab-pane key="security" tab="安全"><div class="settings-grid"><label>会话空闲分钟<input v-model.number="form.security.sessionIdleMinutes" type="number" min="5" max="1440"></label><label>密码最短长度<input v-model.number="form.security.passwordMinimumLength" type="number" min="8" max="128"></label><label class="switch"><input v-model="form.security.requireMfaForAdmins" type="checkbox">管理员强制 MFA</label></div></a-tab-pane>
      <a-tab-pane key="quota" tab="默认配额"><div class="settings-grid"><label>成员上限<input v-model.number="form.quota.defaultMemberLimit" type="number" min="1"></label><label>模块上限<input v-model.number="form.quota.defaultModuleLimit" type="number" min="1"></label><label>存储上限（字节）<input v-model.number="form.quota.defaultStorageBytes" type="number" min="1048576"></label></div></a-tab-pane>
      <a-tab-pane key="backup" tab="备份"><div class="settings-grid"><label class="switch"><input v-model="form.backup.enabled" type="checkbox">启用周期备份策略</label><label>保留天数<input v-model.number="form.backup.retentionDays" type="number" min="1" max="3650"></label><label>间隔小时<input v-model.number="form.backup.intervalHours" type="number" min="1" max="168"></label></div></a-tab-pane>
      <a-tab-pane key="release" tab="发布"><div class="settings-grid"><label class="switch"><input v-model="form.release.maintenanceMode" type="checkbox">维护模式</label><label>发布通道<select v-model="form.release.channel"><option value="STABLE">稳定</option><option value="CANARY">灰度</option></select></label><label class="switch"><input v-model="form.release.approvalRequired" type="checkbox">发布需审批</label></div></a-tab-pane>
    </a-tabs>
    <footer>配置版本 {{ form.version }}<span v-if="form.updatedAt"> · 最近更新 {{ new Date(form.updatedAt).toLocaleString('zh-CN') }}</span></footer>
  </section>
</template>

<style scoped>
.platform-global-settings{display:grid;gap:16px}.settings-grid{display:grid;grid-template-columns:repeat(3,minmax(180px,1fr));gap:18px;padding:18px;border:1px solid #e5e7eb;border-radius:12px;background:#fff}.settings-grid label{display:grid;gap:7px;color:#475569}.settings-grid input,.settings-grid select,.settings-grid textarea{border:1px solid #d9d9d9;border-radius:7px;padding:8px;background:#fff}.settings-grid .wide{grid-column:1/-1}.settings-grid .switch{display:flex;align-items:center;gap:9px}.platform-global-settings footer{color:#64748b}@media(max-width:760px){.settings-grid{grid-template-columns:1fr}}
</style>
