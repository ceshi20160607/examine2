<template>
  <button
    class="u-btn"
    :class="[`u-btn--${type || 'default'}`, disabled ? 'u-btn--disabled' : '']"
    :disabled="disabled"
    hover-class="u-btn--hover"
    @click="onClick"
  >
    <slot />
  </button>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    type?: 'default' | 'primary' | 'warn'
    disabled?: boolean
  }>(),
  {
    type: 'default',
    disabled: false
  }
)

const emit = defineEmits<{
  (e: 'click', ev: any): void
}>()

function onClick(ev: any) {
  if (props.disabled) return
  emit('click', ev)
}
</script>

<style scoped>
.u-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  min-height: 36px;
  line-height: 1;
  font-size: 14px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #333;
  box-sizing: border-box;
}

.u-btn--primary {
  background: #007aff;
  border-color: #007aff;
  color: #fff;
}

.u-btn--warn {
  background: #dd524d;
  border-color: #dd524d;
  color: #fff;
}

.u-btn--hover {
  opacity: 0.85;
}

.u-btn--disabled {
  opacity: 0.55;
}
</style>
