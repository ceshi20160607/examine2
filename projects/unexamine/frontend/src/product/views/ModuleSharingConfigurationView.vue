<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import ModuleRangeSharingDrawer from '../components/ModuleRangeSharingDrawer.vue'
import { allowsPermission } from '../permissions'
import { systemContext, systemTokens } from '../session'
import type { RuntimeModuleCatalogItem, RuntimeModuleConfiguration } from '../types'

/** 范围共享属于当前租户的模块管理；目录和动作均取已发布运行版本。 */
const modules = ref<RuntimeModuleCatalogItem[]>([])
const selectedCode = ref('')
const publication = ref<RuntimeModuleConfiguration>()
const drawerOpen = ref(false)
const loading = ref(false)
const error = ref('')
let requestVersion = 0

const manageableModules = computed(() => modules.value.filter(module => module.configurationMode !== 'SHARED_RECORDS'
  && allowsPermission(systemContext.value?.permissions, 'MODULE', module.moduleCode, 'SHARE')))
const selectedModule = computed(() => manageableModules.value.find(module => module.moduleCode === selectedCode.value))

async function load() {
  const token = systemTokens.value?.accessToken
  if (!token) return
  const current = ++requestVersion
  loading.value = true
  error.value = ''
  publication.value = undefined
  drawerOpen.value = false
  try {
    const catalog = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, token)
    if (current !== requestVersion) return
    modules.value = catalog
    if (!manageableModules.value.some(module => module.moduleCode === selectedCode.value)) {
      selectedCode.value = manageableModules.value[0]?.moduleCode || ''
    }
    await loadPublication(current, token)
  } catch (reason) {
    if (current !== requestVersion) return
    modules.value = []
    error.value = reason instanceof ApiError ? reason.message : '业务模块加载失败，请重试。'
  } finally {
    if (current === requestVersion) loading.value = false
  }
}

async function loadPublication(current: number, token: string) {
  const module = selectedModule.value
  if (!module) return
  try {
    const result = await api<RuntimeModuleConfiguration>(
      `/api/runtime/modules/${encodeURIComponent(module.moduleCode)}/configuration`, {}, token)
    if (current === requestVersion && selectedCode.value === module.moduleCode) publication.value = result
  } catch (reason) {
    if (current !== requestVersion) return
    error.value = reason instanceof ApiError ? reason.message : '已发布模块配置加载失败，请重试。'
  }
}

function selectModule(code: string) {
  if (code === selectedCode.value) return
  selectedCode.value = code
  publication.value = undefined
  drawerOpen.value = false
  error.value = ''
  const token = systemTokens.value?.accessToken
  if (token) {
    const current = ++requestVersion
    loading.value = true
    void loadPublication(current, token).finally(() => { if (current === requestVersion) loading.value = false })
  }
}

watch(() => [systemContext.value?.systemId, systemContext.value?.tenantId, systemContext.value?.contextRevision,
  systemTokens.value?.accessToken], () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <section class="panel-card" aria-label="业务模块范围共享配置">
    <div class="section-heading"><div><h2>范围共享</h2><p>选择已发布的业务模块，设置当前工作空间向其他工作空间开放的数据范围。</p></div>
      <a-button :loading="loading" @click="load">刷新模块</a-button></div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-empty v-if="!loading && !manageableModules.length" description="当前没有可管理范围共享的已发布业务模块" />
    <div v-else class="form-grid">
      <a-form-item label="业务模块"><a-select :value="selectedCode || undefined" show-search option-filter-prop="label"
        :options="manageableModules.map(module => ({ value: module.moduleCode, label: `${module.groupName} · ${module.moduleName}` }))"
        @change="selectModule(String($event))" /></a-form-item>
      <a-form-item label="共享配置"><a-button type="primary" :disabled="!selectedModule || !publication || loading" @click="drawerOpen = true">管理范围共享</a-button></a-form-item>
    </div>
    <ModuleRangeSharingDrawer v-if="selectedModule && publication" v-model:open="drawerOpen"
      :module-code="selectedModule.moduleCode" :module-name="selectedModule.moduleName"
      :actions="publication.configuration.actions" :enabled="Boolean(selectedModule && publication)" />
  </section>
</template>
