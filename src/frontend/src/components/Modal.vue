<template>
  <teleport to="body">
    <transition name="fade">
      <div v-if="open" class="fixed inset-0 z-[80] flex items-center justify-center p-4" role="dialog" aria-modal="true" :aria-label="title">
        <div class="absolute inset-0 bg-black/50" @click="$emit('close')"></div>
        <div
          class="relative bg-overlay border border-edge rounded-xl shadow-lg w-full max-h-[90vh] overflow-auto animate-slide-up-fade"
          :class="sizeClass"
        >
          <div class="flex items-center justify-between px-5 py-3 border-b border-edge">
            <h3 class="text-base font-semibold text-primary">{{ title }}</h3>
            <button class="text-muted hover:text-primary rounded focus-visible:ring-2 focus-visible:ring-brand/50" aria-label="Close" @click="$emit('close')">✕</button>
          </div>
          <div class="p-5">
            <slot />
          </div>
          <div v-if="$slots.footer" class="flex justify-end gap-2 px-5 py-3 border-t border-edge">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { computed, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  open: Boolean,
  title: String,
  size: { type: String, default: 'md' }, // sm | md | lg | xl
})
const emit = defineEmits(['close'])

const sizeClass = computed(() => ({
  sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-2xl', xl: 'max-w-4xl',
}[props.size] || 'max-w-md'))

function onKey(e) {
  if (e.key === 'Escape' && props.open) emit('close')
}

watch(() => props.open, open => {
  if (open) window.addEventListener('keydown', onKey)
  else window.removeEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
