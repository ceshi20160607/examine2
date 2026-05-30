<template>
  <AdminLayout>
    <div class="page-head">
      <div>
        <h2>发起流程</h2>
        <p class="muted">选择已发布模板，填写业务信息后创建审批实例。</p>
      </div>
      <div class="toolbar">
        <button type="button" class="secondary" @click="load">刷新模板</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="start-panel">
      <label>
        <span>流程模板</span>
        <select v-model="selectedCode" @change="syncTitleFromTemplate">
          <option value="">请选择模板</option>
          <option v-for="t in temps" :key="t.id || t.tempCode" :value="t.tempCode">
            {{ t.tempName || t.tempCode }}
          </option>
        </select>
      </label>
      <label>
        <span>流程标题</span>
        <input v-model="form.title" placeholder="例如：费用报销审批" />
      </label>
      <label>
        <span>业务类型</span>
        <input v-model="form.bizType" placeholder="例如：record / order / ui_flow" />
      </label>
      <label>
        <span>业务 ID</span>
        <input v-model="form.bizId" placeholder="留空将自动生成" />
      </label>
      <label class="start-panel__vars">
        <span>变量 JSON</span>
        <textarea v-model="form.varsText" class="json-area" rows="5" placeholder='{"amount": 100}' />
      </label>
      <div class="start-panel__actions">
        <button type="button" :disabled="starting || !selectedCode" @click="startSelected">
          {{ starting ? '发起中...' : '发起流程' }}
        </button>
      </div>
    </section>

    <table v-if="temps.length" class="table">
      <thead><tr><th>编码</th><th>名称</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="t in temps" :key="t.id || t.tempCode" :class="{ selected: t.tempCode === selectedCode }">
          <td>{{ t.tempCode }}</td>
          <td>{{ t.tempName }}</td>
          <td><button type="button" class="secondary" @click="selectTemplate(t)">选择</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无可发起模板</p>

    <pre v-if="resultText" class="pre">{{ resultText }}</pre>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { pageTemps, startInstance } from '../api/flow'
import { notify } from '../utils/notify.js'

const temps = ref([])
const selectedCode = ref('')
const error = ref('')
const resultText = ref('')
const starting = ref(false)
const form = reactive({
  title: '',
  bizType: 'ui_flow',
  bizId: '',
  varsText: '{}'
})

const selectedTemplate = computed(() => temps.value.find((t) => t.tempCode === selectedCode.value) || null)

async function load() {
  error.value = ''
  try {
    const r = await pageTemps(1, 50)
    temps.value = r.data?.list || r.data?.records || r.data || []
    if (!selectedCode.value && temps.value.length) {
      selectTemplate(temps.value[0])
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function selectTemplate(t) {
  selectedCode.value = t?.tempCode || ''
  if (!form.title && t?.tempName) {
    form.title = t.tempName
  }
}

function syncTitleFromTemplate() {
  if (!form.title && selectedTemplate.value?.tempName) {
    form.title = selectedTemplate.value.tempName
  }
}

function parseVars() {
  const raw = (form.varsText || '').trim()
  if (!raw) return {}
  const parsed = JSON.parse(raw)
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('变量 JSON 必须是对象')
  }
  return parsed
}

async function startSelected() {
  if (!selectedCode.value) {
    error.value = '请选择流程模板'
    return
  }
  if (!form.title.trim()) {
    error.value = '流程标题不能为空'
    return
  }
  let vars
  try {
    vars = parseVars()
  } catch (e) {
    error.value = e?.message || '变量 JSON 格式错误'
    return
  }

  error.value = ''
  starting.value = true
  try {
    const r = await startInstance({
      defCode: selectedCode.value,
      title: form.title.trim(),
      bizType: form.bizType.trim() || 'ui_flow',
      bizId: form.bizId.trim() || `ui-${Date.now()}`,
      vars
    })
    resultText.value = JSON.stringify(r.data || null, null, 2)
    notify.success('已发起')
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    starting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.start-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 0.9rem 1rem;
  margin: 1rem 0 1.2rem;
  padding: 1rem;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}
.start-panel label {
  display: grid;
  gap: 0.35rem;
  color: #43524b;
  font-size: 0.86rem;
  font-weight: 700;
}
.start-panel input,
.start-panel select {
  min-height: 38px;
  padding: 0.48rem 0.65rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
  background: #fff;
}
.start-panel__vars {
  grid-column: 1 / -1;
}
.start-panel__actions {
  grid-column: 1 / -1;
}
.start-panel__actions button {
  min-height: 38px;
  padding: 0.5rem 0.9rem;
  border: 1px solid var(--color-primary);
  border-radius: 7px;
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}
.table tr.selected td {
  background: var(--color-primary-soft);
}
.pre {
  margin-top: 1rem;
  background: #fff;
  border: 1px solid var(--color-border);
  padding: 0.75rem;
  border-radius: 8px;
  font-size: 0.85rem;
  overflow-x: auto;
}

@media (max-width: 760px) {
  .page-head {
    display: block;
  }
  .start-panel {
    grid-template-columns: 1fr;
  }
}
</style>
