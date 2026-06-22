<template>
  <div class="p-4 sm:p-6 max-w-3xl mx-auto space-y-5">
    <div>
      <h1>Tools</h1>
      <p class="text-sm text-muted">Server-wide announcements and mail.</p>
    </div>

    <div v-if="can('BROADCAST')" class="card space-y-3">
      <h3>Broadcast</h3>
      <p class="text-xs text-muted">Send a message to every online player. Supports &amp; colour codes.</p>
      <textarea v-model="broadcastMsg" rows="2" class="input" placeholder="&aServer restarting in 5 minutes!"></textarea>
      <ColorCodePreview :text="broadcastMsg" placeholder="Type a message to preview colours…" />
      <button class="btn-primary" :disabled="busy" @click="doBroadcast">Broadcast</button>
    </div>

    <div v-if="can('MAIL_MANAGE')" class="card space-y-3">
      <h3>Mail Everyone</h3>
      <p class="text-xs text-muted">Send EssentialsX mail to every known player. They read it with <code class="text-brand">/mail read</code>.</p>
      <textarea v-model="mailMsg" rows="2" class="input" placeholder="Thanks for playing on our server!"></textarea>
      <ColorCodePreview :text="mailMsg" placeholder="Type a message to preview colours…" />
      <button class="btn-primary" :disabled="busy" @click="doMailAll">Send to All</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import ColorCodePreview from '../components/ColorCodePreview.vue'

const auth = useAuthStore()
const toast = useToastStore()
const can = p => auth.hasPermission(p)

const broadcastMsg = ref('')
const mailMsg = ref('')
const busy = ref(false)

async function doBroadcast() {
  if (!broadcastMsg.value.trim()) return
  busy.value = true
  try { await api.broadcast(broadcastMsg.value); toast.success('Broadcast sent'); broadcastMsg.value = '' }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
  finally { busy.value = false }
}

async function doMailAll() {
  if (!mailMsg.value.trim()) return
  busy.value = true
  try { const { data } = await api.mailAll(mailMsg.value); toast.success(`Mailed ${data.sent} players`); mailMsg.value = '' }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
  finally { busy.value = false }
}
</script>
