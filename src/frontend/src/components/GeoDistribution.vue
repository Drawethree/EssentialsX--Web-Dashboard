<script setup>
import { computed } from 'vue'

const props = defineProps({
  // Array of { countryCode, country, players, logins }, players-descending.
  countries: { type: Array, default: () => [] },
  geoipInstalled: { type: Boolean, default: true },
})

const max = computed(() => props.countries.reduce((m, c) => Math.max(m, c.players), 0) || 1)
const totalPlayers = computed(() => props.countries.reduce((a, c) => a + c.players, 0))

// ISO 3166-1 alpha-2 → flag emoji via regional indicator symbols. "??" → globe.
function flag(code) {
  if (!code || code.length !== 2 || code === '??') return '🌐'
  const base = 0x1f1e6
  return String.fromCodePoint(...[...code.toUpperCase()].map(c => base + c.charCodeAt(0) - 65))
}
</script>

<template>
  <div>
    <div v-if="!geoipInstalled" class="rounded-md border border-edge bg-elevated px-3 py-2 text-xs text-muted mb-3">
      EssentialsXGeoIP is not installed — countries can't be resolved.
    </div>
    <ul v-if="countries.length" class="space-y-1.5">
      <li v-for="c in countries" :key="c.countryCode" class="flex items-center gap-3 text-sm">
        <span class="w-6 text-base leading-none">{{ flag(c.countryCode) }}</span>
        <span class="w-24 sm:w-40 truncate text-secondary">{{ c.country }}</span>
        <div class="flex-1 h-2 rounded-full bg-elevated overflow-hidden">
          <div class="h-full rounded-full" :style="{ width: (c.players / max * 100) + '%', backgroundColor: 'rgb(var(--color-brand-rgb))' }"></div>
        </div>
        <span class="w-10 text-right font-medium text-primary">{{ c.players }}</span>
      </li>
    </ul>
    <div v-else class="flex h-24 items-center justify-center text-sm text-muted">
      No location data yet.
    </div>
    <p v-if="countries.length" class="mt-2 text-xs text-muted">
      {{ countries.length }} countries · {{ totalPlayers }} located connections.
    </p>
  </div>
</template>
