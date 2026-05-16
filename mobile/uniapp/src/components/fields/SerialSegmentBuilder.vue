<template>
  <view class="serial-builder">
    <view v-for="(seg, idx) in segments" :key="idx" class="serial-builder__seg">
      <view class="serial-builder__head">
        <text class="u-subtitle">段 {{ idx + 1 }}</text>
        <text v-if="segments.length > 1" class="serial-builder__remove" @click="remove(idx)">删除</text>
      </view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="类型">
          <uni-data-select v-model="seg.type" :localdata="typeOptions" @change="emitChange" />
        </uni-forms-item>
        <uni-forms-item v-if="seg.type === 'fixed'" label="固定文本">
          <uni-easyinput v-model="seg.value" placeholder="如 NO-" @blur="emitChange" />
        </uni-forms-item>
        <uni-forms-item v-if="seg.type === 'field'" label="引用字段 code">
          <uni-easyinput v-model="seg.fieldCode" placeholder="同模型字段 fieldCode" @blur="emitChange" />
        </uni-forms-item>
        <template v-if="seg.type === 'seq'">
          <uni-forms-item label="位数">
            <uni-easyinput v-model="seg.width" type="number" placeholder="4" @blur="emitChange" />
          </uni-forms-item>
          <uni-forms-item label="重置周期">
            <uni-data-select v-model="seg.reset" :localdata="resetOptions" @change="emitChange" />
          </uni-forms-item>
        </template>
      </uni-forms>
    </view>
    <uni-button size="mini" @click="add">添加段</uni-button>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

export type SerialSegment =
  | { type: 'fixed'; value?: string }
  | { type: 'field'; fieldCode?: string }
  | { type: 'seq'; width?: number | string; reset?: string }

const props = defineProps<{ modelValue?: SerialSegment[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: SerialSegment[]): void }>()

const typeOptions = [
  { value: 'fixed', text: '固定文本' },
  { value: 'field', text: '引用字段' },
  { value: 'seq', text: '流水号' }
]
const resetOptions = [
  { value: 'never', text: '不重置' },
  { value: 'day', text: '按日' },
  { value: 'month', text: '按月' }
]

const segments = ref<SerialSegment[]>([])

function normalize(list?: SerialSegment[]): SerialSegment[] {
  if (!list?.length) return [{ type: 'seq', width: 4, reset: 'never' }]
  return list.map((s) => ({ ...s }))
}

function emitChange() {
  emit('update:modelValue', segments.value.map((s) => ({ ...s })))
}

function add() {
  segments.value.push({ type: 'fixed', value: '' })
  emitChange()
}

function remove(idx: number) {
  segments.value.splice(idx, 1)
  if (!segments.value.length) segments.value.push({ type: 'seq', width: 4, reset: 'never' })
  emitChange()
}

watch(
  () => props.modelValue,
  (v) => {
    segments.value = normalize(v)
  },
  { immediate: true, deep: true }
)
</script>

<style scoped>
.serial-builder__seg {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 8px;
  margin-bottom: 10px;
}
.serial-builder__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.serial-builder__remove {
  color: #d00;
  font-size: 13px;
}
</style>
