<template>
  <Page :title="`角色菜单权限（roleId=${roleId}）`" subtitle="选择菜单并覆盖写权限级别">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="permLevel">
          <uni-easyinput v-model="permLevelText" placeholder="1" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">保存（覆盖写）</uni-button>
        <uni-button @click="selectAll">全选</uni-button>
        <uni-button @click="clearAll">清空</uni-button>
        <uni-button :disabled="loading" @click="reload">刷新</uni-button>
      </ActionBar>
      <view class="u-subtitle">点击菜单行切换选中；保存会调用 `/v1/system/module/rbac/roles/menu-perms/set`。</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">菜单（树形；点击切换）</view>
      <view style="margin-top: 12px">
        <uni-list v-if="menusFlat.length">
          <uni-list-item
            v-for="m in menusFlat"
            :key="m.id"
            :title="`${isSelected(m.id) ? '✓ ' : ''}${rbacMenuTitleIndented(m)}`"
            :note="rbacMenuNote(m)"
            clickable
            @click="toggle(m.id)"
          />
        </uni-list>
        <EmptyState v-else text="暂无菜单（请先在 RBAC 页创建菜单）" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import {
  flattenRbacMenusTree,
  rbacMenuNote,
  rbacMenuTitleIndented,
  type RbacMenuFlatRow,
  type RbacMenuRow
} from '@/utils/rbacMenuTree'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { listRbacMenus, listRoleMenuPerms, setRoleMenuPerms } from '@/api/module'
import { hasId, idToString } from '@/utils/id'

type MenuRow = RbacMenuRow
type MenuFlatRow = RbacMenuFlatRow

const appId = ref('')
const roleId = ref('')

const loading = ref(false)
const saving = ref(false)

const menus = ref<MenuRow[]>([])
const selected = ref<Record<string, boolean>>({})
const permLevelText = ref('1')

const menusFlat = computed(() => flattenRbacMenusTree(menus.value || []))

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
  roleId.value = idToString((opts as any)?.roleId)
})

function isSelected(id: string) {
  return !!selected.value[id]
}

function toggle(id: string) {
  const key = idToString(id)
  const next = { ...selected.value }
  if (next[key]) delete next[key]
  else next[key] = true
  selected.value = next
}

function selectAll() {
  const next: Record<string, boolean> = {}
  for (const m of menusFlat.value) {
    const id = idToString(m.id)
    if (hasId(id)) next[id] = true
  }
  selected.value = next
}

function clearAll() {
  selected.value = {}
}

async function loadMenus() {
  if (!hasId(appId.value)) return
  loading.value = true
  try {
    const r = await listRbacMenus(appId.value)
    menus.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadCurrentPerms() {
  if (!hasId(roleId.value)) return
  const r = await listRoleMenuPerms(roleId.value)
  const perms = (r.data || []) as Array<{ menuId?: string | number; permLevel?: number }>
  const next: Record<string, boolean> = {}
  for (const p of perms) {
    const mid = idToString(p?.menuId)
    if (!hasId(mid)) continue
    next[mid] = true
  }
  selected.value = next
  const first = perms.find((p) => p?.permLevel != null)
  permLevelText.value = first ? String(first.permLevel) : '1'
}

async function reload() {
  selected.value = {}
  await Promise.all([loadMenus(), loadCurrentPerms()])
}

async function save() {
  if (!hasId(roleId.value)) return
  const permLevel = Number((permLevelText.value || '1').trim() || '1')
  if (Number.isNaN(permLevel) || (permLevel !== 0 && permLevel !== 1)) {
    uni.showToast({ title: 'permLevel 仅支持 0 或 1', icon: 'none' })
    return
  }
  const menuIds = Object.keys(selected.value).filter((id) => hasId(id))

  saving.value = true
  try {
    await setRoleMenuPerms({ roleId: roleId.value, menuIds, permLevel })
    uni.showToast({ title: '已保存', icon: 'success' })
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!hasId(appId.value) || !hasId(roleId.value)) {
    uni.showToast({ title: '缺少 appId/roleId', icon: 'none' })
    return
  }
  reload()
})
</script>
