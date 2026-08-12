<script setup lang="ts">
import { FileSpreadsheet, FileText, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import ReportExportDrawer from '@/components/report/ReportExportDrawer.vue'
import { runtimeReportApi } from '@/services/report'
import type { RuntimeReportField, RuntimeReportMetadata, RuntimeReportRow, RuntimeReportRows } from '@/types/report'

const route = useRoute()
const router = useRouter()
const systemId = computed(() => String(route.params.systemId))
const catalog = ref<RuntimeReportMetadata[]>([])
const metadata = ref<RuntimeReportMetadata | null>(null)
const result = ref<RuntimeReportRows | null>(null)
const selectedCode = ref('')
const page = ref(1)
const size = ref(20)
const catalogLoading = ref(false)
const rowsLoading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
const exportOpen = ref(false)
let loadGeneration = 0

const rows = computed(() => result.value?.rows ?? result.value?.items ?? [])
const canPrevious = computed(() => page.value > 1)
const canNext = computed(() => page.value * size.value < (result.value?.total ?? 0))
const pageCount = computed(() => Math.max(1, Math.ceil((result.value?.total ?? 0) / size.value)))

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

async function loadCatalog() {
  const generation = ++loadGeneration
  catalogLoading.value = true
  loaded.value = false
  errorMessage.value = ''
  try {
    const reports = await runtimeReportApi.list(systemId.value)
    if (generation !== loadGeneration) return
    catalog.value = reports
    const routeCode = typeof route.params.reportCode === 'string' ? route.params.reportCode : ''
    selectedCode.value = reports.some(report => report.code === routeCode)
      ? routeCode : reports[0]?.code ?? ''
    if (selectedCode.value) await loadReport(1, generation)
    else {
      metadata.value = null
      result.value = null
      loaded.value = true
    }
  } catch (error) {
    if (generation !== loadGeneration) return
    errorMessage.value = requestError(error, '报表目录暂时不可用')
    metadata.value = null
    result.value = null
    loaded.value = true
  } finally {
    if (generation === loadGeneration) catalogLoading.value = false
  }
}

async function chooseReport() {
  page.value = 1
  exportOpen.value = false
  if (!selectedCode.value) return
  await router.replace({
    name: 'system-reports',
    params: { systemId: systemId.value, reportCode: selectedCode.value },
  })
  await loadReport(1)
}

async function loadReport(nextPage = page.value, parentGeneration?: number) {
  const generation = parentGeneration ?? ++loadGeneration
  if (!selectedCode.value) return
  rowsLoading.value = true
  errorMessage.value = ''
  try {
    const [report, rowsResult] = await Promise.all([
      runtimeReportApi.metadata(systemId.value, selectedCode.value),
      runtimeReportApi.rows(systemId.value, selectedCode.value, nextPage, size.value),
    ])
    if (generation !== loadGeneration) return
    metadata.value = report
    result.value = rowsResult
    page.value = rowsResult.page || nextPage
    loaded.value = true
  } catch (error) {
    if (generation !== loadGeneration) return
    errorMessage.value = requestError(error, '报表运行失败')
    metadata.value = null
    result.value = null
    loaded.value = true
  } finally {
    if (generation === loadGeneration) rowsLoading.value = false
  }
}

async function changeSize() {
  page.value = 1
  await loadReport(1)
}

function cellValue(row: RuntimeReportRow, field: RuntimeReportField) {
  if (Array.isArray(row.values)) {
    const value = row.values.find(item => item.fieldCode === field.fieldCode)
    return value?.displayValue ?? value?.canonicalValue ?? value?.value ?? '—'
  }
  const raw = row.values[field.fieldCode]
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    const value = raw as Record<string, unknown>
    return value.displayValue ?? value.canonicalValue ?? value.value ?? '—'
  }
  return raw ?? '—'
}

onMounted(loadCatalog)
</script>

<template>
  <section class="system-reports-page">
    <header class="runtime-report-heading">
      <div><h1><FileText :size="24" />报表</h1><p>运行已发布报表；字段权限、数据范围和固定筛选在每次查询时重新校验。</p></div>
      <div class="runtime-report-heading-actions"><a-button v-if="metadata" class="runtime-report-export" @click="exportOpen = true"><FileSpreadsheet :size="16" />导出 XLSX</a-button><a-button :loading="catalogLoading || rowsLoading" @click="loadCatalog"><RefreshCw :size="16" />刷新</a-button></div>
    </header>

    <a-alert v-if="errorMessage" class="runtime-report-error" type="error" show-icon message="报表暂时不可用" :description="errorMessage" />

    <section v-if="catalog.length" class="runtime-report-toolbar">
      <label>选择报表<select v-model="selectedCode" class="runtime-report-select" @change="chooseReport"><option v-for="report in catalog" :key="report.id" :value="report.code">{{ report.name }} · {{ report.code }} · v{{ report.versionNumber }}</option></select></label>
      <label>每页<select v-model.number="size" class="runtime-report-size" @change="changeSize"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option><option :value="200">200</option></select></label>
      <span v-if="result?.queryHash" class="query-hash">查询 {{ result.queryHash }}</span>
    </section>

    <a-spin :spinning="catalogLoading || rowsLoading">
      <section v-if="metadata" class="runtime-report-card">
        <header><div><h2>{{ metadata.name }}</h2><p>{{ metadata.description || '暂无描述' }}</p></div><a-tag color="green">v{{ metadata.versionNumber }}</a-tag></header>
        <div class="report-pin-summary"><span>数据源 {{ metadata.dataSourceName }} · {{ metadata.dataSourceCode }}</span><span>数据源版本 v{{ metadata.dataSourceVersionNumber }} · {{ metadata.dataSourceVersionId }}</span><span>模块 {{ metadata.moduleCode }}</span><span>Schema {{ metadata.schemaVersionId }}</span></div>
        <div v-if="rows.length" class="runtime-report-table-wrap">
          <table class="runtime-report-table"><thead><tr><th v-for="field in metadata.fields" :key="field.fieldCode">{{ field.fieldName }}<small>{{ field.fieldCode }} · {{ field.type }}</small></th></tr></thead><tbody><tr v-for="row in rows" :key="row.recordId"><td v-for="field in metadata.fields" :key="field.fieldCode">{{ cellValue(row, field) }}</td></tr></tbody></table>
        </div>
        <a-empty v-else-if="loaded && !rowsLoading && !errorMessage" class="runtime-report-empty" description="当前权限和筛选范围内没有数据" />
        <footer v-if="result" class="runtime-report-pagination"><span>共 {{ result.total }} 条 · 第 {{ page }}/{{ pageCount }} 页</span><div><a-button class="report-page-previous" :disabled="!canPrevious || rowsLoading" @click="loadReport(page - 1)">上一页</a-button><a-button class="report-page-next" :disabled="!canNext || rowsLoading" @click="loadReport(page + 1)">下一页</a-button></div></footer>
      </section>
      <a-empty v-else-if="loaded && !errorMessage" class="runtime-report-catalog-empty" description="当前租户没有可运行的已发布报表" />
      <div v-else-if="catalogLoading || rowsLoading" class="runtime-report-loading" role="status" aria-live="polite">正在加载报表…</div>
    </a-spin>
    <ReportExportDrawer :open="exportOpen" :system-id="systemId" :report-code="metadata?.code ?? selectedCode" :report-name="metadata?.name ?? ''" @close="exportOpen = false" />
  </section>
</template>

<style scoped>
.system-reports-page{display:grid;gap:16px;max-width:1480px;margin:0 auto;padding:28px;min-width:0;background:#f4f6f8}.runtime-report-heading,.runtime-report-heading h1,.runtime-report-heading-actions,.runtime-report-toolbar,.runtime-report-card>header,.runtime-report-pagination,.runtime-report-pagination>div{display:flex;align-items:center}.runtime-report-heading{justify-content:space-between;gap:20px}.runtime-report-heading-actions{gap:8px;flex-wrap:wrap}.runtime-report-heading h1{gap:9px;margin:0}.runtime-report-heading p,.runtime-report-card header p{margin:5px 0 0;color:#667780}.runtime-report-toolbar{gap:16px;padding:13px 16px;border:1px solid #dce5e9;border-radius:9px;background:#fff;min-width:0}.runtime-report-toolbar label{display:flex;align-items:center;gap:7px;color:#52626a}.runtime-report-toolbar select{min-width:170px;max-width:100%;padding:7px 9px;border:1px solid #cbd5da;border-radius:7px;background:#fff}.query-hash{margin-left:auto;color:#738189;font:12px ui-monospace;overflow-wrap:anywhere}.runtime-report-card{display:grid;gap:14px;padding:18px;border:1px solid #dce5e9;border-radius:10px;background:#fff;min-width:0}.runtime-report-card>header{justify-content:space-between;gap:16px}.runtime-report-card h2{margin:0}.report-pin-summary{display:flex;flex-wrap:wrap;gap:8px}.report-pin-summary span{padding:5px 8px;border-radius:6px;background:#f2f6f7;color:#65747b;font-size:12px;overflow-wrap:anywhere}.runtime-report-table-wrap{overflow:auto;border:1px solid #e0e7e9;border-radius:8px}.runtime-report-table{width:100%;min-width:680px;border-collapse:collapse;white-space:nowrap}.runtime-report-table th,.runtime-report-table td{padding:11px 13px;border-bottom:1px solid #e7edef;text-align:left}.runtime-report-table th{background:#f6f9f9;color:#394950;position:sticky;top:0;z-index:1}.runtime-report-table th small{display:block;margin-top:3px;color:#7a888e;font-weight:400}.runtime-report-pagination{justify-content:space-between;gap:14px}.runtime-report-pagination>div{gap:8px}.runtime-report-empty,.runtime-report-catalog-empty,.runtime-report-loading{min-height:260px;display:grid;place-content:center}.runtime-report-loading{color:#738189}
@media(max-width:900px){.system-reports-page{padding:20px}.runtime-report-heading{align-items:flex-start}.runtime-report-toolbar{align-items:stretch;flex-wrap:wrap}.runtime-report-toolbar label{flex:1 1 240px}.runtime-report-toolbar select{flex:1;min-width:0}.query-hash{flex:1 0 100%;margin-left:0}.runtime-report-pagination{align-items:flex-start;flex-direction:column}}
@media(max-width:560px){.system-reports-page{gap:14px;padding:14px}.runtime-report-heading{align-items:stretch;flex-direction:column}.runtime-report-heading-actions{justify-content:flex-start}.runtime-report-toolbar{display:grid;gap:10px;padding:12px}.runtime-report-toolbar label{align-items:stretch;flex-direction:column}.runtime-report-card{padding:14px}.runtime-report-card>header{align-items:flex-start;flex-direction:column}.runtime-report-pagination>div{width:100%}.runtime-report-pagination .ant-btn{flex:1}.runtime-report-table{min-width:600px}}
</style>
