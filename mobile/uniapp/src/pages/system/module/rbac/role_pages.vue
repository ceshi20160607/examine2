<template>
  <Page :title="`角色页面权限（roleId=${roleId}）`" subtitle="选择低代码页面并覆盖写权限">
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
    </view>

    <view class="u-card u-section">
      <view class="u-title">页面列表（点击切换）</view>
      <uni-list v-if="pages.length">
        <uni-list-item
          v-for="p in pages"
          :key="p.id"
          :title="`${isSelected(p.id) ? '✓ ' : ''}${p.pageName || p.pageCode}`"
          :note="`${p.pageCode || ''} · ${p.pageType || ''}`"
          clickable
          @click="toggle(p.id)"
        />
      </uni-list>
      <EmptyState v-else text="暂无页面（请先在 Pages 中创建）" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { listPagesByApp } from '@/api/pages'
import { listRolePagePerms, setRolePagePerms } from '@/api/module'
import { hasId, idToString } from '@/utils/id'

const appId = ref('')
const roleId = ref('')
const loading = ref(false)
const saving = ref(false)
const pages = ref<Array<{ id: string; pageCode?: string; pageName?: string; pageType?: string }>>([])
const selected = ref<Record<string, boolean>>({})
const permLevelText = ref('1')

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
  for (const p of pages.value) {
    const id = idToString(p.id)
    if (hasId(id)) next[id] = true
  }
  selected.value = next
}

function clearAll() {
  selected.value = {}
}

async function reload() {
  if (!hasId(appId.value) || !hasId(roleId.value)) return
  loading.value = true
  try {
    const [pr, permR] = await Promise.all([listPagesByApp(appId.value), listRolePagePerms(roleId.value)])
    pages.value = pr.data || []
    const sel: Record<string, boolean> = {}
    const perms = permR.data || []
    for (const row of perms) {
      const id = idToString(row?.pageId)
      if (hasId(id)) sel[id] = true
    }
    selected.value = sel
    const first = perms.find((row) => row?.permLevel != null)
    permLevelText.value = first ? String(first.permLevel) : '1'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!hasId(roleId.value)) return
  const permLevel = Number(permLevelText.value.trim() || '1')
  if (Number.isNaN(permLevel) || (permLevel !== 0 && permLevel !== 1)) {
    uni.showToast({ title: 'permLevel 仅支持 0 或 1', icon: 'none' })
    return
  }
  const pageIds = Object.keys(selected.value).filter((id) => hasId(id))
  saving.value = true
  try {
    await setRolePagePerms({ roleId: roleId.value, pageIds, permLevel })
    uni.showToast({ title: '已保存', icon: 'success' })
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  reload()
})
</script>
