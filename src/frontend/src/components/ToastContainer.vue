<template>
  <div class="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 w-80 max-w-[calc(100vw-2rem)]">
    <transition-group name="toast">
      <div
        v-for="t in toast.toasts"
        :key="t.id"
        class="px-4 py-3 rounded-lg shadow-lg text-sm flex items-start gap-2 animate-slide-up-fade"
        :class="`toast-${t.type}`"
        role="status"
      >
        <component :is="icons[t.type]" class="w-4 h-4 mt-0.5 flex-shrink-0" />
        <div class="flex-1 min-w-0">
          <span>{{ t.message }}</span>
          <button
            v-if="t.action"
            class="block mt-1 font-semibold underline underline-offset-2 opacity-90 hover:opacity-100"
            @click="toast.runAction(t.id)"
          >{{ t.action.label }}</button>
        </div>
        <button class="opacity-60 hover:opacity-100" aria-label="Dismiss" @click="toast.remove(t.id)">✕</button>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { useToastStore } from '../stores/toast'
import { CheckCircleIcon, XCircleIcon, InformationCircleIcon } from '@heroicons/vue/24/solid'

const toast = useToastStore()
const icons = { success: CheckCircleIcon, error: XCircleIcon, info: InformationCircleIcon }
</script>

<style scoped>
.toast-enter-active, .toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }
</style>
