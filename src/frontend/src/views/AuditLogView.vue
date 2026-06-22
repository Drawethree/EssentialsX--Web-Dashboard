<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1>Audit Log</h1>
        <p class="text-sm text-muted">Every action taken through the dashboard.</p>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <button class="btn-ghost" :disabled="!entries.length" @click="exportPage">Export page</button>
        <button class="btn-ghost" :disabled="!total || exporting" @click="exportAll">
          {{ exporting ? 'Exporting…' : 'Export all' }}
        </button>
        <button class="btn-ghost" @click="load">Refresh</button>
      </div>
    </div>

    <!-- Filters -->
    <div class="flex flex-col sm:flex-row gap-2">
      <div class="relative flex-1">
        <MagnifyingGlassIcon class="w-4 h-4 text-muted absolute left-3 top-1/2 -translate-y-1/2" />
        <input
          v-model="q"
          class="input pl-9"
          placeholder="Search user, action, or details…"
          @input="onSearch"
        />
      </div>
      <div class="sm:w-56">
        <Select
          v-model="action"
          :options="actionOptions"
          placeholder="All actions"
          aria-label="Filter by action"
          @update:model-value="onFilterChange"
        />
      </div>
    </div>

    <!-- Entries -->
    <div class="card p-0 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr>
            <th class="text-left px-4 py-2 whitespace-nowrap">Time</th>
            <th class="text-left px-4 py-2 hidden md:table-cell">User</th>
            <th class="text-left px-4 py-2">Action</th>
            <th class="text-left px-4 py-2 w-full">Details</th>
            <th class="text-left px-4 py-2 whitespace-nowrap hidden sm:table-cell">IP</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(e, i) in rows" :key="i" class="border-t border-edge align-top">
            <td class="px-4 py-2 text-muted whitespace-nowrap" :title="formatDateTime(e.timestamp)">{{ timeAgo(e.timestamp) }}</td>
            <td class="px-4 py-2 font-medium text-primary whitespace-nowrap hidden md:table-cell">{{ e.user }}</td>
            <td class="px-4 py-2 whitespace-nowrap">
              <span class="badge" :class="auditBadgeClass(e.action)">{{ auditLabel(e.action) }}</span>
            </td>
            <td class="px-4 py-2 text-secondary break-words font-mono text-xs">{{ e.details || '—' }}</td>
            <td class="px-4 py-2 text-muted font-mono text-xs whitespace-nowrap hidden sm:table-cell">{{ e.ip || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!entries.length" class="py-2">
        <p v-if="loading" class="text-sm text-muted text-center py-8">Loading…</p>
        <EmptyState
          v-else
          :icon="ClipboardDocumentListIcon"
          :title="isFiltered ? 'No entries match your filters' : 'No activity yet'"
          :hint="isFiltered ? 'Try a different search or action type.' : 'Actions taken through the dashboard will appear here.'"
        />
      </div>
    </div>

    <Pagination :page="page" :size="size" :total="total" @update:page="p => { page = p; load() }" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import { exportCsv } from '../utils/csv'
import { timeAgo, formatDateTime } from '../utils'
import { auditBadgeClass, auditLabel } from '../utils/auditActions'
import Pagination from '../components/Pagination.vue'
import Select from '../components/ui/Select.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { MagnifyingGlassIcon, ClipboardDocumentListIcon } from '@heroicons/vue/24/outline'

const toast = useToastStore()
const entries = ref([])
const page = ref(0)
const size = 50
const total = ref(0)
const loading = ref(true)
const q = ref('')
const action = ref('')
const actions = ref([])
const exporting = ref(false)
let searchTimer = null

// The backend appends the client IP to the details as a trailing "  ip=<ip>" token.
// Split it back out into its own field for display/export.
function splitIp(details) {
  const m = (details || '').match(/^(.*?)\s+ip=(\S+)\s*$/s)
  return m ? { details: m[1], ip: m[2] } : { details: details || '', ip: '' }
}
const rows = computed(() => entries.value.map(e => ({ ...e, ...splitIp(e.details) })))

const isFiltered = computed(() => !!q.value.trim() || !!action.value)
const actionOptions = computed(() => [
  { value: '', label: 'All actions' },
  ...actions.value.map(a => ({ value: a, label: auditLabel(a) })),
])

async function load() {
  loading.value = true
  try {
    const { data } = await api.auditLog(page.value, size, q.value.trim(), action.value)
    entries.value = data.entries
    total.value = data.total
    // The action list spans the whole file regardless of filter — only refresh when not filtering
    // so the dropdown keeps every option available.
    if (data.actions && !action.value) actions.value = data.actions
  } catch { /* ignore */ } finally { loading.value = false }
}

function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 0; load() }, 250)
}

function onFilterChange() {
  page.value = 0
  load()
}

const CSV_COLUMNS = [
  { key: 'time', label: 'Time' },
  { key: 'user', label: 'User' },
  { key: 'action', label: 'Action' },
  { key: 'details', label: 'Details' },
  { key: 'ip', label: 'IP' },
]

function toRow(e) {
  const { details, ip } = splitIp(e.details)
  return {
    time: formatDateTime(e.timestamp),
    user: e.user,
    action: auditLabel(e.action),
    details,
    ip,
  }
}

function exportPage() {
  exportCsv('audit-log.csv', entries.value.map(toRow), CSV_COLUMNS)
}

// Paginate through every entry matching the active filters (mirrors EconomyView's baltop
// export), then download a single CSV.
async function exportAll() {
  if (exporting.value) return
  exporting.value = true
  try {
    const pageSize = 500
    const all = []
    for (let p = 0; ; p++) {
      const { data } = await api.auditLog(p, pageSize, q.value.trim(), action.value)
      all.push(...data.entries)
      if (all.length >= data.total || data.entries.length === 0) break
    }
    if (!all.length) return toast.info('No entries to export')
    exportCsv('audit-log.csv', all.map(toRow), CSV_COLUMNS)
  } catch {
    toast.error('Failed to export audit log')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>
