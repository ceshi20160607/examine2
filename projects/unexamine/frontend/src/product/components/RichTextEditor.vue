<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { richPlainText, sanitizeRichMarkup } from '../structured-content'
const props = withDefaults(defineProps<{ modelValue?: unknown; mode?: string; placeholder?: string; maxLength?: number }>(), { mode: 'FULL' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const editor = ref<HTMLElement>()
const link = ref('')
const error = ref('')
const count = computed(() => Array.from(richPlainText(String(props.modelValue || ''))).length)
let selection: Range | undefined
let lastEmitted: string | undefined
function rememberSelection() {
  const current = window.getSelection()
  if (current?.rangeCount && editor.value?.contains(current.anchorNode)) selection = current.getRangeAt(0).cloneRange()
}
function restoreSelection() {
  editor.value?.focus()
  if (selection) { const current = window.getSelection(); current?.removeAllRanges(); current?.addRange(selection) }
}
function changed() {
  const safe = sanitizeRichMarkup(editor.value?.innerHTML || '')
  lastEmitted = richPlainText(safe) ? safe : ''
  emit('update:modelValue', lastEmitted)
  rememberSelection()
}
function command(name: string, value?: string) {
  rememberSelection(); restoreSelection(); document.execCommand(name, false, value); changed()
}
function addLink() {
  try { const url = new URL(link.value); if (!['http:', 'https:'].includes(url.protocol)) throw new Error(); command('createLink', url.href); link.value = ''; error.value = '' }
  catch { error.value = '请输入完整的 http 或 https 链接' }
}
function paste(event: ClipboardEvent) { command('insertText', event.clipboardData?.getData('text/plain') || '') }
watch(() => props.modelValue, async value => {
  await nextTick()
  if (value !== lastEmitted && editor.value) editor.value.innerHTML = sanitizeRichMarkup(String(value || ''))
}, { immediate: true })
</script>
<template>
  <section class="rich-editor">
    <div class="rich-toolbar" role="toolbar" aria-label="富文本格式">
      <a-button size="small" aria-label="加粗" @mousedown.prevent @click="command('bold')"><b>粗体</b></a-button>
      <a-button size="small" aria-label="斜体" @mousedown.prevent @click="command('italic')"><i>斜体</i></a-button>
      <a-button size="small" aria-label="下划线" @mousedown.prevent @click="command('underline')"><u>下划线</u></a-button>
      <template v-if="mode === 'FULL'"><a-button size="small" @mousedown.prevent @click="command('insertUnorderedList')">项目列表</a-button><a-button size="small" @mousedown.prevent @click="command('insertOrderedList')">编号列表</a-button><a-button size="small" @mousedown.prevent @click="command('formatBlock', 'h2')">标题</a-button><a-button size="small" @mousedown.prevent @click="command('formatBlock', 'p')">正文</a-button></template>
    </div>
    <div ref="editor" class="rich-editor__content" contenteditable="true" role="textbox" aria-label="富文本内容" aria-multiline="true" :data-placeholder="placeholder || '填写内容，可使用上方工具设置格式'" @input="changed" @mouseup="rememberSelection" @keyup="rememberSelection" @blur="rememberSelection" @paste.prevent="paste" @drop.prevent />
    <div v-if="mode === 'FULL'" class="rich-link"><a-input v-model:value="link" type="url" aria-label="富文本链接地址" placeholder="https://example.com" size="small" /><a-button size="small" @click="addLink">为选中文字添加链接</a-button></div>
    <small v-if="error" class="rich-error">{{ error }}</small><small>{{ count }}{{ maxLength ? ` / ${maxLength}` : '' }} 字</small>
  </section>
</template>
<style scoped>
.rich-editor { border: 1px solid #dbe4ef; border-radius: 8px; padding: 10px; display: grid; gap: 10px; }
.rich-toolbar, .rich-link { display: flex; gap: 6px; flex-wrap: wrap; }
.rich-link .ant-input { flex: 1; min-width: 150px; }
.rich-editor__content { min-height: 130px; max-height: 350px; overflow: auto; outline: none; line-height: 1.6; }
.rich-editor__content:empty::before { content: attr(data-placeholder); color: #94a3b8; }
.rich-error { color: #c62828; }
</style>
