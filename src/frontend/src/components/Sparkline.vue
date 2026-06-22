<script setup>
import { computed } from 'vue'

const props = defineProps({
  // Array of numbers (nulls are skipped). Oldest → newest.
  values: { type: Array, default: () => [] },
  width: { type: Number, default: 120 },
  height: { type: Number, default: 36 },
  // CSS color; defaults to the brand accent.
  color: { type: String, default: 'rgb(var(--color-brand-rgb))' },
  strokeWidth: { type: Number, default: 2 },
})

const points = computed(() => props.values.filter(v => v !== null && v !== undefined && !Number.isNaN(v)))

const path = computed(() => {
  const vals = points.value
  if (vals.length < 2) return null
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const range = max - min || 1
  const pad = props.strokeWidth
  const w = props.width - pad * 2
  const h = props.height - pad * 2
  const step = w / (vals.length - 1)
  return vals.map((v, i) => {
    const x = pad + i * step
    const y = pad + h - ((v - min) / range) * h
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})

const areaPath = computed(() => {
  if (!path.value) return null
  const pad = props.strokeWidth
  const w = props.width - pad
  const h = props.height - pad
  return `${path.value} L${w.toFixed(1)},${h.toFixed(1)} L${pad},${h.toFixed(1)} Z`
})

const gradId = `spark-${Math.random().toString(36).slice(2, 8)}`
</script>

<template>
  <svg v-if="path" :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`"
       class="overflow-visible" preserveAspectRatio="none">
    <defs>
      <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="color" stop-opacity="0.25" />
        <stop offset="100%" :stop-color="color" stop-opacity="0" />
      </linearGradient>
    </defs>
    <path :d="areaPath" :fill="`url(#${gradId})`" stroke="none" />
    <path :d="path" fill="none" :stroke="color" :stroke-width="strokeWidth"
          stroke-linecap="round" stroke-linejoin="round" />
  </svg>
  <div v-else class="flex items-center justify-center text-xs text-slate-400 dark:text-slate-500"
       :style="{ width: width + 'px', height: height + 'px' }">
    —
  </div>
</template>
