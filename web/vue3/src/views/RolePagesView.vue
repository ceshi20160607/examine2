<template>
  <AdminLayout>
    <h2>角色页面权限 · role {{ roleId }}</h2>
    <div class="toolbar">
      <label>权限级别
        <select v-model="permLevel">
          <option value="1">允许</option>
          <option value="0">拒绝</option>
        </select>
      </label>
      <button type="button" @click="save">保存（覆盖写）</button>
      <button type="button" class="secondary" @click="selectAll">全选</button>
      <button type="button" class="secondary" @click="clearAll">清空</button>
      <button type="button" class="secondary" @click="reload">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="pages.length" class="table">
      <thead>
        <tr>
          <th></th>
          <th>ID</th>
          <th>编码</th>
          <th>名称</th>
          <th>类型</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in pages" :key="p.id" @click="toggle(p.id)" style="cursor:pointer">
          <td class="check">{{ selected[keyOf(p.id)] ? '✓' : '' }}</td>
          <td>{{ p.id }}</td>
          <td>{{ p.pageCode }}</td>
          <td>{{ p.pageName }}</td>
          <td>{{ p.pageType }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无页面</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listPagesByApp } from '../api/pages'
import { listRolePagePerms, setRolePagePerms } from '../api/module'
import { hasId, idToString, uniqueIds } from '../utils/id'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const roleId = computed(() => String(route.params.roleId || ''))
const pages = ref([])
const selected = ref({})
const permLevel = ref('1')
const error = ref('')

function keyOf(id) {
  return idToString(id)
}

function toggle(id) {
  const key = keyOf(id)
  if (!hasId(key)) return
  const next = { ...selected.value }
  if (next[key]) delete next[key]
  else next[key] = true
  selected.value = next
}

function selectAll() {
  selected.value = Object.fromEntries(pages.value.map((p) => keyOf(p.id)).filter(hasId).map((id) => [id, true]))
}

function clearAll() {
  selected.value = {}
}

async function reload() {
  error.value = ''
  try {
    const [pr, permR] = await Promise.all([
      listPagesByApp(appId.value),
      listRolePagePerms(roleId.value)
    ])
    pages.value = pr.data || []
    const perms = permR.data || []
    const sel = {}
    for (const row of perms) {
      const id = keyOf(row?.pageId)
      if (hasId(id)) sel[id] = true
    }
    selected.value = sel
    const first = perms.find((row) => row?.permLevel != null)
    permLevel.value = first ? String(first.permLevel) : '1'
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function save() {
  const level = Number(permLevel.value || '1')
  const pageIds = uniqueIds(Object.keys(selected.value))
  error.value = ''
  try {
    await setRolePagePerms({ roleId: roleId.value, pageIds, permLevel: level })
    notify.success('已保存')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(reload)
</script>

<style scoped>
.check {
  width: 2rem;
  color: #1677ff;
  font-weight: 700;
}
</style>
