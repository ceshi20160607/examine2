<template>
  <Page :title="`字典项 Items（dictId=${dictId}）`" subtitle="为 dictCode 添加可选项">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingId ? '编辑字典项' : '新增字典项'">
          <uni-easyinput v-model="form.itemValue" placeholder="value" />
          <uni-easyinput v-model="form.itemLabel" placeholder="label" />
        </uni-forms-item>
        <uni-forms-item label="排序">
          <uni-easyinput v-model="form.sortNo" type="number" placeholder="0" />
        </uni-forms-item>
        <uni-forms-item label="状态">
          <uni-data-select v-model="form.status" :localdata="statusOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">{{ editingId ? '保存' : '新增' }}</uni-button>
        <uni-button v-if="editingId" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="it in rows"
            :key="it.id"
            :title="it.itemLabel || it.itemValue || ('Item#' + it.id)"
            :note="`${it.itemValue || ''} · sort=${it.sortNo ?? 0} · ${it.status === 1 ? '启用' : '停用'}`"
            clickable
            @click="openActions(it)"
          />
        </uni-list>
        <EmptyState v-else text="暂无字典项" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { deleteDictItems, listDictItems, type ModuleDictItemRow, upsertDictItem } from '@/api/module'
import { hasId, idToString, type IdValue } from '@/utils/id'

const dictId = ref('')
const loading = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const rows = ref<ModuleDictItemRow[]>([])

const form = reactive<{ itemValue: string; itemLabel: string; sortNo: string; status: number }>({
  itemValue: '',
  itemLabel: '',
  sortNo: '0',
  status: 1
})
const statusOptions = [
  { value: 1, text: '启用' },
  { value: 2, text: '停用' }
]

onLoad((opts) => {
  dictId.value = idToString((opts as any)?.dictId)
})

async function load() {
  if (!hasId(dictId.value)) return
  loading.value = true
  try {
    const r = await listDictItems(dictId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!hasId(dictId.value)) return
  if (!form.itemValue.trim() || !form.itemLabel.trim()) {
    uni.showToast({ title: '请输入 value/label', icon: 'none' })
    return
  }
  const sortNo = Number((form.sortNo || '0').trim() || '0')
  if (Number.isNaN(sortNo)) {
    uni.showToast({ title: 'sortNo 非法', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await upsertDictItem(dictId.value, {
      id: editingId.value,
      itemValue: form.itemValue.trim(),
      itemLabel: form.itemLabel.trim(),
      sortNo,
      status: form.status
    })
    resetForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } finally {
    saving.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.itemValue = ''
  form.itemLabel = ''
  form.sortNo = '0'
  form.status = 1
}

function fillForm(item: ModuleDictItemRow) {
  editingId.value = idToString(item.id as IdValue)
  form.itemValue = item.itemValue || ''
  form.itemLabel = item.itemLabel || ''
  form.sortNo = String(item.sortNo ?? 0)
  form.status = item.status === 2 ? 2 : 1
}

function remove(item: ModuleDictItemRow) {
  const id = idToString(item.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '删除字典项？',
    content: item.itemLabel || item.itemValue || id,
    success: async (m) => {
      if (!m.confirm) return
      await deleteDictItems([id])
      if (editingId.value === id) resetForm()
      uni.showToast({ title: '已删除', icon: 'success' })
      await load()
    }
  })
}

function openActions(item: ModuleDictItemRow) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        fillForm(item)
        return
      }
      if (res.tapIndex === 1) {
        remove(item)
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

