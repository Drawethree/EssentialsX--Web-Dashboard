<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { formatDateTime } from '../utils'

const props = defineProps({
  // Array of { ts: epoch-ms, value: number|null }, oldest → newest. Nulls/NaN skipped.
  points: { type: Array, default: () => [] },
  // CSS color; defaults to the brand accent.
  color: { type: String, default: 'rgb(var(--color-brand-rgb))' },
  height: { type: Number, default: 200 },
  // Formats y-axis ticks and the tooltip value.
  format: { type: Function, default: v => (v == null ? '—' : String(Math.round(v))) },
})

// ── Responsive width via ResizeObserver (keeps text crisp + hover mapping exact) ──
const wrap = ref(null)
const width = ref(600)
let ro = null
onMounted(() => {
  if (!wrap.value) return
  width.value = wrap.value.clientWidth || 600
  ro = new ResizeObserver(entries => {
    for (const e of entries) if (e.contentRect.width) width.value = e.contentRect.width
  })
  ro.observe(wrap.value)
})
onUnmounted(() => { if (ro) ro.disconnect() })

// Inner gutters reserved for axis labels.
const padTop = 12, padRight = 14, padBottom = 26, padLeft = 54

const clean = computed(() =>
  props.points.filter(p => p && p.ts != null && p.value != null && !Number.isNaN(Number(p.value)))
    .map(p => ({ ts: Number(p.ts), value: Number(p.value) })))

const plotW = computed(() => Math.max(10, width.value - padLeft - padRight))
const plotH = computed(() => Math.max(10, props.height - padTop - padBottom))

const tsMin = computed(() => (clean.value.length ? clean.value[0].ts : 0))
const tsMax = computed(() => (clean.value.length ? clean.value[clean.value.length - 1].ts : 1))

// ── "Nice" y-axis scale ──────────────────────────────────────────────────────
function niceNum(range, round) {
  const exp = Math.floor(Math.log10(range || 1))
  const frac = (range || 1) / Math.pow(10, exp)
  let nf
  if (round) nf = frac < 1.5 ? 1 : frac < 3 ? 2 : frac < 7 ? 5 : 10
  else nf = frac <= 1 ? 1 : frac <= 2 ? 2 : frac <= 5 ? 5 : 10
  return nf * Math.pow(10, exp)
}
const yScale = computed(() => {
  const vals = clean.value.map(p => p.value)
  let min = vals.length ? Math.min(...vals) : 0
  let max = vals.length ? Math.max(...vals) : 1
  if (min === max) { const pad = Math.abs(min) || 1; min -= pad; max += pad }
  const range = niceNum(max - min, false)
  const step = niceNum(range / 3, true)
  const niceMin = Math.floor(min / step) * step
  const niceMax = Math.ceil(max / step) * step
  const ticks = []
  for (let v = niceMin; v <= niceMax + step * 0.5; v += step) ticks.push(v)
  return { min: niceMin, max: niceMax, ticks }
})

function xOf(ts) {
  const span = tsMax.value - tsMin.value || 1
  return padLeft + ((ts - tsMin.value) / span) * plotW.value
}
function yOf(v) {
  const { min, max } = yScale.value
  const range = max - min || 1
  return padTop + plotH.value - ((v - min) / range) * plotH.value
}

// ── X-axis time ticks (granularity follows the span) ─────────────────────────
function fmtTick(ts, intraday) {
  const d = new Date(ts)
  return intraday
    ? d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}
const xTicks = computed(() => {
  if (clean.value.length < 2) return []
  const span = tsMax.value - tsMin.value || 1
  const intraday = span <= 36 * 3_600_000
  const count = Math.min(5, Math.max(2, Math.round(plotW.value / 120)))
  const out = []
  for (let i = 0; i < count; i++) {
    const ts = tsMin.value + (span * i) / (count - 1)
    out.push({ ts, x: xOf(ts), label: fmtTick(ts, intraday) })
  }
  return out
})

const baseY = computed(() => padTop + plotH.value)
const linePath = computed(() => {
  const pts = clean.value
  if (pts.length < 2) return null
  return pts.map((p, i) => `${i ? 'L' : 'M'}${xOf(p.ts).toFixed(1)},${yOf(p.value).toFixed(1)}`).join(' ')
})
const areaPath = computed(() => {
  if (!linePath.value) return null
  const pts = clean.value
  const x0 = xOf(pts[0].ts).toFixed(1)
  const x1 = xOf(pts[pts.length - 1].ts).toFixed(1)
  const y = baseY.value.toFixed(1)
  return `${linePath.value} L${x1},${y} L${x0},${y} Z`
})

// ── Hover crosshair + tooltip ────────────────────────────────────────────────
const hoverIdx = ref(null)
function onMove(e) {
  const pts = clean.value
  if (pts.length < 2 || !wrap.value) return
  const mx = e.clientX - wrap.value.getBoundingClientRect().left
  let best = 0, bestD = Infinity
  for (let i = 0; i < pts.length; i++) {
    const d = Math.abs(xOf(pts[i].ts) - mx)
    if (d < bestD) { bestD = d; best = i }
  }
  hoverIdx.value = best
}
function onLeave() { hoverIdx.value = null }

const hoverPoint = computed(() => (hoverIdx.value == null ? null : clean.value[hoverIdx.value]))
const hoverX = computed(() => (hoverPoint.value ? xOf(hoverPoint.value.ts) : 0))
const hoverY = computed(() => (hoverPoint.value ? yOf(hoverPoint.value.value) : 0))
const tooltipStyle = computed(() => {
  if (!hoverPoint.value) return {}
  const left = Math.min(Math.max(hoverX.value, 70), width.value - 70)
  return { left: left + 'px', top: Math.max(hoverY.value - 12, 4) + 'px' }
})

const gradId = `tsc-${Math.random().toString(36).slice(2, 8)}`
</script>

<template>
  <div ref="wrap" class="relative w-full select-none" :style="{ height: height + 'px' }">
    <svg v-if="linePath" :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`"
         @mousemove="onMove" @mouseleave="onLeave">
      <defs>
        <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="color" stop-opacity="0.22" />
          <stop offset="100%" :stop-color="color" stop-opacity="0" />
        </linearGradient>
      </defs>

      <!-- Y gridlines + value labels -->
      <g class="chart-grid">
        <template v-for="(t, i) in yScale.ticks" :key="'y' + i">
          <line :x1="padLeft" :y1="yOf(t)" :x2="width - padRight" :y2="yOf(t)" />
          <text :x="padLeft - 8" :y="yOf(t) + 3" text-anchor="end" class="chart-label">{{ format(t) }}</text>
        </template>
      </g>

      <!-- X axis baseline + time labels -->
      <line :x1="padLeft" :y1="baseY" :x2="width - padRight" :y2="baseY" class="chart-axis" />
      <g class="chart-grid">
        <template v-for="(t, i) in xTicks" :key="'x' + i">
          <text :x="t.x" :y="height - 8"
                :text-anchor="i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'"
                class="chart-label">{{ t.label }}</text>
        </template>
      </g>

      <!-- Area + line -->
      <path :d="areaPath" :fill="`url(#${gradId})`" stroke="none" />
      <path :d="linePath" fill="none" :stroke="color" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" />

      <!-- Hover crosshair + marker -->
      <g v-if="hoverPoint">
        <line :x1="hoverX" :y1="padTop" :x2="hoverX" :y2="baseY" class="chart-crosshair" />
        <circle :cx="hoverX" :cy="hoverY" r="4" :fill="color"
                stroke="var(--color-bg-surface)" stroke-width="2" />
      </g>
    </svg>

    <!-- Tooltip (HTML, so it can use the design tokens) -->
    <div v-if="hoverPoint"
         class="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-md
                border border-edge bg-elevated px-2.5 py-1.5 text-xs shadow-lg"
         :style="tooltipStyle">
      <p class="font-semibold text-primary">{{ format(hoverPoint.value) }}</p>
      <p class="text-muted">{{ formatDateTime(hoverPoint.ts) }}</p>
    </div>

    <div v-if="!linePath" class="flex h-full items-center justify-center text-sm text-muted">
      Not enough data to chart yet.
    </div>
  </div>
</template>

<style scoped>
.chart-grid line { stroke: rgb(var(--color-edge-rgb)); stroke-width: 1; stroke-opacity: 0.7; }
.chart-axis { stroke: rgb(var(--color-edge-rgb)); stroke-width: 1; }
.chart-label { fill: var(--color-text-faint); font-size: 11px; }
.chart-crosshair { stroke: rgb(var(--color-edge-rgb)); stroke-width: 1; stroke-dasharray: 3 3; }
</style>
