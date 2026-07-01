<template>
  <div class="p-4 sm:p-6 max-w-6xl mx-auto space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1>Analytics</h1>
        <p class="text-sm text-muted">Historical server trends sampled every 5 minutes.</p>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <div class="w-32">
          <Select v-model="range" :options="rangeOptions" aria-label="Time range" @update:model-value="load" />
        </div>
        <button class="btn-ghost" @click="load">Refresh</button>
      </div>
    </div>

    <div v-if="loading" class="grid sm:grid-cols-2 gap-4">
      <Skeleton v-for="i in 4" :key="i" class="h-56" />
    </div>

    <EmptyState
      v-else-if="!samples.length"
      :icon="ChartBarIcon"
      title="No data yet"
      hint="Trends appear once the server has been running long enough to collect samples."
    />

    <div v-else class="grid sm:grid-cols-2 gap-4">
      <div v-for="m in charts" :key="m.key" class="card space-y-3">
        <div class="flex items-center justify-between">
          <h3 class="flex items-center gap-2">
            <component :is="m.icon" class="w-4 h-4 text-brand" /> {{ m.label }}
          </h3>
          <span class="text-sm font-semibold text-primary">{{ m.format(m.stats.current) }}</span>
        </div>
        <TimeSeriesChart :points="m.points" :color="m.color" :format="m.format" :height="180" />
        <div class="grid grid-cols-3 gap-2 text-center pt-1 border-t border-edge">
          <div><p class="text-xs text-muted">Current</p><p class="text-sm font-medium text-primary">{{ m.format(m.stats.current) }}</p></div>
          <div><p class="text-xs text-muted">Average</p><p class="text-sm font-medium text-primary">{{ m.format(m.stats.avg) }}</p></div>
          <div><p class="text-xs text-muted">Peak</p><p class="text-sm font-medium text-primary">{{ m.format(m.stats.peak) }}</p></div>
        </div>
      </div>
    </div>

    <!-- Login activity + geography (independent of the metric range; span all retained login history) -->
    <div v-if="!loading" class="grid gap-4 lg:grid-cols-2">
      <div class="card space-y-3">
        <h3 class="flex items-center gap-2"><CalendarDaysIcon class="w-4 h-4 text-brand" /> Login Activity</h3>
        <ActivityHeatmap :buckets="heatmap" />
      </div>
      <div class="card space-y-3">
        <h3 class="flex items-center gap-2"><GlobeAltIcon class="w-4 h-4 text-brand" /> Player Geography</h3>
        <GeoDistribution :countries="geo.countries" :geoip-installed="geo.geoipInstalled" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import { formatMoney } from '../utils'
import Select from '../components/ui/Select.vue'
import TimeSeriesChart from '../components/TimeSeriesChart.vue'
import ActivityHeatmap from '../components/ActivityHeatmap.vue'
import GeoDistribution from '../components/GeoDistribution.vue'
import Skeleton from '../components/ui/Skeleton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { UsersIcon, CpuChipIcon, CircleStackIcon, BanknotesIcon, ChartBarIcon, CalendarDaysIcon, GlobeAltIcon, Squares2X2Icon, BugAntIcon } from '@heroicons/vue/24/outline'

const range = ref('24h')
const samples = ref([])
const heatmap = ref([])
const geo = ref({ countries: [], geoipInstalled: true })
const loading = ref(true)
const symbol = ref('$')

const rangeOptions = [
  { value: '1h', label: 'Last hour' },
  { value: '24h', label: 'Last 24h' },
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
]

// Reduce a numeric series (nulls skipped) to current / average / peak.
function statsOf(values) {
  const nums = values.filter(v => v !== null && v !== undefined && !Number.isNaN(v))
  if (!nums.length) return { current: null, avg: null, peak: null }
  return {
    current: nums[nums.length - 1],
    avg: nums.reduce((a, b) => a + b, 0) / nums.length,
    peak: Math.max(...nums),
  }
}

const round1 = n => (n == null ? '—' : Math.round(n * 10) / 10)
const intFmt = n => (n == null ? '—' : Math.round(n))

// Build { ts, value } points for a metric, applying an optional value transform.
const pointsOf = (pick) => samples.value.map(s => ({ ts: s.ts, value: pick(s) }))

const charts = computed(() => {
  const online = pointsOf(s => s.online)
  const tps = pointsOf(s => (s.tps == null ? null : Math.min(20, s.tps)))
  const mem = pointsOf(s => s.memoryUsedMb)
  const eco = pointsOf(s => (s.totalEconomy == null ? null : Number(s.totalEconomy)))
  const chunks = pointsOf(s => s.loadedChunks)
  const entities = pointsOf(s => s.entities)
  const valuesOf = pts => pts.map(p => p.value)
  const out = [
    { key: 'online', label: 'Players Online', icon: UsersIcon, color: 'rgb(var(--color-brand-rgb))', points: online, stats: statsOf(valuesOf(online)), format: intFmt },
    { key: 'tps', label: 'TPS', icon: CpuChipIcon, color: '#22c55e', points: tps, stats: statsOf(valuesOf(tps)), format: round1 },
    { key: 'mem', label: 'Memory (MB)', icon: CircleStackIcon, color: '#f59e0b', points: mem, stats: statsOf(valuesOf(mem)), format: intFmt },
    { key: 'eco', label: 'Total Economy', icon: BanknotesIcon, color: '#8b5cf6', points: eco, stats: statsOf(valuesOf(eco)), format: v => (v == null ? '—' : formatMoney(v, symbol.value)) },
    { key: 'chunks', label: 'Loaded Chunks', icon: Squares2X2Icon, color: '#0ea5e9', points: chunks, stats: statsOf(valuesOf(chunks)), format: intFmt },
    { key: 'entities', label: 'Entities', icon: BugAntIcon, color: '#ec4899', points: entities, stats: statsOf(valuesOf(entities)), format: intFmt },
  ]
  // Hide the newer metrics entirely until at least one sample carries them (older DBs store null).
  return out.filter(m => !['chunks', 'entities'].includes(m.key) || m.stats.current != null)
})

async function load() {
  loading.value = true
  try {
    samples.value = (await api.analyticsHistory(range.value)).data.samples
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try { symbol.value = (await api.overview()).data.symbol || '$' } catch { /* keep default */ }
  try { heatmap.value = (await api.activityHeatmap()).data.buckets } catch { /* ignore */ }
  try { geo.value = (await api.geoDistribution()).data } catch { /* ignore */ }
  load()
})
</script>
