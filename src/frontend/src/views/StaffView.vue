<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1>Staff Accounts</h1>
        <p class="text-sm text-muted">Dashboard logins and their permissions.</p>
      </div>
      <button class="btn-primary flex-shrink-0" @click="openNew">New Account</button>
    </div>

    <div class="card p-0 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated text-muted text-xs uppercase">
          <tr>
            <th class="text-left px-4 py-2">Username</th>
            <th class="text-left px-4 py-2">Role</th>
            <th class="text-left px-4 py-2">2FA</th>
            <th class="text-left px-4 py-2 hidden sm:table-cell">Permissions</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in staff" :key="s.username" class="border-t border-edge">
            <td class="px-4 py-2 font-medium text-primary">{{ s.username }}</td>
            <td class="px-4 py-2"><span class="badge bg-brand-subtle text-brand">{{ s.role }}</span></td>
            <td class="px-4 py-2">
              <span v-if="s.twoFactorEnabled" class="badge bg-green-500/15 text-green-500">On</span>
              <span v-else class="badge bg-elevated text-muted">Off</span>
            </td>
            <td class="px-4 py-2 text-muted text-xs hidden sm:table-cell">{{ s.role === 'ADMIN' ? 'All' : (s.permissions.length || 'None') + ' granted' }}</td>
            <td class="px-4 py-2"><div class="flex flex-wrap gap-1 justify-end">
              <button v-if="s.twoFactorEnabled" class="btn-ghost py-1" @click="reset2fa(s.username)">Reset 2FA</button>
              <button class="btn-subtle py-1" @click="openEdit(s)">Edit</button>
              <button class="btn-ghost py-1 text-red-500" @click="remove(s.username)">Delete</button>
            </div></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Active sessions across all accounts -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-base font-semibold text-primary">Active Sessions</h2>
          <p class="text-xs text-muted">Everyone currently signed in to the dashboard.</p>
        </div>
        <button class="btn-subtle py-1" @click="loadSessions">Refresh</button>
      </div>
      <div v-if="sessions.length" class="space-y-2">
        <div v-for="s in sessions" :key="s.jti"
             class="flex items-center gap-3 p-3 rounded-lg border border-edge"
             :class="s.current ? 'bg-brand-subtle/40 border-brand/40' : 'bg-elevated/40'">
          <div class="min-w-0 flex-1">
            <div class="text-sm text-primary truncate">
              {{ s.username }}
              <span class="badge bg-elevated text-muted ml-1">{{ s.role }}</span>
              <span v-if="s.current" class="badge bg-green-500/15 text-green-500 ml-1">You</span>
            </div>
            <div class="text-xs text-muted truncate">{{ s.ip || 'unknown IP' }} · {{ device(s.userAgent) }} · active {{ relative(s.lastSeen) }}</div>
          </div>
          <button v-if="!s.current" class="btn-ghost py-1 text-red-500 flex-shrink-0" @click="revoke(s)">Revoke</button>
        </div>
      </div>
      <p v-else class="text-sm text-muted py-2">No active sessions.</p>
    </div>

    <Modal :open="modal" :title="editing.existing ? `Edit: ${editing.username}` : 'New Account'" @close="modal = false">
      <div class="space-y-3">
        <div v-if="!editing.existing">
          <label class="label">Username</label>
          <input v-model="editing.username" class="input" />
        </div>
        <div>
          <label class="label">Password <span v-if="editing.existing" class="text-faint">(blank = keep)</span></label>
          <input v-model="editing.password" type="password" class="input" />
        </div>
        <div>
          <label class="label">Role</label>
          <select v-model="editing.role" class="input">
            <option value="ADMIN">Admin (full access)</option>
            <option value="STAFF">Staff (custom permissions)</option>
            <option value="DEMO">Demo (read-only)</option>
          </select>
        </div>
        <div v-if="editing.role === 'STAFF'">
          <label class="label">Permissions</label>
          <div class="grid grid-cols-2 gap-1 max-h-48 overflow-auto p-2 bg-elevated rounded-lg">
            <label v-for="p in allPermissions" :key="p" class="flex items-center gap-1.5 text-xs text-secondary">
              <input type="checkbox" :value="p" v-model="editing.permissions" /> {{ p }}
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn-ghost" @click="modal = false">Cancel</button>
        <button class="btn-primary" @click="save">Save</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import Modal from '../components/Modal.vue'
import { useConfirm } from '../composables/useConfirm'

const toast = useToastStore()
const confirm = useConfirm()
const staff = ref([])
const sessions = ref([])
const allPermissions = ref([])
const modal = ref(false)
const editing = ref({})

async function load() {
  try {
    const { data } = await api.getStaff()
    staff.value = data.staff
    allPermissions.value = data.allPermissions
  } catch { /* ignore */ }
}

async function loadSessions() {
  try { sessions.value = (await api.getAllSessions()).data.sessions } catch { /* ignore */ }
}

function openNew() {
  editing.value = { username: '', password: '', role: 'STAFF', permissions: [], existing: false }
  modal.value = true
}
function openEdit(s) {
  editing.value = { username: s.username, password: '', role: s.role, permissions: [...s.permissions], existing: true }
  modal.value = true
}

async function save() {
  const e = editing.value
  const body = { username: e.username, password: e.password, role: e.role, permissions: e.permissions }
  try {
    if (e.existing) await api.updateStaff(e.username, body)
    else await api.createStaff(body)
    toast.success('Account saved')
    modal.value = false
    load()
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to save account')
  }
}

async function remove(username) {
  if (!(await confirm({ title: 'Delete account', message: `Delete staff account "${username}"?`, confirmText: 'Delete', danger: true }))) return
  try { await api.deleteStaff(username); toast.success('Account deleted'); load(); loadSessions() }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to delete') }
}

async function reset2fa(username) {
  if (!(await confirm({ title: 'Reset 2FA', message: `Turn off two-factor for "${username}"? They can re-enrol afterwards.`, confirmText: 'Reset', danger: true }))) return
  try { await api.resetStaff2fa(username); toast.success('Two-factor reset'); load() }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to reset 2FA') }
}

async function revoke(s) {
  if (!(await confirm({ title: 'Revoke session', message: `Sign out ${s.username}'s session?`, confirmText: 'Revoke', danger: true }))) return
  try { await api.revokeAnySession(s.jti); toast.success('Session revoked'); loadSessions() }
  catch { toast.error('Failed to revoke session') }
}

function device(ua) {
  if (!ua) return 'unknown device'
  const browser = /edg/i.test(ua) ? 'Edge' : /chrome|crios/i.test(ua) ? 'Chrome' : /firefox|fxios/i.test(ua) ? 'Firefox'
    : /safari/i.test(ua) ? 'Safari' : 'Browser'
  const os = /windows/i.test(ua) ? 'Windows' : /mac os|macintosh/i.test(ua) ? 'macOS'
    : /android/i.test(ua) ? 'Android' : /iphone|ipad/i.test(ua) ? 'iOS' : /linux/i.test(ua) ? 'Linux' : ''
  return [browser, os].filter(Boolean).join('/')
}

function relative(ts) {
  const s = Math.round((Date.now() - ts) / 1000)
  if (s < 60) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.round(m / 60)
  return h < 24 ? `${h}h ago` : `${Math.round(h / 24)}d ago`
}

onMounted(() => { load(); loadSessions() })
</script>
