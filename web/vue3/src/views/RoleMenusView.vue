<template>
  <AdminLayout>
    <h2>角色菜单权限 · role {{ roleId }}</h2>
    <div class="toolbar">
      <label>permLevel <input v-model="permLevel" style="width:48px" /></label>
      <button type="button" @click="save">保存（覆盖写）</button>
      <button type="button" class="secondary" @click="reload">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <ul class="menu-list">
      <li v-for="m in menusFlat" :key="m.id">
        <button type="button" :class="{ on: selected[m.id] }" @click="toggle(m.id)">
          {{ selected[m.id] ? '✓ ' : '' }}{{ m._title }}
        </button>
        <span class="muted">{{ m.pageId ? `pageId=${m.pageId}` : '' }}</span>
      </li>
    </ul>
    <p v-if="!menusFlat.length" class="muted">暂无菜单</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listRbacMenus, listRoleMenuPerms, setRoleMenuPerms } from '../api/module'

const route = useRoute()
const appId = computed(() => Number(route.params.appId))
const roleId = computed(() => Number(route.params.roleId))
const menus = ref([])
const selected = ref({})
const permLevel = ref('1')
const error = ref('')

function flattenTree(nodes, depth = 0, out = []) {
  for (const n of nodes || []) {
    const pad = '  '.repeat(depth)
    out.push({ ...n, _title: pad + (n.menuName || `Menu#${n.id}`) })
    if (n.children?.length) flattenTree(n.children, depth + 1, out)
  }
  return out
}

const menusFlat = computed(() => flattenTree(menus.value))

function toggle(id) {
  const next = { ...selected.value }
  if (next[id]) delete next[id]
  else next[id] = true
  selected.value = next
}

async function reload() {
  error.value = ''
  try {
    const [mr, pr] = await Promise.all([
      listRbacMenus(appId.value),
      listRoleMenuPerms(roleId.value)
    ])
    menus.value = mr.data || []
    const sel = {}
    for (const p of pr.data || []) {
      if (p?.menuId && p?.permLevel === 1) sel[Number(p.menuId)] = true
    }
    selected.value = sel
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function save() {
  const level = Number(permLevel.value || '1')
  const menuIds = Object.keys(selected.value).map(Number).filter((id) => id > 0)
  error.value = ''
  try {
    await setRoleMenuPerms({ roleId: roleId.value, menuIds, permLevel: level })
    alert('已保存')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(reload)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.menu-list { list-style: none; padding: 0; }
.menu-list li { margin-bottom: 0.35rem; display: flex; align-items: center; gap: 0.5rem; }
.menu-list button { text-align: left; padding: 0.4rem 0.6rem; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; cursor: pointer; }
.menu-list button.on { border-color: #1677ff; background: #e6f4ff; }
</style>
