<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-4">
    <div>
      <h1>Players</h1>
      <p class="text-sm text-muted">Search and manage any player — online or offline.</p>
    </div>

    <div class="flex items-center gap-2 flex-wrap">
      <input v-model="q" class="input max-w-sm" placeholder="Search by name…" @input="debouncedSearch" />
      <div class="w-36"><Select v-model="status" :options="statusOptions" aria-label="Status filter" @update:model-value="applyFilters" /></div>
      <div class="w-40"><Select v-model="seen" :options="seenOptions" aria-label="Last seen filter" @update:model-value="applyFilters" /></div>
      <div class="w-40"><Select v-model="sort" :options="sortOptions" aria-label="Sort order" @update:model-value="applyFilters" /></div>
      <button class="btn-ghost text-xs" :disabled="!players.length" @click="exportPage">Export CSV</button>
    </div>

    <!-- Bulk action bar — appears once players are selected (requires PLAYERS_MANAGE). -->
    <div v-if="canBulk && selected.length" class="card flex flex-wrap items-end gap-3 border-brand/40">
      <div>
        <span class="badge-info">{{ selected.length }} selected</span>
      </div>
      <div class="w-44">
        <label class="label">Action</label>
        <Select v-model="bulkOp" :options="bulkOptions" aria-label="Bulk action" />
      </div>
      <div v-if="bulkOp === 'give_money' || bulkOp === 'take_money'">
        <label class="label">Amount</label>
        <input v-model.number="bulkAmount" type="number" min="0" class="input w-32" placeholder="0" />
      </div>
      <div v-if="bulkOp === 'mute' || bulkOp === 'ban'">
        <label class="label">Duration (min, 0 = perm)</label>
        <input v-model.number="bulkDuration" type="number" min="0" class="input w-36" placeholder="0" />
      </div>
      <div v-if="bulkOp === 'ban'">
        <label class="label">Reason</label>
        <input v-model="bulkReason" class="input w-48" placeholder="Reason (optional)" />
      </div>
      <div v-if="bulkOp === 'mail'" class="flex-1 min-w-[12rem]">
        <label class="label">Message</label>
        <input v-model="bulkMessage" class="input w-full" placeholder="Mail message…" />
      </div>
      <div class="flex items-center gap-2 ml-auto">
        <button class="btn-ghost text-xs" @click="clearSelection">Clear</button>
        <button class="btn-primary" :disabled="bulkBusy || !bulkOp" @click="applyBulk">
          {{ bulkBusy ? 'Applying…' : 'Apply' }}
        </button>
      </div>
    </div>

    <div class="card p-0 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr>
            <th v-if="canBulk" class="px-4 py-2 w-8">
              <input type="checkbox" :checked="allSelected" :indeterminate.prop="someSelected" aria-label="Select all" @change="toggleAll" />
            </th>
            <th class="text-left px-4 py-2 font-medium">Player</th>
            <th class="text-left px-4 py-2 font-medium hidden sm:table-cell">Last Seen</th>
            <th class="text-left px-4 py-2 font-medium">Status</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in players" :key="p.uuid" class="border-t border-edge hover:bg-elevated/50">
            <td v-if="canBulk" class="px-4 py-2">
              <input type="checkbox" :value="p.uuid" :checked="selected.includes(p.uuid)" :aria-label="`Select ${p.name}`" @change="toggle(p.uuid)" />
            </td>
            <td class="px-4 py-2">
              <div class="flex items-center gap-2">
                <Avatar :uuid="p.uuid" :name="p.name" :size="28" />
                <span class="font-medium text-primary">{{ p.name }}</span>
              </div>
            </td>
            <td class="px-4 py-2 text-muted hidden sm:table-cell">{{ timeAgo(p.lastSeen) }}</td>
            <td class="px-4 py-2">
              <span :class="p.online ? 'badge-online' : 'badge-offline'">{{ p.online ? 'Online' : 'Offline' }}</span>
            </td>
            <td class="px-4 py-2 text-right">
              <router-link :to="`/players/${p.uuid}`" class="btn-subtle py-1">Manage</router-link>
            </td>
          </tr>
          <tr v-if="!players.length">
            <td :colspan="canBulk ? 5 : 4" class="px-4 py-8 text-center text-muted">{{ loading ? 'Loading…' : 'No players found.' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination :page="page" :size="size" :total="total" @update:page="changePage" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { api } from '../api'
import { timeAgo } from '../utils'
import { exportCsv } from '../utils/csv'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import Avatar from '../components/Avatar.vue'
import Pagination from '../components/Pagination.vue'
import Select from '../components/ui/Select.vue'

const auth = useAuthStore()
const toast = useToastStore()
const confirm = useConfirm()

const q = ref('')
const status = ref('all')
const seen = ref('any')
const sort = ref('recent')
const players = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const loading = ref(false)
let timer = null

const statusOptions = [
  { value: 'all', label: 'All players' },
  { value: 'online', label: 'Online only' },
  { value: 'offline', label: 'Offline only' },
]
const seenOptions = [
  { value: 'any', label: 'Any time' },
  { value: '24h', label: 'Seen in 24h' },
  { value: '7d', label: 'Seen in 7 days' },
  { value: '30d', label: 'Seen in 30 days' },
]
const sortOptions = [
  { value: 'recent', label: 'Recently seen' },
  { value: 'name', label: 'Name (A–Z)' },
]

// ── Multi-select + bulk actions ──────────────────────────────────────────────
const canBulk = computed(() => auth.hasPermission('PLAYERS_MANAGE'))
const canBans = computed(() => auth.hasPermission('BANS_MANAGE'))
const selected = ref([])
const bulkOp = ref('give_money')
const bulkAmount = ref(null)
const bulkDuration = ref(0)
const bulkReason = ref('')
const bulkMessage = ref('')
const bulkBusy = ref(false)

const bulkOptions = computed(() => {
  const opts = [
    { value: 'give_money', label: 'Give money' },
    { value: 'take_money', label: 'Take money' },
    { value: 'mail', label: 'Send mail' },
  ]
  if (canBans.value) opts.push(
    { value: 'ban', label: 'Ban' },
    { value: 'unban', label: 'Unban' },
    { value: 'mute', label: 'Mute' },
    { value: 'unmute', label: 'Unmute' },
  )
  return opts
})

const pageUuids = computed(() => players.value.map(p => p.uuid))
const allSelected = computed(() => players.value.length > 0 && pageUuids.value.every(u => selected.value.includes(u)))
const someSelected = computed(() => selected.value.length > 0 && !allSelected.value)

function toggle(uuid) {
  const i = selected.value.indexOf(uuid)
  if (i === -1) selected.value.push(uuid)
  else selected.value.splice(i, 1)
}
function toggleAll() {
  if (allSelected.value) selected.value = selected.value.filter(u => !pageUuids.value.includes(u))
  else selected.value = [...new Set([...selected.value, ...pageUuids.value])]
}
function clearSelection() { selected.value = [] }

const opLabel = computed(() => bulkOptions.value.find(o => o.value === bulkOp.value)?.label || bulkOp.value)

async function applyBulk() {
  if (!selected.value.length) return
  if ((bulkOp.value === 'give_money' || bulkOp.value === 'take_money') && !(bulkAmount.value > 0)) {
    toast.error('Enter a positive amount.')
    return
  }
  if (bulkOp.value === 'mail' && !bulkMessage.value.trim()) {
    toast.error('Enter a mail message.')
    return
  }
  const ok = await confirm({
    title: 'Apply to selected players?',
    message: `${opLabel.value} will be applied to ${selected.value.length} player(s).`,
    danger: bulkOp.value === 'ban',
  })
  if (!ok) return
  bulkBusy.value = true
  try {
    const { data } = await api.playersBulk({
      uuids: selected.value,
      op: bulkOp.value,
      amount: bulkAmount.value || 0,
      reason: bulkReason.value,
      durationMinutes: bulkDuration.value || 0,
      message: bulkMessage.value,
    })
    const failed = data.failed?.length || 0
    toast.success(`${opLabel.value}: ${data.affected} done${failed ? `, ${failed} failed` : ''}.`)
    clearSelection()
    bulkAmount.value = null; bulkReason.value = ''; bulkMessage.value = ''; bulkDuration.value = 0
    search()
  } catch (e) {
    toast.error(e.response?.data?.error || 'Bulk action failed.')
  } finally {
    bulkBusy.value = false
  }
}

async function search() {
  loading.value = true
  try {
    const { data } = await api.searchPlayers(q.value, page.value, size, {
      status: status.value, seen: seen.value, sort: sort.value,
    })
    players.value = data.players
    total.value = data.total
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function debouncedSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => { page.value = 0; search() }, 300)
}

function applyFilters() {
  page.value = 0
  search()
}

function changePage(p) {
  page.value = p
  search()
}

function exportPage() {
  exportCsv('players.csv', players.value.map(p => ({
    name: p.name,
    uuid: p.uuid,
    online: p.online ? 'yes' : 'no',
    lastSeen: p.lastSeen ? new Date(p.lastSeen).toISOString() : '',
  })), [
    { key: 'name', label: 'Player' },
    { key: 'uuid', label: 'UUID' },
    { key: 'online', label: 'Online' },
    { key: 'lastSeen', label: 'Last Seen' },
  ])
}

search()
</script>
