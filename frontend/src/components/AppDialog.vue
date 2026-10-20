<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

const props = defineProps<{ open: boolean; title: string; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const titleId = useId()
let previousOverflow = ''

function synchronize() {
  if (props.open && !dialog.value?.open) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.value?.showModal()
  } else if (!props.open && dialog.value?.open) {
    dialog.value.close()
    document.body.style.overflow = previousOverflow
  }
}
function close() { if (!props.busy) emit('close') }
watch(() => props.open, synchronize, { flush: 'post' })
onMounted(synchronize)
onBeforeUnmount(() => {
  if (dialog.value?.open) {
    dialog.value.close()
    document.body.style.overflow = previousOverflow
  }
})
</script>

<template>
  <dialog ref="dialog" class="app-dialog" :aria-labelledby="titleId" :aria-busy="busy" @cancel.prevent="close">
    <header class="dialog-head">
      <h2 :id="titleId" class="display">{{ title }}</h2>
      <button class="action-button" type="button" :disabled="busy" aria-label="关闭对话框" @click="close">关闭</button>
    </header>
    <slot />
  </dialog>
</template>

<style scoped>
.app-dialog {
  width: min(640px, calc(100vw - 24px));
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: var(--space-5);
  overflow-y: auto;
  border: 1px solid var(--glass-stroke);
  border-radius: var(--radius-xl);
  background: var(--dawn-top);
  color: var(--ink);
  box-shadow: var(--shadow-soft);
}
.app-dialog::backdrop { background: rgba(14, 33, 27, 0.38); }
.dialog-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 24px; }
.dialog-head h2 { font-size: var(--fs-h2); }
@media (max-width: 480px) { .app-dialog { padding: var(--space-4); } }
</style>
