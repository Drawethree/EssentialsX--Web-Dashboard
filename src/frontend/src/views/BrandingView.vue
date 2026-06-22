<template>
  <div class="p-4 sm:p-6 max-w-3xl mx-auto space-y-5">
    <div>
      <h1>Branding</h1>
      <p class="text-sm text-muted">White-label the dashboard with your own name, colour, and logo.</p>
    </div>

    <div class="card space-y-4">
      <div>
        <label class="label">Panel name</label>
        <input v-model="form.serverName" class="input" maxlength="48" placeholder="My Server Panel" />
        <p class="text-xs text-muted mt-1">Shown in the sidebar, login screen, and browser tab.</p>
      </div>

      <div>
        <label class="label">Accent colour</label>
        <div class="flex items-center gap-3">
          <input v-model="form.accentColor" type="color" class="w-12 h-10 rounded-lg border border-edge bg-transparent cursor-pointer" />
          <input v-model="form.accentColor" class="input w-32 font-mono" maxlength="7" />
          <div class="flex gap-1.5">
            <button v-for="c in presets" :key="c" type="button"
                    class="w-7 h-7 rounded-full border border-edge transition-transform hover:scale-110"
                    :style="{ backgroundColor: c }" :title="c" @click="form.accentColor = c"></button>
          </div>
        </div>
        <div class="mt-3 flex items-center gap-2">
          <button type="button" class="px-3 py-1.5 rounded-lg text-white text-sm font-medium" :style="{ backgroundColor: form.accentColor }">Primary button</button>
          <span class="badge" :style="{ backgroundColor: subtle, color: form.accentColor }">Badge</span>
          <span class="text-sm font-medium" :style="{ color: form.accentColor }">Accent text</span>
        </div>
      </div>

      <div class="flex justify-end">
        <button class="btn-primary" :disabled="saving" @click="save">Save changes</button>
      </div>
    </div>

    <div class="card space-y-4">
      <div>
        <h2 class="text-base font-semibold text-primary">Logo</h2>
        <p class="text-xs text-muted">PNG, JPG, GIF, WEBP or SVG, up to 1&nbsp;MB. Square images look best.</p>
      </div>
      <div class="flex items-center gap-4">
        <img :src="branding.logoUrl || fallbackLogo" alt="Logo preview" class="w-16 h-16 rounded-xl border border-edge object-contain bg-elevated" />
        <div class="flex gap-2">
          <button class="btn-subtle" :disabled="uploading" @click="pickFile">{{ uploading ? 'Uploading…' : 'Upload logo' }}</button>
          <button v-if="branding.hasLogo" class="btn-ghost text-red-500" @click="resetLogo">Reset to default</button>
        </div>
        <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/svg+xml" class="hidden" @change="onFile" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import { useBrandingStore } from '../stores/branding'
import fallbackLogo from '../assets/logo.png'

const toast = useToastStore()
const branding = useBrandingStore()
const presets = ['#e13c43', '#2563eb', '#16a34a', '#9333ea', '#ea580c', '#0891b2', '#db2777', '#475569']

const form = reactive({ serverName: branding.serverName, accentColor: branding.accentColor })
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref(null)

const subtle = computed(() => {
  const m = /^#?([0-9a-f]{6})$/i.exec(form.accentColor || '')
  if (!m) return 'transparent'
  const n = parseInt(m[1], 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, 0.12)`
})

async function save() {
  if (!/^#[0-9a-fA-F]{6}$/.test(form.accentColor)) { toast.error('Accent colour must be a hex value like #e13c43'); return }
  saving.value = true
  try {
    const { data } = await api.saveBranding({ serverName: form.serverName, accentColor: form.accentColor })
    branding.apply(data)
    toast.success('Branding updated')
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to save branding')
  } finally {
    saving.value = false
  }
}

function pickFile() { fileInput.value?.click() }

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    await api.uploadLogo(fd)
    await branding.fetch()
    toast.success('Logo updated')
  } catch (err) {
    toast.error(err.response?.data?.error ?? 'Failed to upload logo')
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

async function resetLogo() {
  try {
    await api.deleteLogo()
    await branding.fetch()
    toast.success('Logo reset to default')
  } catch { toast.error('Failed to reset logo') }
}

onMounted(() => {
  form.serverName = branding.serverName
  form.accentColor = branding.accentColor
})
</script>
