<template>
  <Page :title="`RBAC（appId=${appId}）`" subtitle="角色 / 成员 / 菜单 / 权限调试">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="loadRoles">刷新角色</uni-button>
        <uni-button :disabled="loading" @click="loadMenus">刷新菜单</uni-button>
        <uni-button :disabled="loading" @click="loadMembers">刷新成员</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">新增角色</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="roleCode">
          <uni-easyinput v-model="roleForm.roleCode" placeholder="roleCode" />
        </uni-forms-item>
        <uni-forms-item label="roleName">
          <uni-easyinput v-model="roleForm.roleName" placeholder="roleName" />
        </uni-forms-item>
        <uni-forms-item label="数据权限 dataScope">
          <uni-data-select v-model="roleForm.dataScope" :localdata="dataScopeOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="savingRole" @click="upsertRole">保存</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">成员分配角色</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="搜索账号（≥2字符）">
          <view style="display:flex; gap:8px; flex-wrap:wrap; align-items:center;">
            <uni-easyinput v-model="accountKeyword" placeholder="用户名/显示名" style="flex:1; min-width:160px" />
            <uni-button size="mini" :disabled="searchingAccount" @click="searchAccount">搜索</uni-button>
          </view>
        </uni-forms-item>
        <uni-forms-item v-if="accountHits.length" label="选择账号">
          <uni-data-select v-model="memberForm.memberPlatId" :localdata="accountHits" placeholder="选择成员" />
        </uni-forms-item>
        <uni-forms-item v-else label="memberPlatId">
          <uni-easyinput v-model="memberForm.memberPlatId" placeholder="平台账号 platId" />
        </uni-forms-item>
        <uni-forms-item label="roleId">
          <uni-data-select v-model="memberForm.roleId" :localdata="roleOptions" placeholder="选择角色" />
        </uni-forms-item>
        <uni-forms-item label="部门（树形选择）">
          <uni-data-select v-model="memberForm.deptId" :localdata="deptOptions" placeholder="可选" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="savingMember" @click="assignMemberRole">分配</uni-button>
      </ActionBar>
      <view class="u-subtitle">点击成员可自动填充到这里。</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">权限验证（按 URI）</view>
      <ActionBar>
        <uni-easyinput v-model="permPreviewUri" placeholder="/v1/system/records/query" style="flex:1; min-width: 220px" />
        <uni-button type="primary" :disabled="previewingPerm" @click="previewPerm">验证</uni-button>
      </ActionBar>
      <view v-if="permPreviewText" style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ permPreviewText }}</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">成员列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="members.length">
          <uni-list-item
            v-for="m in members"
            :key="m.id"
            :title="`platId=${m.platId}`"
            :note="`roleId=${m.roleId || ''} deptId=${(m as any).deptId || ''} status=${m.status || ''}`"
            clickable
            @click="quickFillMember(m)"
          />
        </uni-list>
        <EmptyState v-else text="暂无成员" />
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">角色列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="roles.length">
          <uni-list-item
            v-for="r in roles"
            :key="r.id"
            :title="r.roleName || r.roleCode || ('Role#' + r.id)"
            :note="`${r.roleCode || ''} scope=${(r as any).dataScope ?? 1}`"
            clickable
            @click="openRoleActions(r)"
          />
        </uni-list>
        <EmptyState v-else text="暂无角色" />
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">新增菜单</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="parentId(0根)">
          <uni-easyinput v-model="menuForm.parentId" placeholder="parentId(0根)" />
        </uni-forms-item>
        <uni-forms-item label="menuName">
          <uni-easyinput v-model="menuForm.menuName" placeholder="menuName" />
        </uni-forms-item>
        <uni-forms-item label="permKey(可选)">
          <uni-easyinput v-model="menuForm.permKey" placeholder="permKey(可选)" />
        </uni-forms-item>
        <uni-forms-item label="apiPattern(可选)">
          <uni-easyinput v-model="menuForm.apiPattern" placeholder="apiPattern(可选)" />
        </uni-forms-item>
        <uni-forms-item label="关联页面 pageId（可选）">
          <uni-data-select v-model="menuForm.pageId" :localdata="pageOptions" placeholder="选择低代码页面" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="savingMenu" @click="upsertMenu">保存</uni-button>
      </ActionBar>
      <view class="u-subtitle">点击菜单行会把 parentId 预填到这里。</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">菜单列表（树形）</view>
      <view style="margin-top: 12px">
        <uni-list v-if="menusFlat.length">
          <uni-list-item
            v-for="m in menusFlat"
            :key="m.id"
            :title="rbacMenuTitleIndented(m)"
            :note="rbacMenuNote(m)"
            clickable
            @click="quickFillParentMenu(m)"
          />
        </uni-list>
        <EmptyState v-else text="暂无菜单" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import {
  assignRbacMemberRole,
  listRbacMembers,
  listRbacMenus,
  listRbacRoles,
  permPreview,
  upsertRbacMenu,
  searchRbacAccounts,
  upsertRbacRole
} from '@/api/module'
import { listApps } from '@/api/meta'
import { listDeptPickerOptions } from '@/api/dept'
import { listPagePickerOptions } from '@/api/pages'
import { hasId, idToString } from '@/utils/id'

type RoleRow = { id: string; roleCode?: string; roleName?: string; status?: number; dataScope?: number }
type MenuRow = RbacMenuRow
type MenuFlatRow = RbacMenuFlatRow
type MemberRow = { id: string; platId?: string; roleId?: string; status?: number }

const appId = ref('')
const { loading, error, run, capture, clearError } = usePageRequest()

const roles = ref<RoleRow[]>([])
const menus = ref<MenuRow[]>([])
const members = ref<MemberRow[]>([])

const savingRole = ref(false)
const dataScopeOptions = [
  { value: 1, text: '1 仅本人' },
  { value: 2, text: '2 本人及下属' },
  { value: 3, text: '3 本部门' },
  { value: 4, text: '4 本部门及下级' },
  { value: 5, text: '5 全部' }
]
const roleForm = reactive<{ roleCode: string; roleName: string; dataScope: number }>({
  roleCode: '',
  roleName: '',
  dataScope: 1
})
const roleOptions = computed(() =>
  roles.value.map((r) => ({
    value: r.id,
    text: `${r.roleName || r.roleCode || r.id} (scope=${r.dataScope ?? 1})`
  }))
)
const deptOptions = ref<Array<{ value: string; text: string }>>([])
const pageOptions = ref<Array<{ value: string; text: string }>>([])
const accountKeyword = ref('')
const accountHits = ref<Array<{ value: number | string; text: string }>>([])
const searchingAccount = ref(false)

const savingMember = ref(false)
const memberForm = reactive<{ memberPlatId: string; roleId: string; deptId: string }>({
  memberPlatId: '',
  roleId: '',
  deptId: ''
})

const savingMenu = ref(false)
const menuForm = reactive<{ parentId: string; menuName: string; permKey: string; apiPattern: string; pageId: string }>({
  parentId: '0',
  menuName: '',
  permKey: '',
  apiPattern: '',
  pageId: ''
})

const previewingPerm = ref(false)
const permPreviewUri = ref('/v1/system/records/query')
const permPreviewText = ref('')

const menusFlat = computed(() => flattenRbacMenusTree(menus.value || []))

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
})

async function ensureAppIdFromFirstApp() {
  if (hasId(appId.value)) return
  try {
    const r = await listApps()
    const apps = r.data || []
    if (apps.length && apps[0]?.id) appId.value = idToString(apps[0].id)
  } catch {
    /* ignore */
  }
}

function quickFillParentMenu(m: MenuFlatRow) {
  menuForm.parentId = idToString(m.id) || '0'
}

async function loadRoles() {
  if (!hasId(appId.value)) return
  await run(async () => {
    const r = await listRbacRoles(appId.value)
    roles.value = r.data || []
  })
}

async function loadMenus() {
  if (!hasId(appId.value)) return
  await run(async () => {
    const r = await listRbacMenus(appId.value)
    menus.value = r.data || []
  })
}

async function loadMembers() {
  if (!hasId(appId.value)) return
  await run(async () => {
    const r = await listRbacMembers(appId.value)
    members.value = r.data || []
  })
}

function quickFillMember(m: MemberRow) {
  if (!m?.platId) return
  memberForm.memberPlatId = String(m.platId)
  if (m.roleId) memberForm.roleId = String(m.roleId)
  if ((m as any).deptId) memberForm.deptId = String((m as any).deptId)
}

async function upsertRole() {
  if (!hasId(appId.value)) return
  if (!roleForm.roleCode.trim() || !roleForm.roleName.trim()) {
    uni.showToast({ title: '请输入 roleCode/roleName', icon: 'none' })
    return
  }
  savingRole.value = true
  try {
    await upsertRbacRole(appId.value, {
      id: null,
      roleCode: roleForm.roleCode.trim(),
      roleName: roleForm.roleName.trim(),
      status: 1,
      dataScope: Number(roleForm.dataScope) || 1
    })
    roleForm.roleCode = ''
    roleForm.roleName = ''
    await loadRoles()
  } finally {
    savingRole.value = false
  }
}

async function assignMemberRole() {
  if (!hasId(appId.value)) return
  const memberPlatId = idToString(memberForm.memberPlatId)
  const roleId = idToString(memberForm.roleId)
  if (!hasId(memberPlatId) || !hasId(roleId)) {
    uni.showToast({ title: '请输入合法 memberPlatId/roleId', icon: 'none' })
    return
  }
  savingMember.value = true
  try {
    const deptRaw = String(memberForm.deptId ?? '').trim()
    const deptId = hasId(deptRaw) ? deptRaw : null
    await assignRbacMemberRole({
      appId: appId.value,
      memberPlatId,
      roleId,
      deptId
    })
    uni.showToast({ title: '分配成功', icon: 'success' })
    memberForm.memberPlatId = ''
    memberForm.roleId = ''
    memberForm.deptId = ''
    await loadMembers()
  } finally {
    savingMember.value = false
  }
}

async function upsertMenu() {
  if (!hasId(appId.value)) return
  if (!menuForm.menuName.trim()) {
    uni.showToast({ title: '请输入 menuName', icon: 'none' })
    return
  }
  const parentId = idToString(menuForm.parentId) || '0'
  if (!/^\d+$/.test(parentId)) {
    uni.showToast({ title: 'parentId 非法', icon: 'none' })
    return
  }
  const pageIdRaw = menuForm.pageId.trim()
  const pageId = hasId(pageIdRaw) ? pageIdRaw : null
  if (pageIdRaw && !/^\d+$/.test(pageIdRaw)) {
    uni.showToast({ title: 'pageId 非法', icon: 'none' })
    return
  }

  savingMenu.value = true
  try {
    await upsertRbacMenu(appId.value, {
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

async function previewPerm() {
  const uri = (permPreviewUri.value || '').trim()
  if (!uri) {
    uni.showToast({ title: '请输入 uri', icon: 'none' })
    return
  }
  previewingPerm.value = true
  permPreviewText.value = ''
  try {
    const r = await permPreview(uri)
    permPreviewText.value = JSON.stringify(r.data ?? null, null, 2)
  } catch (e: any) {
    permPreviewText.value = e?.message ?? String(e)
  } finally {
    previewingPerm.value = false
  }
}

async function loadDeptOptions() {
  if (!hasId(appId.value)) return
  try {
    const r = await listDeptPickerOptions(appId.value)
    deptOptions.value = [{ value: '', text: '（不指定部门）' }].concat(
      (r.data || []).map((x) => ({ value: idToString(x.value), text: x.text }))
    )
  } catch {
    deptOptions.value = []
  }
}

async function loadPageOptions() {
  if (!hasId(appId.value)) return
  try {
    const r = await listPagePickerOptions(appId.value)
    pageOptions.value = [{ value: '', text: '（不关联页面）' }].concat(
      (r.data || []).map((x) => ({ value: idToString(x.value), text: x.text }))
    )
  } catch {
    pageOptions.value = []
  }
}

async function searchAccount() {
  const kw = accountKeyword.value.trim()
  if (kw.length < 2) {
    uni.showToast({ title: '至少输入 2 个字符', icon: 'none' })
    return
  }
  searchingAccount.value = true
  try {
    const r = await searchRbacAccounts(kw)
    accountHits.value = (r.data || []).map((a) => ({ value: a.platId, text: a.text }))
    if (!accountHits.value.length) uni.showToast({ title: '无匹配账号', icon: 'none' })
  } finally {
    searchingAccount.value = false
  }
}

function openRoleActions(r: RoleRow) {
  if (!hasId(r?.id)) return
  uni.showActionSheet({
    itemList: ['设置菜单权限', '设置页面权限', '编辑数据权限'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        uni.navigateTo({
          url: `/pages/system/module/rbac/role_menus?appId=${encodeURIComponent(appId.value)}&roleId=${encodeURIComponent(idToString(r.id))}`
        })
        return
      }
      if (res.tapIndex === 1) {
        uni.navigateTo({
          url: `/pages/system/module/rbac/role_pages?appId=${encodeURIComponent(appId.value)}&roleId=${encodeURIComponent(idToString(r.id))}`
        })
        return
      }
      if (res.tapIndex === 2 && r.roleCode && r.roleName) {
        uni.showActionSheet({
          itemList: dataScopeOptions.map((o) => o.text),
          success: async (s2) => {
            const picked = dataScopeOptions[s2.tapIndex]
            if (!picked) return
            savingRole.value = true
            try {
              await upsertRbacRole(appId.value, {
                id: r.id,
                roleCode: r.roleCode!,
                roleName: r.roleName!,
                status: r.status ?? 1,
                dataScope: Number(picked.value)
              })
              uni.showToast({ title: '数据权限已更新', icon: 'success' })
              await loadRoles()
            } finally {
              savingRole.value = false
            }
          }
        })
      }
    }
  })
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  await ensureAppIdFromFirstApp()
  loadRoles()
  loadMenus()
  loadMembers()
  loadDeptOptions()
  loadPageOptions()
})
</script>
