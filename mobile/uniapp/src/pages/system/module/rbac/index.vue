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

    <uni-card title="成员分配角色" style="margin-top: 12px">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="memberForm.memberPlatId" placeholder="memberPlatId" />
        <uni-easyinput v-model="memberForm.roleId" placeholder="roleId" />
        <uni-button type="primary" :disabled="savingMember" @click="assignMemberRole">分配</uni-button>
      </view>
      <view style="margin-top: 8px; color:#666">说明：memberPlatId 为平台账号 platId（数字）。</view>
    </uni-card>

    <uni-card title="成员列表" style="margin-top: 12px">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button :disabled="loading" @click="loadMembers">刷新成员</uni-button>
      </view>
      <uni-list v-if="members.length">
        <uni-list-item
          v-for="m in members"
          :key="m.id"
          :title="`platId=${m.platId}`"
          :note="`roleId=${m.roleId || ''} status=${m.status || ''}`"
          clickable
          @click="quickFillMember(m)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无成员</view>
      <view style="margin-top: 8px; color:#666">点击成员可自动填充到“成员分配角色”。</view>
    </uni-card>

    <uni-card title="角色列表" style="margin-top: 12px">
      <uni-list v-if="roles.length">
        <uni-list-item
          v-for="r in roles"
          :key="r.id"
          :title="r.roleName || r.roleCode || ('Role#' + r.id)"
          :note="r.roleCode || ''"
          clickable
          @click="openRoleActions(r)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无角色</view>
    </uni-card>

    <uni-card title="新增菜单" style="margin-top: 12px">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="menuForm.parentId" placeholder="parentId(0根)" />
        <uni-easyinput v-model="menuForm.menuName" placeholder="menuName" />
        <uni-easyinput v-model="menuForm.permKey" placeholder="permKey(可选)" />
        <uni-easyinput v-model="menuForm.apiPattern" placeholder="apiPattern(可选)" />
        <uni-easyinput v-model="menuForm.pageId" placeholder="pageId(可选)" />
        <uni-button type="primary" :disabled="savingMenu" @click="upsertMenu">保存</uni-button>
      </view>
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
type MemberRow = { id: number; platId?: number; roleId?: number; status?: number }

const appId = ref<number>(0)
const loading = ref(false)

const roles = ref<RoleRow[]>([])
const menus = ref<MenuRow[]>([])
const members = ref<MemberRow[]>([])

const savingRole = ref(false)
const roleForm = reactive<{ roleCode: string; roleName: string }>({ roleCode: '', roleName: '' })

const savingMember = ref(false)
const memberForm = reactive<{ memberPlatId: string; roleId: string }>({ memberPlatId: '', roleId: '' })

const savingMenu = ref(false)
const menuForm = reactive<{ parentId: string; menuName: string; permKey: string; apiPattern: string; pageId: string }>({
  parentId: '0',
  menuName: '',
  permKey: '',
  apiPattern: '',
  pageId: ''
})

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

async function loadMembers() {
  if (!appId.value) return
  loading.value = true
  try {
    const r = await httpGet<MemberRow[]>(`/v1/system/module/rbac/apps/${appId.value}/members`)
    members.value = r.data || []
  } finally {
    loading.value = false
  }
}

function quickFillMember(m: MemberRow) {
  if (!m?.platId) return
  memberForm.memberPlatId = String(m.platId)
  if (m.roleId) memberForm.roleId = String(m.roleId)
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

async function assignMemberRole() {
  if (!appId.value) return
  const memberPlatId = Number(memberForm.memberPlatId.trim())
  const roleId = Number(memberForm.roleId.trim())
  if (!memberPlatId || Number.isNaN(memberPlatId) || !roleId || Number.isNaN(roleId)) {
    uni.showToast({ title: '请输入合法 memberPlatId/roleId', icon: 'none' })
    return
  }
  savingMember.value = true
  try {
    await httpPost('/v1/system/module/rbac/members/assign-role', {
      appId: appId.value,
      memberPlatId,
      roleId
    })
    uni.showToast({ title: '分配成功', icon: 'success' })
    memberForm.memberPlatId = ''
    memberForm.roleId = ''
  } finally {
    savingMember.value = false
  }
}

async function upsertMenu() {
  if (!appId.value) return
  if (!menuForm.menuName.trim()) {
    uni.showToast({ title: '请输入 menuName', icon: 'none' })
    return
  }
  const parentId = Number((menuForm.parentId || '0').trim() || '0')
  if (Number.isNaN(parentId)) {
    uni.showToast({ title: 'parentId 非法', icon: 'none' })
    return
  }
  const pageIdRaw = menuForm.pageId.trim()
  const pageId = pageIdRaw ? Number(pageIdRaw) : null
  if (pageIdRaw && (!pageId || Number.isNaN(pageId))) {
    uni.showToast({ title: 'pageId 非法', icon: 'none' })
    return
  }

  savingMenu.value = true
  try {
    await httpPost(`/v1/system/module/rbac/apps/${appId.value}/menus/upsert`, {
      id: null,
      parentId,
      menuName: menuForm.menuName.trim(),
      pageId,
      sortNo: 0,
      visibleFlag: 1,
      permKey: menuForm.permKey.trim() ? menuForm.permKey.trim() : null,
      apiPattern: menuForm.apiPattern.trim() ? menuForm.apiPattern.trim() : null
    })
    menuForm.menuName = ''
    menuForm.permKey = ''
    menuForm.apiPattern = ''
    menuForm.pageId = ''
    await loadMenus()
  } finally {
    savingMenu.value = false
  }
}

function openRoleActions(r: RoleRow) {
  if (!r?.id) return
  uni.showActionSheet({
    itemList: ['设置菜单权限'],
    success: (res) => {
      if (res.tapIndex !== 0) return
      uni.navigateTo({ url: `/pages/system/module/rbac/role_menus?appId=${appId.value}&roleId=${r.id}` })
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  loadRoles()
  loadMenus()
  loadMembers()
})
</script>
