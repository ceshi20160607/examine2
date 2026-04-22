<template>
  <view style="padding: 16px">
    <uni-card :title="`RBAC（appId=${appId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="loading" @click="loadRoles">刷新角色</uni-button>
        <uni-button :disabled="loading" @click="loadMenus">刷新菜单</uni-button>
      </view>
    </uni-card>

    <uni-card title="新增角色" style="margin-top: 12px">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="roleForm.roleCode" placeholder="roleCode" />
        <uni-easyinput v-model="roleForm.roleName" placeholder="roleName" />
        <uni-button type="primary" :disabled="savingRole" @click="upsertRole">保存</uni-button>
      </view>
    </uni-card>

    <uni-card title="角色列表" style="margin-top: 12px">
      <uni-list v-if="roles.length">
        <uni-list-item v-for="r in roles" :key="r.id" :title="r.roleName || r.roleCode || ('Role#' + r.id)" :note="r.roleCode || ''" />
      </uni-list>
      <view v-else style="color:#666">暂无角色</view>
    </uni-card>

    <uni-card title="菜单列表" style="margin-top: 12px">
      <uni-list v-if="menus.length">
        <uni-list-item
          v-for="m in menus"
          :key="m.id"
          :title="m.menuName || ('Menu#' + m.id)"
          :note="m.permKey || ''"
        />
      </uni-list>
      <view v-else style="color:#666">暂无菜单</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type RoleRow = { id: number; roleCode?: string; roleName?: string; status?: number }
type MenuRow = { id: number; parentId?: number; menuName?: string; permKey?: string; apiPattern?: string; pageId?: number }

const appId = ref<number>(0)
const loading = ref(false)

const roles = ref<RoleRow[]>([])
const menus = ref<MenuRow[]>([])

const savingRole = ref(false)
const roleForm = reactive<{ roleCode: string; roleName: string }>({ roleCode: '', roleName: '' })

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
})

async function loadRoles() {
  if (!appId.value) return
  loading.value = true
  try {
    const r = await httpGet<RoleRow[]>(`/v1/system/module/rbac/apps/${appId.value}/roles`)
    roles.value = r.data || []
  } finally {
    loading.value = false
  }
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

async function upsertRole() {
  if (!appId.value) return
  if (!roleForm.roleCode.trim() || !roleForm.roleName.trim()) {
    uni.showToast({ title: '请输入 roleCode/roleName', icon: 'none' })
    return
  }
  savingRole.value = true
  try {
    await httpPost(`/v1/system/module/rbac/apps/${appId.value}/roles/upsert`, {
      id: null,
      roleCode: roleForm.roleCode.trim(),
      roleName: roleForm.roleName.trim(),
      status: 1
    })
    roleForm.roleCode = ''
    roleForm.roleName = ''
    await loadRoles()
  } finally {
    savingRole.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadRoles()
  loadMenus()
})
</script>
