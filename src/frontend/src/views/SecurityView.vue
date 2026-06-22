<template>
  <div class="p-4 sm:p-6 max-w-3xl mx-auto space-y-5">
    <div>
      <h1>Security</h1>
      <p class="text-sm text-muted">Manage two-factor authentication and your active sign-ins.</p>
    </div>

    <!-- Two-factor authentication -->
    <div class="card space-y-4">
      <div class="flex items-start justify-between gap-3">
        <div class="flex items-start gap-3">
          <span class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
                :class="twoFa.enabled ? 'bg-green-500/15 text-green-500' : 'bg-elevated text-muted'">
            <ShieldCheckIcon class="w-5 h-5" />
          </span>
          <div>
            <h2 class="text-base font-semibold text-primary">Two-Factor Authentication</h2>
            <p class="text-xs text-muted max-w-md">
              Require a one-time code from an authenticator app (Google Authenticator, Authy, 1Password…) in
              addition to your password.
            </p>
          </div>
        </div>
        <span class="badge flex-shrink-0" :class="twoFa.enabled ? 'bg-green-500/15 text-green-500' : 'bg-elevated text-muted'">
          {{ twoFa.enabled ? 'Enabled' : 'Disabled' }}
        </span>
      </div>

      <div v-if="twoFa.enabled" class="flex flex-wrap items-center gap-2 pt-1">
        <span class="text-xs text-muted mr-auto">{{ twoFa.recoveryRemaining }} recovery code(s) remaining</span>
        <button class="btn-subtle py-1" @click="askPassword('regen')">Regenerate recovery codes</button>
        <button class="btn-ghost py-1 text-red-500" @click="askPassword('disable')">Disable 2FA</button>
      </div>
      <div v-else class="pt-1">
        <button class="btn-primary" @click="askPassword('setup')">Enable Two-Factor Authentication</button>
      </div>
    </div>

    <!-- Active sessions -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="text-base font-semibold text-primary">Active Sessions</h2>
          <p class="text-xs text-muted">Devices currently signed in to your account.</p>
        </div>
        <button v-if="sessions.length > 1" class="btn-subtle py-1" @click="revokeOthers">Log out everywhere else</button>
      </div>

      <div v-if="sessions.length" class="space-y-2">
        <div v-for="s in sessions" :key="s.jti"
             class="flex items-center gap-3 p-3 rounded-lg border border-edge"
             :class="s.current ? 'bg-brand-subtle/40 border-brand/40' : 'bg-elevated/40'">
          <component :is="deviceIcon(s.userAgent)" class="w-5 h-5 text-muted flex-shrink-0" />
          <div class="min-w-0 flex-1">
            <div class="text-sm text-primary truncate">
              {{ deviceLabel(s.userAgent) }}
              <span v-if="s.current" class="badge bg-green-500/15 text-green-500 ml-1">This device</span>
            </div>
            <div class="text-xs text-muted truncate">
              {{ s.ip || 'unknown IP' }} · active {{ relative(s.lastSeen) }}
            </div>
          </div>
          <button v-if="!s.current" class="btn-ghost py-1 text-red-500 flex-shrink-0" @click="revoke(s)">Revoke</button>
        </div>
      </div>
      <EmptyState v-else :icon="ComputerDesktopIcon" title="No active sessions" />
    </div>

    <!-- Password prompt (setup / disable / regenerate) -->
    <Modal :open="pwModal.open" :title="pwModal.title" @close="pwModal.open = false">
      <form id="pw-form" class="space-y-3" @submit.prevent="confirmPassword">
        <p class="text-sm text-muted">{{ pwModal.message }}</p>
        <div>
          <label class="label">Password</label>
          <input v-model="pwModal.password" type="password" class="input" autocomplete="current-password" autofocus />
        </div>
        <p v-if="pwModal.error" class="text-sm text-danger">{{ pwModal.error }}</p>
      </form>
      <template #footer>
        <button class="btn-ghost" @click="pwModal.open = false">Cancel</button>
        <button type="submit" form="pw-form" class="btn-primary" :disabled="pwModal.busy">Continue</button>
      </template>
    </Modal>

    <!-- 2FA enrolment wizard -->
    <Modal :open="setup.open" title="Set up two-factor authentication" @close="setup.open = false">
      <div class="space-y-4">
        <ol class="text-sm text-secondary space-y-3">
          <li>
            <span class="font-medium text-primary">1.</span> Scan this QR code with your authenticator app:
            <div class="flex justify-center my-3">
              <img v-if="setup.qr" :src="setup.qr" alt="2FA QR code" class="w-44 h-44 rounded-lg bg-white p-2" />
            </div>
            <p class="text-xs text-muted text-center">
              Or enter this key manually:
              <code class="text-primary break-all">{{ setup.secret }}</code>
            </p>
          </li>
          <li>
            <span class="font-medium text-primary">2.</span> Enter the 6-digit code it shows:
            <input v-model="setup.code" class="input mt-2 text-center tracking-[0.4em] text-lg"
                   inputmode="numeric" placeholder="000000" />
          </li>
        </ol>
        <p v-if="setup.error" class="text-sm text-danger">{{ setup.error }}</p>
      </div>
      <template #footer>
        <button class="btn-ghost" @click="setup.open = false">Cancel</button>
        <button class="btn-primary" :disabled="setup.busy" @click="confirmEnable">Verify & Enable</button>
      </template>
    </Modal>

    <!-- Recovery codes (shown once) -->
    <Modal :open="recovery.open" title="Save your recovery codes" @close="recovery.open = false">
      <div class="space-y-3">
        <p class="text-sm text-muted">
          Store these somewhere safe. Each code can be used once to sign in if you lose access to your
          authenticator. They won't be shown again.
        </p>
        <div class="grid grid-cols-2 gap-2 p-3 bg-elevated rounded-lg font-mono text-sm text-primary">
          <div v-for="c in recovery.codes" :key="c" class="text-center py-1">{{ c }}</div>
        </div>
        <div class="flex gap-2">
          <button class="btn-subtle py-1 flex-1" @click="copyCodes">Copy</button>
          <button class="btn-subtle py-1 flex-1" @click="downloadCodes">Download</button>
        </div>
      </div>
      <template #footer>
        <button class="btn-primary" @click="recovery.open = false">Done</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import QRCode from 'qrcode'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import { useConfirm } from '../composables/useConfirm'
import { useClipboard } from '../composables/useClipboard'
import Modal from '../components/Modal.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import {
  ShieldCheckIcon, ComputerDesktopIcon, DevicePhoneMobileIcon, GlobeAltIcon,
} from '@heroicons/vue/24/outline'

const toast = useToastStore()
const confirm = useConfirm()
const copy = useClipboard()

const twoFa = ref({ enabled: false, recoveryRemaining: 0 })
const sessions = ref([])

const pwModal = ref({ open: false, intent: '', title: '', message: '', password: '', error: '', busy: false })
const setup = ref({ open: false, secret: '', qr: '', code: '', error: '', busy: false })
const recovery = ref({ open: false, codes: [] })

async function load() {
  try {
    const [{ data: tf }, { data: ss }] = await Promise.all([api.get2fa(), api.getSessions()])
    twoFa.value = tf
    sessions.value = ss.sessions
  } catch { /* ignore */ }
}

function askPassword(intent) {
  const map = {
    setup: { title: 'Confirm your password', message: 'Confirm your password to begin two-factor setup.' },
    disable: { title: 'Disable two-factor', message: 'Confirm your password to turn off two-factor authentication.' },
    regen: { title: 'Regenerate recovery codes', message: 'Confirm your password to generate a new set of recovery codes. The old codes stop working.' },
  }
  pwModal.value = { open: true, intent, ...map[intent], password: '', error: '', busy: false }
}

async function confirmPassword() {
  const { intent, password } = pwModal.value
  if (!password) { pwModal.value.error = 'Password is required'; return }
  pwModal.value.busy = true
  pwModal.value.error = ''
  try {
    if (intent === 'setup') {
      const { data } = await api.setup2fa(password)
      pwModal.value.open = false
      setup.value = { open: true, secret: data.secret, qr: await QRCode.toDataURL(data.otpauthUri), code: '', error: '', busy: false }
    } else if (intent === 'disable') {
      await api.disable2fa(password)
      pwModal.value.open = false
      toast.success('Two-factor authentication disabled.')
      load()
    } else if (intent === 'regen') {
      const { data } = await api.regenerateRecoveryCodes(password)
      pwModal.value.open = false
      showRecovery(data.recoveryCodes)
      load()
    }
  } catch (err) {
    pwModal.value.error = err.response?.data?.error ?? 'Something went wrong'
  } finally {
    pwModal.value.busy = false
  }
}

async function confirmEnable() {
  if (!/^\d{6}$/.test(setup.value.code.trim())) { setup.value.error = 'Enter the 6-digit code'; return }
  setup.value.busy = true
  setup.value.error = ''
  try {
    const { data } = await api.enable2fa(setup.value.code.trim())
    setup.value.open = false
    toast.success('Two-factor authentication enabled.')
    showRecovery(data.recoveryCodes)
    load()
  } catch (err) {
    setup.value.error = err.response?.data?.error ?? 'Could not enable 2FA'
  } finally {
    setup.value.busy = false
  }
}

function showRecovery(codes) {
  recovery.value = { open: true, codes }
}

function copyCodes() {
  copy(recovery.value.codes.join('\n'), 'Recovery codes copied.')
}

function downloadCodes() {
  const blob = new Blob([recovery.value.codes.join('\n') + '\n'], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'essdashboard-recovery-codes.txt'
  a.click()
  URL.revokeObjectURL(url)
}

async function revoke(s) {
  if (!(await confirm({ title: 'Revoke session', message: 'Sign this device out?', confirmText: 'Revoke', danger: true }))) return
  try { await api.revokeSession(s.jti); toast.success('Session revoked.'); load() }
  catch { toast.error('Failed to revoke session.') }
}

async function revokeOthers() {
  if (!(await confirm({ title: 'Log out everywhere else', message: 'Sign out all other devices? This keeps you signed in here.', confirmText: 'Log out others', danger: true }))) return
  try {
    const { data } = await api.revokeOtherSessions()
    toast.success(`Signed out ${data.revoked} other session(s).`)
    load()
  } catch { toast.error('Failed to revoke sessions.') }
}

function deviceLabel(ua) {
  if (!ua) return 'Unknown device'
  const os = /windows/i.test(ua) ? 'Windows' : /mac os|macintosh/i.test(ua) ? 'macOS'
    : /android/i.test(ua) ? 'Android' : /iphone|ipad|ios/i.test(ua) ? 'iOS' : /linux/i.test(ua) ? 'Linux' : ''
  const browser = /edg/i.test(ua) ? 'Edge' : /chrome|crios/i.test(ua) ? 'Chrome' : /firefox|fxios/i.test(ua) ? 'Firefox'
    : /safari/i.test(ua) ? 'Safari' : 'Browser'
  return [browser, os].filter(Boolean).join(' · ') || 'Unknown device'
}

function deviceIcon(ua) {
  if (!ua) return GlobeAltIcon
  if (/android|iphone|ipad|mobile/i.test(ua)) return DevicePhoneMobileIcon
  return ComputerDesktopIcon
}

function relative(ts) {
  const diff = Date.now() - ts
  const s = Math.round(diff / 1000)
  if (s < 60) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.round(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.round(h / 24)}d ago`
}

onMounted(load)
</script>
