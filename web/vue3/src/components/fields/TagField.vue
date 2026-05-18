<template>
  <div class="tag-field">
    <div v-if="presetTags.length" class="tag-field__chips">
      <button
        v-for="t in presetTags"
        :key="t"
        type="button"
        class="tag-field__chip"
        :class="{ 'is-on': selectedSet.has(t) }"
        @click="toggleTag(t)"
      >
        {{ t }}
      </button>
    </div>
    <input
      v-if="allowCustom"
      v-model="customText"
      class="field__input"
      placeholder="自定义标签，逗号分隔"
      @blur="mergeCustom"
    />
    <p v-if="selected.length" class="muted">已选：{{ selected.join('、') }}</p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { configFromMeta } from '../../utils/fieldTypes.js'

const props = defineProps({ field: { type: Object, required: true }, modelValue: { default: null } })
const emit = defineEmits(['update:modelValue'])

const cfg = computed(() => configFromMeta(props.field))
const presetTags = computed(() => {
  const tags = cfg.value.tags
  return Array.isArray(tags) ? tags.map((x) => String(x)).filter(Boolean) : []
})
const allowCustom = computed(() => cfg.value.allowCustom !== false)

const selected = ref([])
const customText = ref('')
const selectedSet = computed(() => new Set(selected.value))

function parseValue(raw) {
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return raw.map(String).filter(Boolean)
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr.map(String).filter(Boolean) : []
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

function toggleTag(tag) {
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
  selected.value = [...new Set([...selected.value, ...extra])]
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
  border: 1px solid #d1d5db;
  background: #f3f4f6;
  cursor: pointer;
  font-size: 13px;
}
.tag-field__chip.is-on {
  background: #1677ff;
  color: #fff;
  border-color: #1677ff;
}
.field__input {
  width: 100%;
  padding: 0.45rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  box-sizing: border-box;
}
.muted {
  font-size: 0.85rem;
  color: #666;
  margin-top: 6px;
}
</style>
