<template>
  <Page :title="`筛选模板 modelId=${modelId}`" subtitle="配置列表筛选模板和筛选字段">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingTplId ? '编辑模板' : '新建模板'">
          <uni-easyinput v-model="tplForm.tplCode" placeholder="tplCode" />
          <uni-easyinput v-model="tplForm.tplName" placeholder="tplName" />
        </uni-forms-item>
        <uni-forms-item label="menuId(可选)">
          <uni-easyinput v-model="tplForm.menuId" placeholder="menuId(可选)" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="savingTpl" @click="saveTpl">{{ editingTplId ? '保存模板' : '创建模板' }}</uni-button>
        <uni-button v-if="editingTplId" @click="resetTplForm">取消编辑</uni-button>
        <uni-button :disabled="loadingTpls" @click="loadTpls">刷新</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">模板列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="t in rows"
            :key="t.id"
            :title="t.tplName || t.tplCode || ('Tpl#' + t.id)"
            :note="`${t.tplCode || ''} / menuId=${t.menuId || '-'}`"
            clickable
            @click="openTplActions(t)"
          />
        </uni-list>
        <EmptyState v-else text="暂无模板" />
      </view>
    </view>

    <view v-if="activeTplId" class="u-card u-section">
      <view class="u-title">筛选项 · {{ activeTplName }}</view>
      <uni-forms labelPosition="top" style="margin-top: 12px">
        <uni-forms-item label="字段">
          <uni-data-select
            v-if="fieldOptions.length"
            v-model="fieldForm.fieldId"
            :localdata="fieldOptions"
            placeholder="选择筛选字段"
          />
          <uni-easyinput v-else v-model="fieldForm.fieldId" placeholder="fieldId" />
        </uni-forms-item>
        <uni-forms-item label="操作符">
          <uni-data-select v-model="fieldForm.opCode" :localdata="opOptions" />
        </uni-forms-item>
        <uni-forms-item label="默认值">
          <uni-easyinput v-model="fieldForm.defaultValue" placeholder="可选；between/in 可用英文逗号分隔" />
        </uni-forms-item>
        <uni-forms-item label="是否必填">
          <uni-data-select v-model="fieldForm.requiredFlag" :localdata="flagOptions" />
        </uni-forms-item>
        <uni-forms-item label="排序号">
          <uni-easyinput v-model="fieldForm.sortNo" type="number" placeholder="数字越小越靠前" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="savingField" @click="saveField">{{ editingFieldId ? '保存筛选项' : '新增筛选项' }}</uni-button>
        <uni-button v-if="editingFieldId" @click="resetFieldForm">取消编辑</uni-button>
        <uni-button :disabled="loadingFields" @click="loadFields">刷新筛选项</uni-button>
      </ActionBar>

      <view style="margin-top: 12px">
        <uni-list v-if="filterFields.length">
          <uni-list-item
            v-for="f in filterFields"
            :key="f.id"
            :title="fieldLabel(f.fieldId)"
            :note="fieldNote(f)"
            clickable
            @click="openFieldActions(f)"
          />
        </uni-list>
        <EmptyState v-else text="暂无筛选项" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import {
  deleteFilterFields,
  deleteFilterTpls,
  listFilterFields,
  listFilterTpls,
  type ModuleFilterFieldRow,
  type ModuleFilterTplRow,
  upsertFilterField,
  upsertFilterTpl
} from '@/api/module'
import { hasId, idToString, type IdValue } from '@/utils/id'

const appId = ref('')
const modelId = ref('')
const loadingTpls = ref(false)
const savingTpl = ref(false)
const loadingFields = ref(false)
const savingField = ref(false)
const rows = ref<ModuleFilterTplRow[]>([])
const modelFields = ref<ModuleField[]>([])
const filterFields = ref<ModuleFilterFieldRow[]>([])
const activeTpl = ref<ModuleFilterTplRow | null>(null)
const editingTplId = ref<string | null>(null)
const editingFieldId = ref<string | null>(null)

const tplForm = reactive<{ tplCode: string; tplName: string; menuId: string }>({ tplCode: '', tplName: '', menuId: '' })
const fieldForm = reactive<{ fieldId: string; opCode: string; defaultValue: string; requiredFlag: number; sortNo: string }>({
  fieldId: '',
  opCode: 'eq',
  defaultValue: '',
  requiredFlag: 0,
  sortNo: '0'
})

const opOptions = [
  { value: 'eq', text: '等于' },
  { value: 'ne', text: '不等于' },
  { value: 'like', text: '包含' },
  { value: 'in', text: '属于' },
  { value: 'between', text: '区间' },
  { value: 'gt', text: '大于' },
  { value: 'ge', text: '大于等于' },
  { value: 'lt', text: '小于' },
  { value: 'le', text: '小于等于' }
]
const flagOptions = [{ value: 0, text: '否' }, { value: 1, text: '是' }]
const activeTplId = computed(() => idToString(activeTpl.value?.id as IdValue))
const activeTplName = computed(() => activeTpl.value?.tplName || activeTpl.value?.tplCode || activeTplId.value)
const fieldOptions = computed(() =>
  modelFields.value.map((f) => ({
    text: `${f.fieldName || f.fieldCode || f.id} (${f.fieldCode || f.id})`,
    value: String(f.id)
  }))
)

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
})

async function loadTpls() {
  if (!hasId(modelId.value)) return
  loadingTpls.value = true
  try {
    const r = await listFilterTpls(modelId.value)
    rows.value = r.data || []
  } finally {
    loadingTpls.value = false
  }
}

async function loadModelFields() {
  if (!hasId(modelId.value)) return
  try {
    const r = await listFieldsByModel(modelId.value)
    modelFields.value = r.data || []
  } catch {
    modelFields.value = []
  }
}

async function saveTpl() {
  if (!hasId(appId.value) || !hasId(modelId.value)) return
  if (!tplForm.tplCode.trim() || !tplForm.tplName.trim()) {
    uni.showToast({ title: '请输入 tplCode/tplName', icon: 'none' })
    return
  }
  const menuIdRaw = tplForm.menuId.trim()
  const menuId = menuIdRaw ? idToString(menuIdRaw) : null
  if (menuIdRaw && !hasId(menuId)) {
    uni.showToast({ title: 'menuId 非法', icon: 'none' })
    return
  }

  savingTpl.value = true
  try {
    await upsertFilterTpl({
      id: editingTplId.value,
      appId: appId.value,
      modelId: modelId.value,
      menuId,
      tplCode: tplForm.tplCode.trim(),
      tplName: tplForm.tplName.trim(),
      status: 1
    })
    resetTplForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await loadTpls()
  } finally {
    savingTpl.value = false
  }
}

function resetTplForm() {
  editingTplId.value = null
  tplForm.tplCode = ''
  tplForm.tplName = ''
  tplForm.menuId = ''
}

function fillTpl(t: ModuleFilterTplRow) {
  editingTplId.value = idToString(t.id as IdValue)
  tplForm.tplCode = t.tplCode || ''
  tplForm.tplName = t.tplName || ''
  tplForm.menuId = t.menuId ? String(t.menuId) : ''
}

async function selectTpl(t: ModuleFilterTplRow) {
  activeTpl.value = t
  resetFieldForm()
  await loadFields()
}

function openTplActions(t: ModuleFilterTplRow) {
  uni.showActionSheet({
    itemList: ['配置筛选项', '编辑模板', '删除模板'],
    success: (res) => {
      if (res.tapIndex === 0) selectTpl(t)
      if (res.tapIndex === 1) fillTpl(t)
      if (res.tapIndex === 2) deleteTpl(t)
    }
  })
}

function deleteTpl(t: ModuleFilterTplRow) {
  const id = idToString(t.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '确认删除？',
    content: '会同时删除该模板下的筛选项。',
    success: async (m) => {
      if (!m.confirm) return
      await deleteFilterTpls([id])
      if (activeTplId.value === id) activeTpl.value = null
      uni.showToast({ title: '已删除', icon: 'success' })
      await loadTpls()
    }
  })
}

async function loadFields() {
  if (!hasId(activeTplId.value)) return
  loadingFields.value = true
  try {
    const r = await listFilterFields(activeTplId.value)
    filterFields.value = r.data || []
  } finally {
    loadingFields.value = false
  }
}

async function saveField() {
  if (!hasId(activeTplId.value)) return
  const fieldId = idToString(fieldForm.fieldId)
  if (!hasId(fieldId)) {
    uni.showToast({ title: '请选择字段', icon: 'none' })
    return
  }
  const sortNo = Number((fieldForm.sortNo || '0').trim() || '0')
  if (Number.isNaN(sortNo)) {
    uni.showToast({ title: '排序号不合法', icon: 'none' })
    return
  }
  savingField.value = true
  try {
    await upsertFilterField({
      id: editingFieldId.value,
      tplId: activeTplId.value,
      fieldId,
      opCode: fieldForm.opCode || 'eq',
      defaultValue: fieldForm.defaultValue.trim() || null,
      requiredFlag: fieldForm.requiredFlag,
      sortNo
    })
    resetFieldForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await loadFields()
  } finally {
    savingField.value = false
  }
}

function resetFieldForm() {
  editingFieldId.value = null
  fieldForm.fieldId = ''
  fieldForm.opCode = 'eq'
  fieldForm.defaultValue = ''
  fieldForm.requiredFlag = 0
  fieldForm.sortNo = String(filterFields.value.length + 1)
}

function fillField(f: ModuleFilterFieldRow) {
  editingFieldId.value = idToString(f.id as IdValue)
  fieldForm.fieldId = idToString(f.fieldId as IdValue)
  fieldForm.opCode = f.opCode || 'eq'
  fieldForm.defaultValue = f.defaultValue || ''
  fieldForm.requiredFlag = f.requiredFlag ?? 0
  fieldForm.sortNo = String(f.sortNo ?? 0)
}

function fieldLabel(fieldId: IdValue | null | undefined) {
  const id = idToString(fieldId)
  const field = modelFields.value.find((f) => String(f.id) === id)
  return field ? `${field.fieldName || field.fieldCode} (${field.fieldCode || field.id})` : `fieldId=${id}`
}

function fieldNote(f: ModuleFilterFieldRow) {
  return `${f.opCode || 'eq'} / 默认=${f.defaultValue || '-'} / ${f.requiredFlag === 1 ? '必填' : '可选'} / sortNo=${f.sortNo ?? ''}`
}

function openFieldActions(f: ModuleFilterFieldRow) {
  uni.showActionSheet({
    itemList: ['编辑', '删除筛选项'],
    success: (res) => {
      if (res.tapIndex === 0) fillField(f)
      if (res.tapIndex === 1) deleteField(f)
    }
  })
}

function deleteField(f: ModuleFilterFieldRow) {
  const id = idToString(f.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '确认删除？',
    content: '只删除该筛选项配置。',
    success: async (m) => {
      if (!m.confirm) return
      await deleteFilterFields([id])
      if (editingFieldId.value === id) resetFieldForm()
      uni.showToast({ title: '已删除', icon: 'success' })
      await loadFields()
    }
  })
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  await Promise.all([loadModelFields(), loadTpls()])
})
</script>
