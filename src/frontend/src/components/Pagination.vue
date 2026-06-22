<template>
  <div v-if="total > size" class="flex items-center justify-between text-sm pt-3">
    <button class="btn-ghost" :disabled="page === 0" @click="$emit('update:page', page - 1)">← Prev</button>
    <span class="text-muted">Page {{ page + 1 }} of {{ pages }} · {{ total.toLocaleString() }} total</span>
    <button class="btn-ghost" :disabled="page + 1 >= pages" @click="$emit('update:page', page + 1)">Next →</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  total: { type: Number, required: true },
})
defineEmits(['update:page'])

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
