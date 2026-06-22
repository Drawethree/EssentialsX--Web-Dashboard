<template>
  <div class="flex flex-wrap items-start justify-between gap-3 mb-1">
    <div class="min-w-0">
      <h1>{{ title }}</h1>
      <p v-if="description" class="text-sm text-muted mt-0.5">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="flex items-center gap-2 flex-shrink-0">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>
