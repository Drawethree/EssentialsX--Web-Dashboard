<script setup>
import { computed } from 'vue'

const props = defineProps({
  // Array of { weekday: 0-6 (0=Sunday), hour: 0-23, count }.
  buckets: { type: Array, default: () => [] },
  // CSS color used at full intensity; cells scale its opacity by relative count.
  color: { type: String, default: 'rgb(var(--color-brand-rgb))' },
})

// Display rows Monday-first (nicer to read); map back to SQLite's 0=Sunday indexing.
const ROWS = [
  { label: 'Mon', wd: 1 }, { label: 'Tue', wd: 2 }, { label: 'Wed', wd: 3 },
  { label: 'Thu', wd: 4 }, { label: 'Fri', wd: 5 }, { label: 'Sat', wd: 6 },
  { label: 'Sun', wd: 0 },
]
const HOURS = Array.from({ length: 24 }, (_, h) => h)

// Lookup table keyed "wd:hour" → count, plus the peak for scaling.
const grid = computed(() => {
  const map = {}
  let max = 0
  for (const b of props.buckets) {
    map[`${b.weekday}:${b.hour}`] = b.count
    if (b.count > max) max = b.count
  }
  return { map, max }
})

const total = computed(() => props.buckets.reduce((a, b) => a + b.count, 0))

function cell(wd, hour) {
  const count = grid.value.map[`${wd}:${hour}`] || 0
  // Floor non-zero buckets to a visible opacity so a single login still shows.
  const intensity = grid.value.max ? count / grid.value.max : 0
  const opacity = count ? 0.15 + intensity * 0.85 : 0
  return { count, opacity }
}
</script>

<template>
  <div class="w-full overflow-x-auto">
    <div v-if="total" class="min-w-[34rem]">
      <table class="border-separate" style="border-spacing: 2px">
        <tbody>
          <tr v-for="row in ROWS" :key="row.wd">
            <td class="pr-2 text-right text-[0.65rem] text-faint align-middle whitespace-nowrap">{{ row.label }}</td>
            <td v-for="h in HOURS" :key="h" class="p-0">
              <div
                class="h-4 w-4 rounded-[3px]"
                :style="{
                  backgroundColor: cell(row.wd, h).count ? color : 'rgb(var(--color-edge-rgb))',
                  opacity: cell(row.wd, h).count ? cell(row.wd, h).opacity : 0.25,
                }"
                :title="`${row.label} ${String(h).padStart(2, '0')}:00 — ${cell(row.wd, h).count} login${cell(row.wd, h).count === 1 ? '' : 's'}`"
              ></div>
            </td>
          </tr>
          <tr>
            <td></td>
            <td v-for="h in HOURS" :key="h" class="text-center">
              <span v-if="h % 6 === 0" class="text-[0.6rem] text-faint">{{ h }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="mt-2 text-xs text-muted">Login density by day &amp; hour (server local time) — {{ total }} logins.</p>
    </div>
    <div v-else class="flex h-32 items-center justify-center text-sm text-muted">
      No login history recorded yet.
    </div>
  </div>
</template>
