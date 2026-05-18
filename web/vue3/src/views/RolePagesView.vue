<template>
  <AdminLayout>
    <h2>角色页面权限 · role {{ roleId }}</h2>
    <div class="toolbar">
      <label>permLevel <input v-model="permLevel" style="width:48px" /></label>
      <button type="button" @click="save">保存（覆盖写）</button>
      <button type="button" class="secondary" @click="reload">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="pages.length" class="table">
      <thead><tr><th></th><th>ID</th><th>编码</th><th>名称</th><th>类型</th></tr></thead>
      <tbody>
        <tr v-for="p in pages" :key="p.id" @click="toggle(p.id)" style="cursor:pointer">
          <td>{{ selected[p.id] ? '✓' : '' }}</td>
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

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
const roleId = computed(() => Number(route.params.roleId))
const pages = ref([])
const selected = ref({})
const permLevel = ref('1')
const error = ref('')

function toggle(id) {
  const next = { ...selected.value }
  if (next[id]) delete next[id]
  else next[id] = true
  selected.value = next
}

async function reload() {
  error.value = ''
  try {
    const [pr, permR] = await Promise.all([
      listPagesByApp(appId.value),
      listRolePagePerms(roleId.value)
    ])
    pages.value = pr.data || []
    const sel = {}
    for (const row of permR.data || []) {
      if (row?.pageId && row?.permLevel === 1) sel[Number(row.pageId)] = true
    }
    selected.value = sel
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function save() {
  const level = Number(permLevel.value || '1')
  const pageIds = Object.keys(selected.value).map(Number).filter((n) => n > 0)
  error.value = ''
  try {
    await setRolePagePerms({ roleId: roleId.value, pageIds, permLevel: level })
    alert('已保存')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(reload)
</script>

<style src="./admin-shared.css"></style>
