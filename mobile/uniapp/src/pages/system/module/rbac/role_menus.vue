<template>
  <view style="padding: 16px">
    <uni-card :title="`角色菜单权限（roleId=${roleId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap; align-items: center;">
        <view style="color:#666">permLevel</view>
        <uni-easyinput v-model="permLevelText" placeholder="1" style="width: 120px" />
        <uni-button type="primary" :disabled="saving" @click="save">保存（覆盖写）</uni-button>
        <uni-button :disabled="loading" @click="reload">刷新</uni-button>
      </view>
      <view style="margin-top: 8px; color:#666">
        点击菜单行切换选中；保存会调用 `/v1/system/module/rbac/roles/menu-perms/set`。
      </view>
    </uni-card>

    <uni-card title="菜单（点击切换）" style="margin-top: 12px">
      <uni-list v-if="menus.length">
        <uni-list-item
          v-for="m in menus"
          :key="m.id"
          :title="`${isSelected(m.id) ? '✓ ' : ''}${m.menuName || ('Menu#' + m.id)}`"
          :note="m.permKey || m.apiPattern || ''"
          clickable
          @click="toggle(m.id)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无菜单（请先在 RBAC 页创建菜单）</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type MenuRow = { id: number; parentId?: number; menuName?: string; permKey?: string; apiPattern?: string; pageId?: number }

const appId = ref<number>(0)
const roleId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)

const menus = ref<MenuRow[]>([])
const selected = ref<Record<number, boolean>>({})
const permLevelText = ref('1')

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  roleId.value = Number((opts as any)?.roleId || 0) || 0
})

function isSelected(id: number) {
  return !!selected.value[id]
}

function toggle(id: number) {
  const next = { ...selected.value }
  if (next[id]) delete next[id]
  else next[id] = true
  selected.value = next
}

async function loadMenus() {
  if (!appId.value) return
  loading.value = true
  try {
    const r = await httpGet<MenuRow[]>(`/v1/system/module/rbac/apps/${appId.value}/menus`)
    menus.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadCurrentPerms() {
  if (!roleId.value) return
  const r = await httpGet<any[]>(`/v1/system/module/rbac/roles/${roleId.value}/menu-perms`)
  const perms = (r.data || []) as Array<{ menuId?: number; permLevel?: number }>
  const next: Record<number, boolean> = {}
  let anyLevel0 = false
  let anyLevel1 = false
  for (const p of perms) {
    const mid = Number(p?.menuId || 0)
    if (!mid) continue
    if (p?.permLevel === 0) anyLevel0 = true
    if (p?.permLevel === 1 || p?.permLevel == null) anyLevel1 = true
    // UI 只回显允许列表（permLevel=1）
    if (p?.permLevel === 1) next[mid] = true
  }
  selected.value = next
  // 默认：如果历史里有 0 级别，仍用 1（UI 当前只支持覆盖写一个级别）
  if (anyLevel1 && !anyLevel0) permLevelText.value = '1'
}

async function reload() {
  selected.value = {}
  await Promise.all([loadMenus(), loadCurrentPerms()])
}

async function save() {
  if (!roleId.value) return
  const permLevel = Number((permLevelText.value || '1').trim() || '1')
  if (!permLevel || Number.isNaN(permLevel)) {
    uni.showToast({ title: 'permLevel 非法', icon: 'none' })
    return
  }
  const menuIds = Object.keys(selected.value)
    .map((k) => Number(k))
    .filter((id) => !!id && !Number.isNaN(id))

  saving.value = true
  try {
    await httpPost('/v1/system/module/rbac/roles/menu-perms/set', {
      roleId: roleId.value,
      menuIds,
      permLevel
    })
    uni.showToast({ title: '已保存', icon: 'success' })
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!appId.value || !roleId.value) {
    uni.showToast({ title: '缺少 appId/roleId', icon: 'none' })
    return
  }
  reload()
})
</script>
