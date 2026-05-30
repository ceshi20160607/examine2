<template>
  <Page :title="`应用菜单 appId=${appId}`" subtitle="点击菜单进入低代码页面（需绑定 pageId）">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <uni-list v-if="menusFlat.length">
        <uni-list-item
          v-for="m in menusFlat"
          :key="m.id"
          :title="rbacMenuTitleIndented(m)"
          :note="menuNote(m)"
          clickable
          @click="openMenu(m)"
        />
      </uni-list>
      <EmptyState v-else text="暂无菜单" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { ensureSystemContext } from '@/utils/guard'
import { listRuntimeMenus } from '@/api/module'
import {
  flattenRbacMenusTree,
  rbacMenuTitleIndented,
  type RbacMenuFlatRow
} from '@/utils/rbacMenuTree'
import { hasId, idToString } from '@/utils/id'

const appId = ref('')
const menusFlat = ref<RbacMenuFlatRow[]>([])
const { loading, error, run } = usePageRequest()

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
})

function menuNote(m: RbacMenuFlatRow) {
  const parts: string[] = []
  if (m.pageId) parts.push(`pageId=${m.pageId}`)
  if (m.permKey) parts.push(m.permKey)
  return parts.join(' · ') || '未绑定页面'
}

function openMenu(m: RbacMenuFlatRow) {
  const pid = idToString(m.pageId)
  if (!hasId(pid)) {
    uni.showToast({ title: '该菜单未绑定页面', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/system/runtime/entry?pageId=${encodeURIComponent(pid)}` })
}

async function load() {
  if (!hasId(appId.value)) return
  await run(async () => {
    const r = await listRuntimeMenus(appId.value)
    menusFlat.value = flattenRbacMenusTree(r.data || [])
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
