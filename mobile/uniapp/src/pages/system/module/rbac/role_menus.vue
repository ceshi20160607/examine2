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

type MenuRow = RbacMenuRow
type MenuFlatRow = RbacMenuFlatRow

const appId = ref<number>(0)
const roleId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)

const menus = ref<MenuRow[]>([])
const selected = ref<Record<number, boolean>>({})
const permLevelText = ref('1')

const menusFlat = computed(() => flattenRbacMenusTree(menus.value || []))

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
    const r = await listRbacMenus(appId.value)
    menus.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadCurrentPerms() {
  if (!roleId.value) return
  const r = await listRoleMenuPerms(roleId.value)
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
    await setRoleMenuPerms({ roleId: roleId.value, menuIds, permLevel })
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
