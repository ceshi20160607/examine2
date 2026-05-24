<template>
  <AdminLayout>
    <h2>流程模板版本 · tempId={{ tempId }}</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <router-link class="btn secondary" to="/flow/temps">返回列表</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <section v-if="vers.length">
      <h3>版本</h3>
      <table class="table">
        <thead><tr><th>ID</th><th>版本号</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="v in vers" :key="v.id">
            <td>{{ v.id }}</td>
            <td>{{ v.verNo }}</td>
            <td>{{ v.status }}</td>
            <td>
              <router-link
                class="btn"
                :to="`/flow/temps/${tempId}/versions/${v.id}/designer`"
              >可视化设计</router-link>
              <button type="button" class="secondary" @click="selectVer(v.id)">JSON</button>
              <button type="button" class="secondary" @click="publish(v.id)">发布</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
    <section v-if="tempVerId">
      <h3>节点 tempVerId={{ tempVerId }}</h3>
      <pre class="json-pre">{{ JSON.stringify(nodes, null, 2) }}</pre>
      <button type="button" @click="addNode">新增节点(JSON)</button>
      <h3>连线</h3>
      <pre class="json-pre">{{ JSON.stringify(lines, null, 2) }}</pre>
      <button type="button" @click="addLine">新增连线(JSON)</button>
    </section>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import {
  pageTempVerLines,
  pageTempVerNodes,
  pageTempVers,
  publishTempVer,
  upsertTempVerLine,
  upsertTempVerNode
} from '../api/flow.js'

const route = useRoute()
const tempId = computed(() => String(route.params.tempId || ''))
const tempVerId = ref('')
const vers = ref([])
const nodes = ref([])
const lines = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await pageTempVers(tempId.value)
    vers.value = r.data?.list || r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function selectVer(id) {
  tempVerId.value = id
  const [nr, lr] = await Promise.all([pageTempVerNodes(id), pageTempVerLines(id)])
  nodes.value = nr.data?.list || nr.data || []
  lines.value = lr.data?.list || lr.data || []
}

async function publish(id) {
  await publishTempVer(id)
  alert('已发布')
  load()
}

async function addNode() {
  const raw = prompt('节点 JSON', '{"nodeKey":"n1","nodeName":"开始","type":"start"}')
  if (!raw) return
  await upsertTempVerNode({ ...JSON.parse(raw), tempVerId: tempVerId.value })
  selectVer(tempVerId.value)
}

async function addLine() {
  const raw = prompt('连线 JSON', '{"fromNodeKey":"n1","toNodeKey":"n2"}')
  if (!raw) return
  await upsertTempVerLine({ ...JSON.parse(raw), tempVerId: tempVerId.value })
  selectVer(tempVerId.value)
}

onMounted(load)
</script>

<style scoped>
.btn {
  display: inline-block;
  margin-right: 0.35rem;
  padding: 0.35rem 0.65rem;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
  text-decoration: none;
  font-size: 0.88rem;
}
.table button.secondary {
  margin-left: 0.25rem;
}
.json-pre {
  background: #f9fafb;
  padding: 0.75rem;
  border-radius: 6px;
  font-size: 0.8rem;
  overflow: auto;
  max-height: 240px;
}
</style>
