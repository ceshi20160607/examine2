<template>
  <view class="tag-field">
    <view v-if="presetTags.length" class="tag-field__chips">
      <view
        v-for="t in presetTags"
        :key="t"
        class="tag-field__chip"
        :class="{ 'tag-field__chip--on': selectedSet.has(t) }"
        @click="toggleTag(t)"
      >
        {{ t }}
      </view>
    </view>
    <uni-easyinput
      v-if="allowCustom"
      v-model="customText"
      placeholder="自定义标签，逗号分隔"
      @blur="mergeCustom"
    />
    <view v-if="selected.length" class="tag-field__summary">已选：{{ selected.join('、') }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { configFromMeta } from '@/utils/fieldTypes'
import type { ModuleField } from '@/api/meta'

const props = defineProps<{ field: ModuleField; modelValue: unknown }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const cfg = computed(() => configFromMeta(props.field))
const presetTags = computed(() => {
  const tags = cfg.value.tags
  return Array.isArray(tags) ? tags.map((x) => String(x)).filter(Boolean) : []
})
const allowCustom = computed(() => cfg.value.allowCustom !== false)

const selected = ref<string[]>([])
const customText = ref('')

const selectedSet = computed(() => new Set(selected.value))

function parseValue(raw: unknown): string[] {
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return raw.map((x) => String(x)).filter(Boolean)
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr.map((x) => String(x)).filter(Boolean) : []
      } catch {
        return t.split(/[,，]/).map((x) => x.trim()).filter(Boolean)
      }
    }
    return t.split(/[,，]/).map((x) => x.trim()).filter(Boolean)
  }
  return [String(raw)]
}

function emitValue() {
  emit('update:modelValue', selected.value.join(','))
}

function toggleTag(tag: string) {
  const set = new Set(selected.value)
  if (set.has(tag)) set.delete(tag)
  else set.add(tag)
  selected.value = [...set]
  emitValue()
}

function mergeCustom() {
  if (!allowCustom.value) return
  const extra = customText.value
    .split(/[,，]/)
    .map((x) => x.trim())
    .filter(Boolean)
  if (!extra.length) return
  const set = new Set([...selected.value, ...extra])
  selected.value = [...set]
  customText.value = ''
  emitValue()
}

watch(
  () => props.modelValue,
  (v) => {
    selected.value = parseValue(v)
  },
  { immediate: true }
)
</script>

<style scoped>
.tag-field__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.tag-field__chip {
  padding: 4px 10px;
  border-radius: 16px;
  background: #f0f0f0;
  font-size: 13px;
}
.tag-field__chip--on {
  background: #1677ff;
  color: #fff;
}
.tag-field__summary {
  margin-top: 6px;
  font-size: 12px;
  color: #666;
}
</style>
