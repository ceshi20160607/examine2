<template>
  <AdminLayout>
    <div class="page-head">
      <div>
        <h2>流程模板版本</h2>
        <p class="muted">tempId={{ tempId }}。新建版本后可进入设计器配置节点并发布。</p>
      </div>
      <div class="toolbar">
        <button type="button" @click="createVersion">新建版本</button>
        <button type="button" class="secondary" @click="load">刷新</button>
        <router-link class="btn secondary" to="/flow/temps">返回列表</router-link>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <section>
      <table v-if="vers.length" class="table">
        <thead>
          <tr><th>ID</th><th>版本号</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="v in vers" :key="v.id" :class="{ selected: String(v.id) === String(tempVerId) }">
            <td>{{ v.id }}</td>
            <td>{{ v.verNo }}</td>
            <td>{{ statusText(v.publishStatus) }}</td>
            <td class="actions">
              <router-link class="btn" :to="`/flow/temps/${tempId}/versions/${v.id}/designer`">设计器</router-link>
              <button type="button" class="secondary" @click="selectVer(v.id)">JSON</button>
              <button type="button" class="secondary" @click="publish(v.id)">发布</button>
              <button type="button" class="danger" @click="removeVersion(v)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty">
        暂无版本。点击“新建版本”会创建一个包含开始、审批、结束节点的默认流程图。
      </p>
    </section>

    <section v-if="tempVerId" class="json-section">
      <div class="json-section__head">
        <h3>版本 JSON #{{ tempVerId }}</h3>
        <div class="toolbar">
          <button type="button" class="secondary" @click="addNode">新增节点(JSON)</button>
          <button type="button" class="secondary" @click="addLine">新增连线(JSON)</button>
        </div>
      </div>
      <div class="json-grid">
        <div>
          <h4>节点</h4>
          <pre class="json-pre">{{ JSON.stringify(nodes, null, 2) }}</pre>
        </div>
        <div>
          <h4>连线</h4>
          <pre class="json-pre">{{ JSON.stringify(lines, null, 2) }}</pre>
        </div>
      </div>
    </section>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import {
  deleteTempVers,
  loadGraphDesigner,
  pageTempVers,
  publishTempVer,
  upsertTempVer,
  upsertTempVerLine,
  upsertTempVerNode
} from '../api/flow.js'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const tempId = computed(() => String(route.params.tempId || ''))
const tempVerId = ref('')
const vers = ref([])
const nodes = ref([])
const lines = ref([])
const error = ref('')

function defaultGraphJson() {
  return JSON.stringify({
    nodes: [
      { id: 'start', type: 'start', name: '开始' },
      { id: 'approve1', type: 'approve', name: '审批' },
      { id: 'end', type: 'end', name: '结束' }
    ],
    edges: [
      { from: 'start', to: 'approve1', priority: 1 },
      { from: 'approve1', to: 'end', priority: 1 }
    ],
    config: {
      exception_policy: { mode: 'fallback_admin', admin_plat_id: 0 }
    }
  })
}

function statusText(status) {
  if (status === 2) return '已发布'
  if (status === 3) return '已废弃'
  return '草稿'
}

async function load() {
  error.value = ''
  try {
    const r = await pageTempVers(tempId.value)
    vers.value = r.data?.list || r.data?.records || r.data || []
    if (tempVerId.value && !vers.value.some((v) => String(v.id) === String(tempVerId.value))) {
      tempVerId.value = ''
      nodes.value = []
      lines.value = []
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function createVersion() {
  const verNoText = await promptText('版本号', {
    message: '可留空，系统会自动使用当前最大版本号 + 1。',
    defaultValue: ''
  })
  if (verNoText === null) return
  const verNo = String(verNoText).trim() ? Number(verNoText) : undefined
  if (verNo !== undefined && (!Number.isInteger(verNo) || verNo <= 0)) {
    error.value = '版本号必须是正整数'
    return
  }
  error.value = ''
  try {
    const r = await upsertTempVer({
      tempId: tempId.value,
      verNo,
      publishStatus: 1,
      graphJson: defaultGraphJson(),
      formJson: null
    })
    notify.success('版本已创建')
    await load()
    if (r.data?.id) {
      await selectVer(r.data.id)
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function selectVer(id) {
  tempVerId.value = id
  error.value = ''
  try {
    const r = await loadGraphDesigner(id)
    nodes.value = r.data?.nodes || []
    lines.value = r.data?.edges || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function publish(id) {
  error.value = ''
  try {
    await publishTempVer(id)
    notify.success('已发布')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeVersion(v) {
  if (!(await confirmDialog(`删除版本 #${v.id}?`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteTempVers([v.id])
    notify.success('版本已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addNode() {
  const raw = await promptText('节点 JSON', {
    defaultValue: '{"nodeKey":"approve2","nodeName":"二级审批","nodeType":"approve"}',
    multiline: true
  })
  if (!raw) return
  error.value = ''
  try {
    await upsertTempVerNode({ ...JSON.parse(raw), tempVerId: tempVerId.value })
    await selectVer(tempVerId.value)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addLine() {
  const raw = await promptText('连线 JSON', {
    defaultValue: '{"fromNodeKey":"approve1","toNodeKey":"approve2","priority":1}',
    multiline: true
  })
  if (!raw) return
  error.value = ''
  try {
    await upsertTempVerLine({ ...JSON.parse(raw), tempVerId: tempVerId.value })
    await selectVer(tempVerId.value)
  } catch (e) {
    error.value = e?.message || String(e)
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
.actions {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}
.btn,
.actions button {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0.35rem 0.65rem;
  border-radius: 7px;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
}
.btn.secondary,
.actions button.secondary {
  background: #fff;
  color: var(--color-text);
  border-color: var(--color-border-strong);
}
.actions button.danger {
  background: var(--color-danger);
  border-color: var(--color-danger);
}
.table tr.selected td {
  background: var(--color-primary-soft);
}
.empty {
  padding: 1rem;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-muted);
}
.json-section {
  margin-top: 1.2rem;
}
.json-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}
.json-pre {
  background: #fff;
  border: 1px solid var(--color-border);
  padding: 0.75rem;
  border-radius: 8px;
  font-size: 0.8rem;
  overflow: auto;
  max-height: 280px;
}

@media (max-width: 820px) {
  .page-head,
  .json-section__head {
    display: block;
  }
  .json-grid {
    grid-template-columns: 1fr;
  }
}
</style>
