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

const appId = ref(0)
const roleId = ref(0)
const loading = ref(false)
const saving = ref(false)
const pages = ref<Array<{ id: number; pageCode?: string; pageName?: string; pageType?: string }>>([])
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

async function reload() {
  if (!appId.value || !roleId.value) return
  loading.value = true
  try {
    const [pr, permR] = await Promise.all([listPagesByApp(appId.value), listRolePagePerms(roleId.value)])
    pages.value = pr.data || []
    const sel: Record<number, boolean> = {}
    for (const row of permR.data || []) {
      if (row?.pageId && row?.permLevel === 1) sel[Number(row.pageId)] = true
    }
    selected.value = sel
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!roleId.value) return
  const permLevel = Number(permLevelText.value.trim() || '1')
  const pageIds = Object.keys(selected.value)
    .map((k) => Number(k))
    .filter((n) => n > 0)
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
