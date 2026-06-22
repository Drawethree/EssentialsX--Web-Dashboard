<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div>
      <h1>Economy</h1>
      <p class="text-sm text-muted">Server-wide balances and bulk operations.</p>
    </div>

    <div v-if="stats" class="grid grid-cols-2 lg:grid-cols-3 gap-4">
      <div class="card">
        <p class="text-xs text-muted uppercase">Total Money</p>
        <p class="text-2xl font-bold text-brand mt-1">{{ formatMoney(stats.totalMoney, stats.symbol) }}</p>
      </div>
      <div class="card">
        <p class="text-xs text-muted uppercase">Average Balance</p>
        <p class="text-2xl font-bold text-primary mt-1">{{ formatMoney(stats.averageMoney, stats.symbol) }}</p>
      </div>
      <div class="card">
        <p class="text-xs text-muted uppercase">Accounts</p>
        <p class="text-2xl font-bold text-primary mt-1">{{ stats.accounts }}</p>
      </div>
    </div>

    <div v-if="can('ECONOMY_MANAGE')" class="card space-y-3">
      <h3>Bulk Adjust</h3>
      <p class="text-xs text-muted">Give or take money from a population of players at once.</p>
      <div class="flex flex-wrap gap-2 items-center">
        <Select v-model="bulk.action" :options="[{value:'give',label:'Give'},{value:'take',label:'Take'}]" class="w-28" aria-label="Action" />
        <input v-model.number="bulk.amount" type="number" min="1" class="input w-40" placeholder="Amount" />
        <Select v-model="bulk.target" :options="[{value:'online',label:'Online players'},{value:'all',label:'All players'}]" class="w-44" aria-label="Target" />
        <Button :loading="busy" @click="runBulk">Apply</Button>
      </div>
    </div>

    <div v-if="can('ECONOMY_MANAGE')" class="card space-y-3">
      <div class="flex items-center justify-between gap-2">
        <h3>Debts <span v-if="debts" class="text-xs text-faint font-normal">({{ debts.count }} in debt)</span></h3>
        <div class="flex items-center gap-2">
          <button class="btn-ghost text-xs" @click="loadDebts">Refresh</button>
          <Button variant="danger" :loading="resettingDebts" :disabled="!debts || !debts.count" @click="resetDebts">
            Reset debts
          </Button>
        </div>
      </div>
      <p class="text-xs text-muted">Floor every negative balance back to zero in one pass.</p>
      <div v-if="debts && debts.count" class="space-y-1.5">
        <p class="text-sm text-muted">
          Total debt: <span class="font-medium text-red-500">{{ formatMoney(debts.totalDebt, symbol) }}</span>
        </p>
        <ul class="text-sm divide-y divide-edge max-h-48 overflow-auto">
          <li v-for="d in debts.entries" :key="d.uuid" class="flex justify-between py-1.5">
            <router-link :to="`/players/${d.uuid}`" class="text-primary hover:text-brand truncate">{{ d.name || d.uuid }}</router-link>
            <span class="text-red-500 font-medium">{{ formatMoney(d.balance, symbol) }}</span>
          </li>
        </ul>
      </div>
      <p v-else-if="debts" class="text-sm text-emerald-500">No players are in debt. 🎉</p>
    </div>

    <div class="card p-0 overflow-x-auto">
      <div class="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-edge">
        <h3>Top Balances</h3>
        <div class="flex items-center gap-2">
          <button class="btn-ghost text-xs" :disabled="!top.length" @click="exportTop">Export CSV</button>
          <button class="btn-ghost text-xs" @click="loadTop">Refresh</button>
        </div>
      </div>
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr><th class="text-left px-4 py-2 w-12">#</th><th class="text-left px-4 py-2">Player</th><th class="text-right px-4 py-2">Balance</th></tr>
        </thead>
        <tbody>
          <tr v-for="e in top" :key="e.uuid" class="border-t border-edge hover:bg-elevated/50">
            <td class="px-4 py-2 text-muted">{{ e.rank }}</td>
            <td class="px-4 py-2">
              <router-link :to="`/players/${e.uuid}`" class="font-medium text-primary hover:text-brand">{{ e.name }}</router-link>
            </td>
            <td class="px-4 py-2 text-right font-medium text-brand">{{ formatMoney(e.balance, symbol) }}</td>
          </tr>
          <tr v-if="!top.length"><td colspan="3" class="px-4 py-8 text-center text-muted">{{ loading ? 'Loading…' : 'No data.' }}</td></tr>
        </tbody>
      </table>
      <div class="px-4 pb-3">
        <Pagination :page="topPage" :size="topSize" :total="topTotal" @update:page="changeTopPage" />
      </div>
    </div>

    <!-- Insights -->
    <div v-if="insights" class="space-y-4">
      <div class="card space-y-2">
        <div class="flex items-center justify-between">
          <h3>Money Supply</h3>
          <Select v-model="range" :options="rangeOptions" class="w-32" aria-label="Range" @update:model-value="loadInsights" />
        </div>
        <TimeSeriesChart v-if="supplyPoints.length > 1" :points="supplyPoints" :height="160"
                         :format="v => formatMoney(v, symbol)" />
        <p v-else class="text-sm text-muted py-6 text-center">Not enough samples yet.</p>
      </div>

      <div class="grid sm:grid-cols-2 gap-4">
        <div class="card space-y-2">
          <h3 class="text-emerald-500">Top Earners <span class="text-xs text-faint font-normal">({{ rangeLabel }})</span></h3>
          <ul class="text-sm divide-y divide-edge">
            <li v-for="m in insights.topEarners" :key="m.uuid" class="flex justify-between py-1.5">
              <router-link :to="`/players/${m.uuid}`" class="text-primary hover:text-brand truncate">{{ m.name || m.uuid }}</router-link>
              <span class="text-emerald-500 font-medium">+{{ formatMoney(m.net, symbol) }}</span>
            </li>
            <li v-if="!insights.topEarners.length" class="py-3 text-muted text-center">No data yet.</li>
          </ul>
        </div>
        <div class="card space-y-2">
          <h3 class="text-red-500">Top Spenders <span class="text-xs text-faint font-normal">({{ rangeLabel }})</span></h3>
          <ul class="text-sm divide-y divide-edge">
            <li v-for="m in insights.topSpenders" :key="m.uuid" class="flex justify-between py-1.5">
              <router-link :to="`/players/${m.uuid}`" class="text-primary hover:text-brand truncate">{{ m.name || m.uuid }}</router-link>
              <span class="text-red-500 font-medium">{{ formatMoney(m.net, symbol) }}</span>
            </li>
            <li v-if="!insights.topSpenders.length" class="py-3 text-muted text-center">No data yet.</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Transaction ledger -->
    <div v-if="can('ECONOMY_LOG_VIEW')" class="card p-0 overflow-x-auto">
      <div class="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-edge">
        <h3>Transaction Ledger</h3>
        <button class="btn-ghost text-xs" @click="loadTx">Refresh</button>
      </div>
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr>
            <th class="text-left px-4 py-2">Player</th>
            <th class="text-right px-4 py-2">Change</th>
            <th class="text-right px-4 py-2 hidden sm:table-cell">Balance</th>
            <th class="text-left px-4 py-2 hidden md:table-cell">Source</th>
            <th class="text-left px-4 py-2 hidden lg:table-cell">Staff</th>
            <th class="text-left px-4 py-2 hidden sm:table-cell">When</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tx" :key="t.id" class="border-t border-edge hover:bg-elevated/50">
            <td class="px-4 py-2 text-primary truncate max-w-[12rem]">{{ t.name }}</td>
            <td class="px-4 py-2 text-right font-medium" :class="deltaClass(t.delta)">{{ formatDelta(t.delta) }}</td>
            <td class="px-4 py-2 text-right text-secondary hidden sm:table-cell">{{ t.balance != null ? formatMoney(Number(t.balance), symbol) : '—' }}</td>
            <td class="px-4 py-2 hidden md:table-cell"><span class="badge bg-brand-subtle text-brand">{{ t.source }}</span></td>
            <td class="px-4 py-2 text-muted hidden lg:table-cell">{{ t.staff || '—' }}</td>
            <td class="px-4 py-2 text-muted whitespace-nowrap hidden sm:table-cell" :title="formatDateTime(t.ts)">{{ timeAgo(t.ts) }}</td>
          </tr>
          <tr v-if="!tx.length"><td colspan="6" class="px-4 py-8 text-center text-muted">No transactions recorded yet.</td></tr>
        </tbody>
      </table>
      <div class="px-4 pb-3">
        <Pagination :page="txPage" :size="txSize" :total="txTotal" @update:page="changeTxPage" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { formatMoney, timeAgo, formatDateTime } from '../utils'
import { exportCsv } from '../utils/csv'
import { useConfirm } from '../composables/useConfirm'
import Pagination from '../components/Pagination.vue'
import TimeSeriesChart from '../components/TimeSeriesChart.vue'
import Select from '../components/ui/Select.vue'
import Button from '../components/ui/Button.vue'

const auth = useAuthStore()
const toast = useToastStore()
const confirm = useConfirm()
const can = p => auth.hasPermission(p)

// Debts (negative balances)
const debts = ref(null)
const resettingDebts = ref(false)

const stats = ref(null)
const top = ref([])
const topPage = ref(0)
const topTotal = ref(0)
const topSize = 20
const symbol = ref('$')
const loading = ref(true)
const busy = ref(false)
const bulk = ref({ action: 'give', amount: null, target: 'online' })

// Insights
const insights = ref(null)
const range = ref('7d')
const rangeOptions = [
  { value: '24h', label: 'Last 24h' },
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
]
const rangeLabel = computed(() => rangeOptions.find(r => r.value === range.value)?.label || '')
const supplyPoints = computed(() =>
  (insights.value?.supply || [])
    .map(s => ({ ts: Number(s.ts), value: Number(s.total) }))
    .filter(p => !Number.isNaN(p.value)))

// Transaction ledger
const tx = ref([])
const txPage = ref(0)
const txTotal = ref(0)
const txSize = 50

function deltaClass(delta) {
  const n = Number(delta)
  if (Number.isNaN(n) || n === 0) return 'text-muted'
  return n > 0 ? 'text-emerald-500' : 'text-red-500'
}
function formatDelta(delta) {
  if (delta == null || delta === '') return '—'
  const n = Number(delta)
  if (Number.isNaN(n)) return delta
  return (n > 0 ? '+' : '') + formatMoney(n, symbol.value)
}

async function loadInsights() {
  try { insights.value = (await api.economyInsights(range.value)).data } catch { /* ignore */ }
}
async function loadTx() {
  try {
    const { data } = await api.economyTransactions(txPage.value, txSize)
    tx.value = data.entries
    txTotal.value = data.total
    if (data.symbol) symbol.value = data.symbol
  } catch { /* ignore */ }
}
function changeTxPage(p) { txPage.value = p; loadTx() }

async function loadStats() {
  try { stats.value = (await api.economyStats()).data } catch { /* ignore */ }
}
async function loadTop() {
  loading.value = true
  try {
    const { data } = await api.baltop(topPage.value, topSize)
    top.value = data.entries
    symbol.value = data.symbol
    topTotal.value = data.total
  } catch { /* ignore */ } finally { loading.value = false }
}

function changeTopPage(p) {
  topPage.value = p
  loadTop()
}

async function exportTop() {
  // Gather all leaderboard pages (server caps the board at 1000) for a complete export.
  try {
    const size = 100
    let page = 0, rows = [], total = Infinity
    while (rows.length < total) {
      const { data } = await api.baltop(page, size)
      total = data.total
      rows = rows.concat(data.entries)
      if (!data.entries.length) break
      page++
    }
    exportCsv('baltop.csv', rows, [
      { key: 'rank', label: 'Rank' },
      { key: 'name', label: 'Player' },
      { key: 'uuid', label: 'UUID' },
      { key: 'balance', label: 'Balance' },
    ])
  } catch { toast.error('Export failed') }
}

async function loadDebts() {
  if (!can('ECONOMY_MANAGE')) return
  try { debts.value = (await api.economyDebts()).data } catch { /* ignore */ }
}

async function resetDebts() {
  if (!debts.value?.count) return
  const ok = await confirm({
    title: 'Reset debts?',
    message: `Floor ${debts.value.count} negative balance(s) to zero (clears ${formatMoney(debts.value.totalDebt, symbol.value)} of debt). This cannot be undone.`,
    danger: true,
  })
  if (!ok) return
  resettingDebts.value = true
  try {
    const { data } = await api.economyResetDebts()
    toast.success(`Cleared debt for ${data.count} player(s).`)
    loadDebts(); loadStats(); loadTop()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to reset debts')
  } finally { resettingDebts.value = false }
}

async function runBulk() {
  if (!bulk.value.amount || bulk.value.amount <= 0) return toast.error('Enter a valid amount')
  busy.value = true
  try {
    const { data } = await api.economyBulk(bulk.value.action, bulk.value.amount, bulk.value.target)
    toast.success(`Applied to ${data.affected} player(s).`)
    loadStats(); loadTop()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Bulk operation failed')
  } finally { busy.value = false }
}

onMounted(() => {
  loadStats(); loadTop(); loadInsights(); loadDebts()
  if (can('ECONOMY_LOG_VIEW')) loadTx()
})
</script>
