<template>
  <Page :title="`连线条件（lineId=${lineId}）`" subtitle="用于条件分支：AND/OR 组合 + 比较操作">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="openEdit()">新增</uni-button>
        <uni-button :disabled="loading" @click="reload">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="c in rows"
            :key="String(c.id)"
            :title="`${c.leftVar || ''} ${c.cmpOp || ''} ${c.rightValue || ''}`"
            :note="`group=${c.groupNo ?? ''} logic=${c.logicOp || ''} status=${c.status ?? ''}`"
            clickable
            @click="openActions(c)"
          />
        </uni-list>
        <EmptyState v-else text="暂无条件" />
      </view>
    </view>

    <uni-popup ref="popupRef" type="bottom">
      <view class="u-card">
        <uni-forms labelPosition="top">
          <uni-forms-item label="groupNo">
            <uni-easyinput v-model="form.groupNo" type="number" />
          </uni-forms-item>
          <uni-forms-item label="logicOp(AND/OR)">
            <uni-easyinput v-model="form.logicOp" />
          </uni-forms-item>
          <uni-forms-item label="leftVar">
            <uni-easyinput v-model="form.leftVar" />
          </uni-forms-item>
          <uni-forms-item label="cmpOp(EQ/NE/GT/GE/LT/LE/IN/EXISTS)">
            <uni-easyinput v-model="form.cmpOp" />
          </uni-forms-item>
          <uni-forms-item label="rightType(string/number/bool/json/null)">
            <uni-easyinput v-model="form.rightType" />
          </uni-forms-item>
          <uni-forms-item label="rightValue">
            <uni-easyinput v-model="form.rightValue" type="textarea" :autoHeight="true" />
          </uni-forms-item>
        </uni-forms>
        <ActionBar>
          <uni-button type="primary" :disabled="saving" @click="save">保存</uni-button>
          <uni-button @click="closePopup">取消</uni-button>
        </ActionBar>
        <ErrorBlock :text="error" />
      </view>
    </uni-popup>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { deleteTempVerLineConds, pageTempVerLineConds, upsertTempVerLineCond } from '@/api/flow'

type CondRow = { id: number | string; groupNo?: number; logicOp?: string; leftVar?: string; cmpOp?: string; rightType?: string; rightValue?: string; status?: number }

const lineId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<CondRow[]>([])
const editingId = ref<any>(null)
const error = ref<string | null>(null)

const popupRef = ref<any>(null)
const form = reactive<{ groupNo: string; logicOp: string; leftVar: string; cmpOp: string; rightType: string; rightValue: string }>({
  groupNo: '0',
  logicOp: 'AND',
  leftVar: '',
  cmpOp: 'EQ',
  rightType: 'string',
  rightValue: ''
})

onLoad((opts) => {
  lineId.value = Number((opts as any)?.lineId || 0) || 0
})

async function load() {
  if (!lineId.value) return
  loading.value = true
  try {
    const r = await pageTempVerLineConds(lineId.value)
    rows.value = (r.data?.records || []) as CondRow[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  await load()
}

function openEdit(c?: CondRow) {
  editingId.value = c?.id ?? null
  form.groupNo = c?.groupNo == null ? '0' : String(c.groupNo)
  form.logicOp = String(c?.logicOp || 'AND')
  form.leftVar = String(c?.leftVar || '')
  form.cmpOp = String(c?.cmpOp || 'EQ')
  form.rightType = String(c?.rightType || 'string')
  form.rightValue = String(c?.rightValue || '')
  error.value = null
  popupRef.value?.open()
}

function closePopup() {
  popupRef.value?.close()
}

function openActions(c: CondRow) {
  if (!c?.id) return
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) return openEdit(c)
      if (res.tapIndex === 1) return del(c.id)
    }
  })
}

function del(id: any) {
  uni.showModal({
    title: '确认删除？',
    content: `将删除条件 #${id}`,
    success: async (m) => {
      if (!m.confirm) return
      await deleteTempVerLineConds([id])
      uni.showToast({ title: '已删除', icon: 'success' })
      reload()
    }
  })
}

async function save() {
  if (!lineId.value) return
  error.value = null
  const g = Number((form.groupNo || '0').trim() || '0')
  if (Number.isNaN(g)) {
    error.value = 'groupNo 非法'
    return
  }
  saving.value = true
  try {
    await upsertTempVerLineCond({
      id: editingId.value,
      lineId: lineId.value,
      groupNo: g,
      logicOp: form.logicOp.trim() || null,
      leftVar: form.leftVar.trim() || null,
      cmpOp: form.cmpOp.trim() || null,
      rightType: form.rightType.trim() || null,
      rightValue: form.rightValue,
      status: 1
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    closePopup()
    reload()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!lineId.value) {
    uni.showToast({ title: '缺少 lineId', icon: 'none' })
    return
  }
  load()
})
</script>

