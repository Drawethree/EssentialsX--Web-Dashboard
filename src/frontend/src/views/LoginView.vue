<template>
  <div class="min-h-screen flex items-center justify-center p-4 bg-page">
    <div class="w-full max-w-sm">
      <div class="flex flex-col items-center mb-6">
        <img :src="branding.logoUrl || logo" alt="" class="w-16 h-16 rounded-2xl mb-3 shadow-md object-contain" />
        <h1 class="text-xl font-bold text-primary">{{ branding.serverName }}</h1>
        <p class="text-sm text-muted">Sign in to manage your server</p>
      </div>

      <!-- Step 1: credentials -->
      <form v-if="!totp.required" class="card space-y-4" @submit.prevent="submit">
        <div>
          <label class="label">Username</label>
          <input v-model="username" class="input" autocomplete="username" autofocus />
        </div>
        <div>
          <label class="label">Password</label>
          <input v-model="password" type="password" class="input" autocomplete="current-password" />
        </div>
        <p v-if="error" class="text-sm text-danger">{{ error }}</p>
        <button type="submit" class="btn-primary w-full" :disabled="loading">
          {{ loading ? 'Signing in…' : 'Sign In' }}
        </button>
        <button v-if="demo.available" type="button" class="btn-ghost w-full" @click="demoLogin">
          Try the demo (read-only)
        </button>
      </form>

      <!-- Step 2: two-factor code -->
      <form v-else class="card space-y-4" @submit.prevent="submitTotp">
        <div class="text-center">
          <span class="inline-flex w-11 h-11 rounded-xl bg-brand-subtle text-brand items-center justify-center mb-2">
            <ShieldCheckIcon class="w-6 h-6" />
          </span>
          <h2 class="text-base font-semibold text-primary">Two-factor authentication</h2>
          <p class="text-xs text-muted">Enter the 6-digit code from your authenticator app, or a recovery code.</p>
        </div>
        <div>
          <input
            v-model="totp.code"
            class="input text-center tracking-[0.4em] text-lg"
            inputmode="text"
            autocomplete="one-time-code"
            placeholder="000000"
            autofocus
          />
        </div>
        <p v-if="error" class="text-sm text-danger">{{ error }}</p>
        <button type="submit" class="btn-primary w-full" :disabled="loading">
          {{ loading ? 'Verifying…' : 'Verify' }}
        </button>
        <button type="button" class="btn-ghost w-full" @click="cancelTotp">Back</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useBrandingStore } from '../stores/branding'
import logo from '../assets/logo.png'
import { ShieldCheckIcon } from '@heroicons/vue/24/outline'

const auth = useAuthStore()
const branding = useBrandingStore()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const demo = ref({ available: false })
const totp = ref({ required: false, preAuthToken: '', code: '' })

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const { data } = await api.login(username.value, password.value)
    if (data.totpRequired) {
      totp.value = { required: true, preAuthToken: data.preAuthToken, code: '' }
    } else {
      auth.loginWithData(data)
    }
  } catch (err) {
    error.value = err.response?.data?.error ?? 'Login failed'
  } finally {
    loading.value = false
  }
}

async function submitTotp() {
  error.value = ''
  loading.value = true
  try {
    const { data } = await api.completeTotp(totp.value.preAuthToken, totp.value.code.trim())
    auth.loginWithData(data)
  } catch (err) {
    error.value = err.response?.data?.error ?? 'Verification failed'
    if (err.response?.status === 401 && /expired/i.test(err.response?.data?.error ?? '')) cancelTotp()
  } finally {
    loading.value = false
  }
}

function cancelTotp() {
  totp.value = { required: false, preAuthToken: '', code: '' }
  error.value = ''
}

async function demoLogin() {
  try {
    const { data } = await api.demoLogin()
    auth.loginWithData(data)
  } catch (err) {
    error.value = err.response?.data?.error ?? 'Demo unavailable'
  }
}

onMounted(async () => {
  try {
    const { data } = await api.demoAvailable()
    demo.value = data
    if (data.available) {
      username.value = data.username
      password.value = data.password
    }
  } catch { /* ignore */ }
})
</script>
