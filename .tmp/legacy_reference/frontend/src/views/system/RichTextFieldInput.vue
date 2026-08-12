<script setup lang="ts">
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { Bold, Italic, Link2, List, ListOrdered, Redo2, Underline, Undo2, Unlink } from 'lucide-vue-next'
import { onBeforeUnmount, watch } from 'vue'

const props = defineProps<{
  modelValue?: string
  inputId: string
  label: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: []
}>()

const editor = useEditor({
  content: props.modelValue ?? '',
  editable: !props.disabled,
  extensions: [StarterKit.configure({ link: { openOnClick: false, autolink: true, defaultProtocol: 'https' } })],
  editorProps: {
    attributes: {
      id: props.inputId,
      role: 'textbox',
      'aria-label': props.label,
      'aria-multiline': 'true',
      class: 'rich-text-editor-content',
    },
  },
  onUpdate({ editor: current }) {
    emit('update:modelValue', current.isEmpty ? '' : current.getHTML())
  },
  onBlur() {
    emit('blur')
  },
})

watch(() => props.modelValue, (value) => {
  if (!editor.value) return
  const next = value ?? ''
  if ((editor.value.isEmpty ? '' : editor.value.getHTML()) !== next) editor.value.commands.setContent(next, { emitUpdate: false })
})

watch(() => props.disabled, (disabled) => editor.value?.setEditable(!disabled))

function setLink() {
  if (!editor.value) return
  const previous = editor.value.getAttributes('link').href as string | undefined
  const href = window.prompt('链接地址', previous ?? 'https://')?.trim()
  if (!href) return
  try {
    const parsed = new URL(href)
    if (!['http:', 'https:', 'mailto:'].includes(parsed.protocol)) throw new Error()
    editor.value.chain().focus().extendMarkRange('link').setLink({ href: parsed.href }).run()
  } catch {
    window.alert('仅支持 HTTP、HTTPS 或邮箱链接。')
  }
}

onBeforeUnmount(() => editor.value?.destroy())
</script>

<template>
  <div class="rich-text-control">
    <div class="rich-text-toolbar" role="toolbar" :aria-label="`${label}格式`">
      <a-tooltip title="粗体"><button type="button" :class="{ active: editor?.isActive('bold') }" aria-label="粗体" :disabled="disabled" @click="editor?.chain().focus().toggleBold().run()"><Bold :size="16" /></button></a-tooltip>
      <a-tooltip title="斜体"><button type="button" :class="{ active: editor?.isActive('italic') }" aria-label="斜体" :disabled="disabled" @click="editor?.chain().focus().toggleItalic().run()"><Italic :size="16" /></button></a-tooltip>
      <a-tooltip title="下划线"><button type="button" :class="{ active: editor?.isActive('underline') }" aria-label="下划线" :disabled="disabled" @click="editor?.chain().focus().toggleUnderline().run()"><Underline :size="16" /></button></a-tooltip>
      <a-tooltip title="无序列表"><button type="button" :class="{ active: editor?.isActive('bulletList') }" aria-label="无序列表" :disabled="disabled" @click="editor?.chain().focus().toggleBulletList().run()"><List :size="16" /></button></a-tooltip>
      <a-tooltip title="有序列表"><button type="button" :class="{ active: editor?.isActive('orderedList') }" aria-label="有序列表" :disabled="disabled" @click="editor?.chain().focus().toggleOrderedList().run()"><ListOrdered :size="16" /></button></a-tooltip>
      <a-tooltip title="添加链接"><button type="button" :class="{ active: editor?.isActive('link') }" aria-label="添加链接" :disabled="disabled" @click="setLink"><Link2 :size="16" /></button></a-tooltip>
      <a-tooltip title="移除链接"><button type="button" aria-label="移除链接" :disabled="disabled || !editor?.isActive('link')" @click="editor?.chain().focus().unsetLink().run()"><Unlink :size="16" /></button></a-tooltip>
      <span class="toolbar-spacer" />
      <a-tooltip title="撤销"><button type="button" aria-label="撤销" :disabled="disabled || !editor?.can().undo()" @click="editor?.chain().focus().undo().run()"><Undo2 :size="16" /></button></a-tooltip>
      <a-tooltip title="重做"><button type="button" aria-label="重做" :disabled="disabled || !editor?.can().redo()" @click="editor?.chain().focus().redo().run()"><Redo2 :size="16" /></button></a-tooltip>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>

<style scoped>
.rich-text-control{border:1px solid #d9d9d9;background:#fff}.rich-text-control:focus-within{border-color:#4096ff;box-shadow:0 0 0 2px rgb(5 145 255 / 10%)}.rich-text-toolbar{height:38px;display:flex;align-items:center;gap:2px;padding:3px 5px;border-bottom:1px solid #e2e7ea;background:#f7f9fa}.rich-text-toolbar button{width:30px;height:30px;display:grid;place-items:center;border:0;background:transparent;color:#52616d;cursor:pointer}.rich-text-toolbar button:hover:not(:disabled),.rich-text-toolbar button.active{background:#dfeeea;color:#087f73}.rich-text-toolbar button:disabled{color:#aeb7bd;cursor:not-allowed}.toolbar-spacer{flex:1}:deep(.rich-text-editor-content){min-height:132px;max-height:320px;overflow:auto;padding:10px 12px;outline:0;color:#273740;line-height:1.55}:deep(.rich-text-editor-content p){margin:0 0 8px}:deep(.rich-text-editor-content p:last-child){margin-bottom:0}:deep(.rich-text-editor-content ul),:deep(.rich-text-editor-content ol){padding-left:22px}:deep(.rich-text-editor-content a){color:#0969a8;text-decoration:underline}@media(max-width:480px){.rich-text-toolbar{height:auto;min-height:38px;flex-wrap:wrap}.toolbar-spacer{display:none}:deep(.rich-text-editor-content){min-height:150px}}
</style>
