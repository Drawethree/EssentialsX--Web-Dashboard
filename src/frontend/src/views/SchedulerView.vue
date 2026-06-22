<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h1>Scheduled Tasks</h1>
        <p class="text-sm text-muted">Automate broadcasts, commands, mass mail, and timed restarts.</p>
      </div>
      <button v-if="canManage" class="btn-primary" @click="openNew">New Task</button>
    </div>

    <div class="card p-0 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr>
            <th class="text-left px-4 py-2">Task</th>
            <th class="text-left px-4 py-2 hidden sm:table-cell">Schedule</th>
            <th class="text-left px-4 py-2 hidden md:table-cell">Next run</th>
            <th class="text-left px-4 py-2 hidden lg:table-cell">Last result</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id" class="border-t border-edge" :class="t.enabled ? '' : 'opacity-50'">
            <td class="px-4 py-2.5">
              <div class="font-medium text-primary">{{ t.name }}</div>
              <div class="flex items-center gap-1.5 mt-0.5">
                <span class="badge" :class="typeClass(t.type)">{{ typeLabel(t.type) }}</span>
                <span class="text-xs text-muted truncate max-w-[16rem]">{{ summary(t) }}</span>
              </div>
            </td>
            <td class="px-4 py-2.5 text-muted text-xs hidden sm:table-cell">{{ scheduleText(t) }}</td>
            <td class="px-4 py-2.5 text-muted text-xs hidden md:table-cell">
              {{ t.enabled ? relative(t.nextRun) : 'Paused' }}
            </td>
            <td class="px-4 py-2.5 text-muted text-xs hidden lg:table-cell">
              <span v-if="t.lastRun">{{ t.lastResult }}</span>
              <span v-else class="text-faint">Never run</span>
            </td>
            <td class="px-4 py-2.5 text-right whitespace-nowrap">
              <template v-if="canManage">
                <button class="btn-ghost py-1" :title="t.enabled ? 'Pause' : 'Resume'" @click="toggle(t)">
                  {{ t.enabled ? 'Pause' : 'Resume' }}
                </button>
                <button class="btn-subtle py-1" @click="runNow(t)">Run now</button>
                <button class="btn-ghost py-1" @click="openEdit(t)">Edit</button>
                <button class="btn-ghost py-1 text-red-500" @click="remove(t)">Delete</button>
              </template>
            </td>
          </tr>
          <tr v-if="!tasks.length">
            <td colspan="5">
              <EmptyState :icon="ClockIcon" title="No scheduled tasks yet"
                          hint="Create a task to broadcast messages, run commands, or restart on a schedule." />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal :open="modal" :title="form.id ? 'Edit Task' : 'New Task'" @close="modal = false">
      <div class="space-y-3">
        <div>
          <label class="label">Name</label>
          <input v-model="form.name" class="input" placeholder="e.g. Vote reminder" />
        </div>
        <div>
          <label class="label">Type</label>
          <select v-model="form.type" class="input">
            <option value="BROADCAST">Broadcast message</option>
            <option value="COMMAND">Run console command</option>
            <option value="MAIL_ALL">Mail all players</option>
            <option value="RESTART">Timed restart</option>
          </select>
        </div>

        <div v-if="form.type === 'BROADCAST' || form.type === 'MAIL_ALL'">
          <label class="label">Message <span class="text-faint">(supports &amp; color codes)</span></label>
          <textarea v-model="form.payload" class="input" rows="2" placeholder="&aDon't forget to vote!"></textarea>
        </div>
        <div v-else-if="form.type === 'COMMAND'">
          <label class="label">Console command</label>
          <input v-model="form.payload" class="input" placeholder="say Hello world" />
        </div>
        <template v-else-if="form.type === 'RESTART'">
          <div>
            <label class="label">Restart command <span class="text-faint">(blank = stop)</span></label>
            <input v-model="form.payload" class="input" placeholder="stop" />
          </div>
          <div>
            <label class="label">Countdown warning (seconds)</label>
            <input v-model.number="form.countdownSeconds" type="number" min="0" class="input" placeholder="30" />
          </div>
        </template>

        <div>
          <label class="label">Schedule</label>
          <select v-model="form.scheduleType" class="input">
            <option value="ONCE">Once</option>
            <option value="INTERVAL">Repeating</option>
          </select>
        </div>
        <div>
          <label class="label">{{ form.scheduleType === 'INTERVAL' ? 'First run at' : 'Run at' }}</label>
          <input v-model="form.firstRun" type="datetime-local" class="input" />
        </div>
        <div v-if="form.scheduleType === 'INTERVAL'" class="flex gap-2">
          <div class="flex-1">
            <label class="label">Repeat every</label>
            <input v-model.number="form.intervalValue" type="number" min="1" class="input" />
          </div>
          <div class="flex-1">
            <label class="label">Unit</label>
            <select v-model="form.intervalUnit" class="input">
              <option value="60000">Minutes</option>
              <option value="3600000">Hours</option>
              <option value="86400000">Days</option>
            </select>
          </div>
        </div>
        <label class="flex items-center gap-2 text-sm text-secondary">
          <input type="checkbox" v-model="form.enabled" /> Enabled
        </label>
        <p v-if="error" class="text-sm text-danger">{{ error }}</p>
      </div>
      <template #footer>
        <button class="btn-ghost" @click="modal = false">Cancel</button>
        <button class="btn-primary" :disabled="saving" @click="save">Save</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import Modal from '../components/Modal.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { ClockIcon } from '@heroicons/vue/24/outline'

const auth = useAuthStore()
const toast = useToastStore()
const confirm = useConfirm()

const tasks = ref([])
const modal = ref(false)
const saving = ref(false)
const error = ref('')
const form = ref({})
const canManage = auth.hasPermission('SCHEDULER_MANAGE')

async function load() {
  try { tasks.value = (await api.getTasks()).data.tasks } catch { /* ignore */ }
}

function blankForm() {
  // default first run = 5 minutes from now, in local datetime-local format
  const d = new Date(Date.now() + 5 * 60000)
  return {
    id: null, name: '', type: 'BROADCAST', payload: '', countdownSeconds: 30,
    scheduleType: 'ONCE', firstRun: toLocalInput(d.getTime()),
    intervalValue: 1, intervalUnit: '3600000', enabled: true,
  }
}

function openNew() {
  form.value = blankForm()
  error.value = ''
  modal.value = true
}

function openEdit(t) {
  let intervalValue = 1, intervalUnit = '3600000'
  if (t.intervalMs > 0) {
    for (const u of ['86400000', '3600000', '60000']) {
      if (t.intervalMs % Number(u) === 0) { intervalUnit = u; intervalValue = t.intervalMs / Number(u); break }
    }
  }
  form.value = {
    id: t.id, name: t.name, type: t.type, payload: t.payload ?? '',
    countdownSeconds: t.countdownSeconds || 30, scheduleType: t.scheduleType,
    firstRun: toLocalInput(t.nextRun), intervalValue, intervalUnit, enabled: t.enabled,
  }
  error.value = ''
  modal.value = true
}

async function save() {
  const f = form.value
  const nextRun = new Date(f.firstRun).getTime()
  if (!nextRun) { error.value = 'Pick a valid run time'; return }
  const body = {
    name: f.name,
    type: f.type,
    payload: f.payload,
    countdownSeconds: f.type === 'RESTART' ? f.countdownSeconds : 0,
    scheduleType: f.scheduleType,
    nextRun,
    intervalMs: f.scheduleType === 'INTERVAL' ? f.intervalValue * Number(f.intervalUnit) : 0,
    enabled: f.enabled,
  }
  saving.value = true
  error.value = ''
  try {
    if (f.id) await api.updateTask(f.id, body)
    else await api.createTask(body)
    toast.success('Task saved')
    modal.value = false
    load()
  } catch (err) {
    error.value = err.response?.data?.error ?? 'Failed to save task'
  } finally {
    saving.value = false
  }
}

async function toggle(t) {
  try { await api.toggleTask(t.id); load() } catch { toast.error('Failed to toggle task') }
}

async function runNow(t) {
  if (!(await confirm({ title: 'Run now', message: `Run "${t.name}" immediately?`, confirmText: 'Run' }))) return
  try {
    const { data } = await api.runTask(t.id)
    toast.success(data.result || 'Task executed')
    load()
  } catch { toast.error('Failed to run task') }
}

async function remove(t) {
  if (!(await confirm({ title: 'Delete task', message: `Delete "${t.name}"?`, confirmText: 'Delete', danger: true }))) return
  try { await api.deleteTask(t.id); toast.success('Task deleted'); load() }
  catch { toast.error('Failed to delete task') }
}

// ── formatting helpers ──
function toLocalInput(ts) {
  const d = new Date(ts)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const TYPE_LABELS = { BROADCAST: 'Broadcast', COMMAND: 'Command', MAIL_ALL: 'Mail all', RESTART: 'Restart' }
const TYPE_CLASSES = {
  BROADCAST: 'bg-blue-500/15 text-blue-700 dark:text-blue-400',
  COMMAND: 'bg-purple-500/15 text-purple-700 dark:text-purple-400',
  MAIL_ALL: 'bg-teal-500/15 text-teal-700 dark:text-teal-400',
  RESTART: 'bg-red-500/15 text-red-700 dark:text-red-400',
}
function typeLabel(t) { return TYPE_LABELS[t] ?? t }
function typeClass(t) { return TYPE_CLASSES[t] ?? 'bg-elevated text-muted' }

function summary(t) {
  if (t.type === 'RESTART') return `${t.payload || 'stop'} · ${t.countdownSeconds}s warning`
  return t.payload
}

function scheduleText(t) {
  if (t.scheduleType !== 'INTERVAL' || t.intervalMs <= 0) return 'Once'
  const ms = t.intervalMs
  if (ms % 86400000 === 0) return `Every ${ms / 86400000}d`
  if (ms % 3600000 === 0) return `Every ${ms / 3600000}h`
  return `Every ${Math.round(ms / 60000)}m`
}

function relative(ts) {
  const diff = ts - Date.now()
  const abs = Math.abs(diff)
  const mins = Math.round(abs / 60000)
  const fmt = mins < 60 ? `${mins}m` : mins < 1440 ? `${Math.round(mins / 60)}h` : `${Math.round(mins / 1440)}d`
  return diff <= 0 ? 'due now' : `in ${fmt}`
}

onMounted(load)
</script>
