<template>
  <div class="p-4 sm:p-6 max-w-6xl mx-auto space-y-6">
    <PageHeader title="Overview" description="Live snapshot of your server." />

    <!-- Stat cards -->
    <div v-if="data" class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="card">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 rounded-lg bg-brand-subtle text-brand flex items-center justify-center flex-shrink-0"><UsersIcon class="w-5 h-5" /></span>
          <div>
            <p class="text-xs text-muted uppercase tracking-wide">Online</p>
            <p class="text-2xl font-bold text-primary leading-tight">{{ onlineCount }} <span class="text-sm text-muted font-normal">/ {{ data.maxPlayers }}</span></p>
          </div>
        </div>
        <Sparkline v-if="onlineSeries.length > 1" :values="onlineSeries" :width="240" :height="32" class="w-full mt-3" />
      </div>
      <div class="card">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0" :class="tpsBg"><BoltIcon class="w-5 h-5" /></span>
          <div>
            <p class="text-xs text-muted uppercase tracking-wide">TPS</p>
            <p class="text-2xl font-bold leading-tight" :class="tpsColor">{{ data.tps ?? '—' }}</p>
          </div>
        </div>
        <Sparkline v-if="tpsSeries.length > 1" :values="tpsSeries" :width="240" :height="32" class="w-full mt-3" />
      </div>
      <div class="card">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 rounded-lg bg-brand-subtle text-brand flex items-center justify-center flex-shrink-0"><CircleStackIcon class="w-5 h-5" /></span>
          <div>
            <p class="text-xs text-muted uppercase tracking-wide">Memory</p>
            <p class="text-2xl font-bold text-primary leading-tight">{{ data.memoryUsedMb }}<span class="text-sm text-muted font-normal">/{{ data.memoryMaxMb }}MB</span></p>
          </div>
        </div>
        <Sparkline v-if="memorySeries.length > 1" :values="memorySeries" :width="240" :height="32" class="w-full mt-3" />
      </div>
      <div class="card">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 rounded-lg bg-brand-subtle text-brand flex items-center justify-center flex-shrink-0"><GlobeAltIcon class="w-5 h-5" /></span>
          <div>
            <p class="text-xs text-muted uppercase tracking-wide">Known Players</p>
            <p class="text-2xl font-bold text-primary leading-tight">{{ data.indexedPlayers.toLocaleString() }}</p>
          </div>
        </div>
        <Sparkline v-if="economySeries.length > 1" :values="economySeries" :width="240" :height="32" class="w-full mt-3" color="rgb(34 197 94)" />
      </div>
    </div>
    <div v-else class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <Skeleton v-for="i in 4" :key="i" height="4.75rem" rounded="rounded-xl" />
    </div>

    <!-- Quick actions -->
    <div class="flex flex-wrap gap-2">
      <router-link v-if="hasPerm('PLAYERS_VIEW')" to="/players" class="btn-subtle"><UsersIcon class="w-4 h-4" /> Players</router-link>
      <router-link v-if="hasPerm('CONSOLE_VIEW')" to="/console" class="btn-subtle"><CommandLineIcon class="w-4 h-4" /> Console</router-link>
      <router-link v-if="hasPerm('ECONOMY_VIEW')" to="/economy" class="btn-subtle"><BanknotesIcon class="w-4 h-4" /> Economy</router-link>
      <router-link v-if="hasPerm('BROADCAST')" to="/tools" class="btn-subtle"><MegaphoneIcon class="w-4 h-4" /> Broadcast</router-link>
    </div>

    <div v-if="data" class="grid lg:grid-cols-3 gap-4">
      <div class="card lg:col-span-2">
        <div class="flex items-center justify-between mb-3">
          <h3>Online Players</h3>
          <span class="badge-online">{{ onlineList.length }} online</span>
        </div>
        <div v-if="onlineList.length" class="grid sm:grid-cols-2 gap-2">
          <router-link
            v-for="p in onlineList" :key="p.uuid" :to="`/players/${p.uuid}`"
            class="flex items-center gap-2 px-3 py-2 rounded-lg bg-elevated hover:bg-brand-subtle transition-colors"
          >
            <Avatar :uuid="p.uuid" :name="p.name" :size="28" />
            <span class="text-sm font-medium text-primary truncate">{{ p.name }}</span>
            <span class="text-xs text-muted ml-auto truncate">{{ p.world }}</span>
          </router-link>
        </div>
        <EmptyState v-else :icon="UsersIcon" title="No players online" hint="Players will appear here as they join." />
      </div>

      <div class="space-y-4">
        <div class="card">
          <h3 class="mb-3">Server</h3>
          <dl class="space-y-2 text-sm">
            <div v-if="data.serverAddress" class="flex justify-between items-center gap-2">
              <dt class="text-muted">Address</dt>
              <dd class="text-primary text-right truncate min-w-0 flex items-center justify-end gap-1">
                <span class="truncate">{{ data.serverAddress }}</span>
                <button class="text-muted hover:text-brand flex-shrink-0" title="Copy address" @click="copy(data.serverAddress, 'Server address copied')">
                  <ClipboardDocumentIcon class="w-3.5 h-3.5" />
                </button>
              </dd>
            </div>
            <div class="flex justify-between"><dt class="text-muted">Version</dt><dd class="text-primary text-right truncate max-w-[60%]">{{ data.bukkitVersion }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted">EssentialsX</dt><dd class="text-primary">{{ data.essentialsVersion }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted">Uptime</dt><dd class="text-primary">{{ uptime(data.uptimeMs) }}</dd></div>
            <div class="flex justify-between"><dt class="text-muted">Currency</dt><dd class="text-primary">{{ data.symbol }}</dd></div>
          </dl>
        </div>

        <div v-if="hasPerm('AUDIT_LOG')" class="card">
          <div class="flex items-center justify-between mb-3">
            <h3>Recent Activity</h3>
            <router-link to="/audit-log" class="text-xs text-brand hover:underline">View all</router-link>
          </div>
          <ul v-if="activity.length" class="space-y-2">
            <li v-for="(e, i) in activity" :key="i" class="flex items-start gap-2 text-sm">
              <span class="badge mt-0.5 flex-shrink-0" :class="auditBadgeClass(e.action)">{{ auditLabel(e.action) }}</span>
              <div class="min-w-0 flex-1">
                <p class="text-secondary break-words leading-snug">
                  <span class="font-medium text-primary">{{ e.user }}</span>
                  <span v-if="e.details" class="text-muted"> · {{ e.details }}</span>
                </p>
                <p class="text-xs text-faint" :title="formatDateTime(e.timestamp)">{{ timeAgo(e.timestamp) }}</p>
              </div>
            </li>
          </ul>
          <p v-else class="text-sm text-muted">No recent activity.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, openEventStream } from '../api'
import { uptime, timeAgo, formatDateTime } from '../utils'
import { auditLabel, auditBadgeClass } from '../utils/auditActions'
import { useAuthStore } from '../stores/auth'
import { useClipboard } from '../composables/useClipboard'
import Avatar from '../components/Avatar.vue'
import Sparkline from '../components/Sparkline.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import Skeleton from '../components/ui/Skeleton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import {
  UsersIcon, BoltIcon, CircleStackIcon, GlobeAltIcon, CommandLineIcon, BanknotesIcon, MegaphoneIcon,
  ClipboardDocumentIcon,
} from '@heroicons/vue/24/outline'

const auth = useAuthStore()
const hasPerm = p => auth.hasPermission(p)
const copy = useClipboard()

const data = ref(null)
const onlineList = ref([])
const activity = ref([])
const history = ref([])
let stream = null
let poll = null

const onlineSeries = computed(() => history.value.map(s => s.online))
const tpsSeries = computed(() => history.value.map(s => s.tps).filter(v => v != null))
const memorySeries = computed(() => history.value.map(s => s.memoryUsedMb))
const economySeries = computed(() =>
  history.value.map(s => (s.totalEconomy != null ? Number(s.totalEconomy) : null)).filter(v => v != null && !Number.isNaN(v)))

const onlineCount = computed(() => onlineList.value.length)
const tpsColor = computed(() => {
  const t = data.value?.tps
  if (t == null) return 'text-muted'
  return t >= 19 ? 'text-green-500' : t >= 15 ? 'text-amber-500' : 'text-red-500'
})
const tpsBg = computed(() => {
  const t = data.value?.tps
  if (t == null) return 'bg-elevated text-muted'
  return t >= 19 ? 'bg-green-500/15 text-green-500' : t >= 15 ? 'bg-amber-500/15 text-amber-500' : 'bg-red-500/15 text-red-500'
})

async function load() {
  try {
    const { data: d } = await api.overview()
    data.value = d
    onlineList.value = d.online
  } catch { /* ignore */ }
}

async function loadActivity() {
  if (!hasPerm('AUDIT_LOG')) return
  try { activity.value = (await api.auditLog(0, 8)).data.entries } catch { /* ignore */ }
}

async function loadHistory() {
  try { history.value = (await api.analyticsHistory('24h')).data.samples } catch { /* ignore */ }
}

onMounted(() => {
  load()
  loadActivity()
  loadHistory()
  poll = setInterval(load, 10000)
  stream = openEventStream()
  stream.addEventListener('player-join', () => load())
  stream.addEventListener('player-quit', () => load())
})

onUnmounted(() => {
  if (stream) stream.close()
  if (poll) clearInterval(poll)
})
</script>
