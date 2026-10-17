<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductPage from '../components/ProductPage.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { systemTokens } from '../session'
import { systemContext } from '../session'
import { allowsPermission } from '../permissions'
import type { RuntimeModuleCatalogItem, RuntimeModuleConfiguration } from '../types'
import DashboardRuntimeView from './DashboardRuntimeView.vue'

const route = useRoute()
const router = useRouter()
const token = computed(() => systemTokens.value?.accessToken)
const modules = ref<RuntimeModuleCatalogItem[]>([])
const loading = ref(false)
const error = ref('')
let modulesRequest = 0
const selectedCode = computed(() => {
  const requested = String(route.query.module || '')
  return requested ? modules.value.find(item => item.moduleCode === requested)?.moduleCode || ''
    : modules.value[0]?.moduleCode || ''
})
const selected = computed(() => modules.value.find(item => item.moduleCode === selectedCode.value))

/** 模块目录来自当前成员授权，切换模块时不沿用上一个模块的首页数据。 */
async function loadModules() {
  if (!token.value) return
  const requestId = ++modulesRequest
  const accessToken = token.value
  loading.value = true
  error.value = ''
  try {
    const catalog = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, accessToken)
    const candidates = catalog.filter(item => item.configurationMode !== 'SHARED_RECORDS'
      && (allowsPermission(systemContext.value?.permissions, 'MODULE', item.moduleCode, 'DASHBOARD')
        || allowsPermission(systemContext.value?.permissions, 'MODULE', item.moduleCode, 'LIST')))
    // 目录只列出实际生效的入口；权限缓存和未发布草稿不能让停用模块继续出现在导航中。
    const checks = await Promise.all(candidates.map(async item => {
      try {
        const current = await api<RuntimeModuleConfiguration>(
          `/api/runtime/modules/${encodeURIComponent(item.moduleCode)}/configuration`, {}, accessToken)
        return current.configuration.actions.some(action => action.code === 'DASHBOARD'
          && action.status === 'ACTIVE' && action.location === 'MODULE_ENTRY') ? item : undefined
      } catch { return undefined }
    }))
    if (requestId === modulesRequest) modules.value = checks.filter((item): item is RuntimeModuleCatalogItem => Boolean(item))
  }
  catch (cause) { if (requestId === modulesRequest) error.value = cause instanceof ApiError ? cause.message : '模块列表暂时无法加载。' }
  finally { if (requestId === modulesRequest) loading.value = false }
}

function selectModule(code: string) {
  void router.replace({ path: route.path, query: { ...route.query, workspace: 'module-home', module: code } })
}

function openRecords() {
  if (!selectedCode.value) return
  void router.push({ path: route.path, query: { workspace: 'runtime', module: selectedCode.value } })
}

watch(() => token.value, loadModules)
onMounted(loadModules)
</script>

<template>
  <ProductPage class="module-dashboard-page">
    <ProductPageHeader kicker="业务模块" :title="selected?.moduleName || '模块首页'" description="查看当前模块已发布的业务概览，并继续处理模块记录。">
      <template #actions><a-button :loading="loading" @click="loadModules">刷新模块</a-button></template>
      <template #primary><a-button v-if="selected" type="primary" @click="openRecords">查看业务记录</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-skeleton v-if="loading && !modules.length" active />
    <a-empty v-else-if="!modules.length" description="当前没有已授权的模块首页入口" />
    <a-empty v-else-if="!selectedCode" description="当前模块没有可进入的首页；请从模块动作重新进入" />
    <template v-else>
      <nav class="module-home-nav" aria-label="选择业务模块">
        <button v-for="module in modules" :key="module.moduleId" type="button" :class="{ active: selectedCode === module.moduleCode }" @click="selectModule(module.moduleCode)">
          <strong>{{ module.menuName || module.moduleName }}</strong><small>{{ module.groupName }}</small>
        </button>
      </nav>
      <DashboardRuntimeView :key="selectedCode" context="system" variant="MODULE" :module-code="selectedCode" />
    </template>
  </ProductPage>
</template>

<style scoped>
.module-home-nav { display: flex; flex-wrap: wrap; gap: 10px; margin: 0 0 20px; }
.module-home-nav button { display: grid; gap: 3px; min-width: 120px; padding: 10px 14px; border: 1px solid #dce4f1; border-radius: 10px; background: #fff; text-align: left; cursor: pointer; }
.module-home-nav button.active { border-color: #315efb; background: #eef3ff; }
.module-home-nav small { color: #66758a; }
</style>
