<template>
  <teleport to="body">
    <div class="toast-host" aria-live="polite" aria-atomic="false">
      <button
        v-for="item in notifications"
        :key="item.id"
        type="button"
        class="toast"
        :class="`toast--${item.type}`"
        @click="remove(item.id)"
      >
        <span class="toast__mark"></span>
        <span class="toast__text">{{ item.message }}</span>
      </button>
    </div>
  </teleport>
</template>

<script setup>
import { notifications, remove } from '../utils/notify.js'
</script>

<style scoped>
.toast-host {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 9999;
  display: grid;
  gap: 10px;
  width: min(360px, calc(100vw - 32px));
  pointer-events: none;
}
.toast {
  display: grid;
  grid-template-columns: 10px 1fr;
  gap: 10px;
  align-items: start;
  width: 100%;
  padding: 12px 13px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
  color: var(--color-text);
  box-shadow: var(--shadow-md);
  text-align: left;
  cursor: pointer;
  pointer-events: auto;
}
.toast__mark {
  width: 9px;
  height: 9px;
  margin-top: 0.35em;
  border-radius: 50%;
  background: var(--color-primary);
}
.toast__text {
  overflow-wrap: anywhere;
  font-size: 0.92rem;
  line-height: 1.45;
}
.toast--success .toast__mark {
  background: #17803f;
}
.toast--error {
  border-color: #ffd3cf;
}
.toast--error .toast__mark {
  background: var(--color-danger);
}
.toast--warn .toast__mark {
  background: #b7791f;
}

@media (max-width: 640px) {
  .toast-host {
    top: 12px;
    right: 12px;
    width: calc(100vw - 24px);
  }
}
</style>
