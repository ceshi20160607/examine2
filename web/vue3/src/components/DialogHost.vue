<template>
  <teleport to="body">
    <div v-if="dialogState.current" class="dialog-backdrop" @mousedown.self="cancel">
      <form class="dialog" @submit.prevent="submitDialog">
        <div class="dialog__head">
          <h3>{{ dialogState.current.title }}</h3>
          <button type="button" class="dialog__close" aria-label="关闭" @click="cancel">×</button>
        </div>
        <p v-if="dialogState.current.message" class="dialog__message">{{ dialogState.current.message }}</p>
        <template v-if="dialogState.current.kind === 'prompt'">
          <textarea
            v-if="dialogState.current.multiline"
            ref="inputRef"
            v-model="dialogState.current.value"
            class="dialog__input dialog__textarea"
            rows="7"
          />
          <input
            v-else
            ref="inputRef"
            v-model="dialogState.current.value"
            class="dialog__input"
          />
        </template>
        <div class="dialog__actions">
          <button type="button" class="secondary" @click="cancel">{{ dialogState.current.cancelText }}</button>
          <button type="submit" :class="{ danger: dialogState.current.danger }">
            {{ dialogState.current.confirmText }}
          </button>
        </div>
      </form>
    </div>
  </teleport>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { closeDialog, dialogState } from '../utils/dialog.js'

const inputRef = ref(null)

watch(
  () => dialogState.current?.id,
  async () => {
    await nextTick()
    inputRef.value?.focus()
  }
)

function submitDialog() {
  const cur = dialogState.current
  if (!cur) return
  closeDialog(cur.kind === 'prompt' ? cur.value : true)
}

function cancel() {
  closeDialog(null)
}
</script>

<style scoped>
.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9998;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(19, 25, 22, 0.42);
}
.dialog {
  width: min(460px, 100%);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-md);
  padding: 1rem;
}
.dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.dialog__head h3 {
  margin: 0;
  font-size: 1.05rem;
}
.dialog__close {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: #fff;
  color: var(--color-muted);
  cursor: pointer;
}
.dialog__message {
  margin: 0.75rem 0 0;
  white-space: pre-wrap;
  color: var(--color-muted);
}
.dialog__input {
  width: 100%;
  min-height: 40px;
  margin-top: 0.8rem;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
}
.dialog__textarea {
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.86rem;
}
.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 1rem;
}
.dialog__actions button {
  min-height: 36px;
  padding: 0.45rem 0.85rem;
  border: 1px solid var(--color-primary);
  border-radius: 7px;
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}
.dialog__actions button.secondary {
  background: #fff;
  color: var(--color-text);
  border-color: var(--color-border-strong);
}
.dialog__actions button.danger {
  background: var(--color-danger);
  border-color: var(--color-danger);
}
</style>
