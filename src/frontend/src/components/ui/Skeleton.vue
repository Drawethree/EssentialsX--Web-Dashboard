<template>
  <div class="relative overflow-hidden rounded bg-elevated" :class="rounded" :style="style">
    <div class="absolute inset-0 -translate-x-full animate-shimmer bg-gradient-to-r from-transparent via-white/10 to-transparent"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  width: { type: String, default: '100%' },
  height: { type: String, default: '1rem' },
  rounded: { type: String, default: 'rounded' },
})

const style = computed(() => ({ width: props.width, height: props.height }))
</script>
