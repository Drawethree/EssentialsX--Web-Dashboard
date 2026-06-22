<template>
  <div class="flex flex-col items-center justify-center text-center py-10 px-4">
    <component :is="icon" v-if="icon" class="w-10 h-10 text-faint mb-3" />
    <p class="text-sm font-medium text-secondary">{{ title }}</p>
    <p v-if="hint" class="text-xs text-muted mt-1 max-w-xs">{{ hint }}</p>
    <div v-if="$slots.action" class="mt-3">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: [Object, Function], default: null },
  title: { type: String, default: 'Nothing here yet' },
  hint: { type: String, default: '' },
})
</script>
