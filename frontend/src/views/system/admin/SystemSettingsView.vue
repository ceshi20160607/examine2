<script setup lang="ts">
import { message } from 'ant-design-vue'
import { RefreshCw, Save } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { systemAdminApi } from '@/services/admin'
import { useSessionStore } from '@/stores/session'
import type { SystemSettings } from '@/types/admin'

const { isMobile } = useAdminViewport()
const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const canEditDescription = computed(() => session.hasPermission('system.settings.description.edit'))
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const settings = ref<SystemSettings | null>(null)
const form = reactive({ name: '', description: '', tenantMode: 'SINGLE' as 'SINGLE' | 'MULTI', version: '' })

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '系统设置加载失败'
}

function apply(value: SystemSettings) {
  settings.value = value
  Object.assign(form, { name: value.name, description: value.description, tenantMode: value.tenantMode, version: value.version })
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    apply(await systemAdminApi.getSettings(systemId.value))
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!settings.value || !form.name.trim()) return
  saving.value = true
  try {
    apply(await systemAdminApi.updateSettings(systemId.value, {
      name: form.name,
      description: canEditDescription.value ? form.description : settings.value.description,
      tenantMode: form.tenantMode,
      version: form.version,
    }))
    message.success('系统设置已保存')
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="系统设置" description="维护当前系统的基本信息和租户运行模式。">
      <template v-if="!isMobile" #actions><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button><a-button type="primary" :loading="saving" :disabled="!settings" @click="save"><Save :size="16" />保存</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <template v-if="isMobile">
      <AdminMobileNotice />
      <a-spin v-if="loading" />
      <a-descriptions v-else-if="settings" class="mobile-descriptions" :column="1" bordered size="small">
        <a-descriptions-item label="系统名称">{{ settings.name }}</a-descriptions-item>
        <a-descriptions-item label="系统编码">{{ settings.code }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ settings.status }}</a-descriptions-item>
        <a-descriptions-item label="租户模式">{{ settings.tenantMode === 'MULTI' ? '多租户' : '单租户' }}</a-descriptions-item>
        <a-descriptions-item label="系统说明">{{ settings.description || '未填写' }}</a-descriptions-item>
      </a-descriptions>
    </template>
    <div v-else class="settings-form-region">
      <a-spin :spinning="loading">
        <a-form v-if="settings" layout="vertical" class="settings-form">
          <div class="settings-grid">
            <a-form-item label="系统名称" required><a-input v-model:value="form.name" :maxlength="64" /></a-form-item>
            <a-form-item label="系统编码"><a-input :value="settings.code" disabled /></a-form-item>
            <a-form-item label="运行状态"><a-input :value="settings.status" disabled /></a-form-item>
            <a-form-item label="默认租户"><a-input :value="settings.defaultTenantId" disabled /></a-form-item>
            <a-form-item label="租户模式"><a-select v-model:value="form.tenantMode"><a-select-option value="SINGLE">单租户</a-select-option><a-select-option value="MULTI">多租户</a-select-option></a-select></a-form-item>
            <a-form-item class="settings-span" label="系统说明"><a-textarea v-model:value="form.description" :disabled="!canEditDescription" :rows="5" :maxlength="1000" /><span v-if="!canEditDescription" class="field-hint">当前权限快照不允许编辑说明字段。</span></a-form-item>
          </div>
        </a-form>
        <a-empty v-else-if="!loading" description="没有可读取的系统设置" />
      </a-spin>
    </div>
  </section>
</template>
