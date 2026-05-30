<template>
  <AdminLayout>
    <h2>角色菜单权限 · role {{ roleId }}</h2>
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
    <ul class="menu-list">
      <li v-for="m in menusFlat" :key="m.id">
        <button type="button" :class="{ on: selected[keyOf(m.id)] }" @click="toggle(m.id)">
          <span class="check">{{ selected[keyOf(m.id)] ? '✓' : '' }}</span>{{ m._title }}
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
import { hasId, idToString, uniqueIds } from '../utils/id'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const roleId = computed(() => String(route.params.roleId || ''))
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
  selected.value = Object.fromEntries(menusFlat.value.map((m) => keyOf(m.id)).filter(hasId).map((id) => [id, true]))
}

function clearAll() {
  selected.value = {}
}

async function reload() {
  error.value = ''
  try {
    const [mr, pr] = await Promise.all([
      listRbacMenus(appId.value),
      listRoleMenuPerms(roleId.value)
    ])
    menus.value = mr.data || []
    const perms = pr.data || []
    const sel = {}
    for (const p of perms) {
      const id = keyOf(p?.menuId)
      if (hasId(id)) sel[id] = true
    }
    selected.value = sel
    const first = perms.find((p) => p?.permLevel != null)
    permLevel.value = first ? String(first.permLevel) : '1'
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function save() {
  const level = Number(permLevel.value || '1')
  const menuIds = uniqueIds(Object.keys(selected.value))
  error.value = ''
  try {
    await setRoleMenuPerms({ roleId: roleId.value, menuIds, permLevel: level })
    notify.success('已保存')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(reload)
</script>

<style scoped>
.menu-list { list-style: none; padding: 0; }
.menu-list li { margin-bottom: 0.35rem; display: flex; align-items: center; gap: 0.5rem; }
.menu-list button {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}
.menu-list button.on { border-color: #1677ff; background: #e6f4ff; }
.check { display: inline-block; width: 1rem; color: #1677ff; font-weight: 700; }
</style>
