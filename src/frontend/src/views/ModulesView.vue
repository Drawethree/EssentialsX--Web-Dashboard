<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <div>
      <h1>Modules</h1>
      <p class="text-sm text-muted">EssentialsX add-ons detected on this server.</p>
    </div>

    <!-- Detection hub -->
    <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
      <div v-for="m in modules" :key="m.key" class="card flex items-center gap-3">
        <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" :class="m.installed ? 'bg-green-500' : 'bg-faint'"></span>
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-primary">{{ m.label }}</p>
          <p class="text-xs text-muted truncate">{{ m.installed ? ('v' + (m.version || '?')) : 'Not installed' }}</p>
        </div>
      </div>
    </div>

    <!-- Chat -->
    <div class="card space-y-3">
      <h3>Chat Formatting <span v-if="!chatInstalled" class="text-xs text-faint font-normal">(EssentialsXChat not installed — editing the format still works)</span></h3>
      <div v-if="can('MODULES_MANAGE')">
        <label class="label">Default format</label>
        <input v-model="chat.format" class="input font-mono" placeholder="{DISPLAYNAME}: {MESSAGE}" />
        <ColorCodePreview class="mt-2" :text="chatPreview" placeholder="Type a format to preview…" />
        <div v-if="Object.keys(chat.groupFormats).length" class="mt-3 space-y-2">
          <p class="label">Per-group formats</p>
          <div v-for="(fmt, group) in chat.groupFormats" :key="group" class="flex items-center gap-2">
            <span class="text-xs text-muted w-24 truncate">{{ group }}</span>
            <input v-model="chat.groupFormats[group]" class="input font-mono text-xs" />
          </div>
        </div>
        <button class="btn-primary mt-3" @click="saveChat">Save Chat Format</button>
      </div>
      <p v-else class="text-sm text-muted">You don't have permission to edit modules.</p>
    </div>

    <!-- Protect -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <h3>Protect Settings</h3>
        <span v-if="protect.installed" class="badge-online">installed</span>
      </div>
      <YamlEditor v-model="protect.content" :readonly="!can('MODULES_MANAGE')" @update:valid="v => protectValid = v" />
      <button v-if="can('MODULES_MANAGE')" class="btn-primary" :disabled="!protectValid" @click="saveProtect">Save Protect Settings</button>
    </div>

    <!-- Discord -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <h3>Discord</h3>
        <span v-if="discord.installed" class="badge-online">installed</span>
        <span v-else class="badge-offline">not installed</span>
      </div>
      <template v-if="discord.installed">
        <p class="text-xs text-muted">Bot token is hidden — leave it unchanged to keep the current token.</p>
        <YamlEditor v-model="discord.content" :readonly="!can('MODULES_MANAGE')" @update:valid="v => discordValid = v" />
        <button v-if="can('MODULES_MANAGE')" class="btn-primary" :disabled="!discordValid" @click="saveDiscord">Save Discord Config</button>
      </template>
      <p v-else class="text-sm text-muted">Install EssentialsXDiscord to relay chat and events to Discord.</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import YamlEditor from '../components/YamlEditor.vue'
import ColorCodePreview from '../components/ColorCodePreview.vue'

const auth = useAuthStore()
const toast = useToastStore()
const can = p => auth.hasPermission(p)

const modules = ref([])
const chat = ref({ format: '', groupFormats: {} })
const chatInstalled = ref(false)
const protect = ref({ content: '', installed: false })
const protectValid = ref(true)
const discord = ref({ content: '', installed: false })
const discordValid = ref(true)

// Substitute the placeholder tokens but keep colour codes — ColorCodePreview renders them.
const chatPreview = computed(() =>
  (chat.value.format || '')
    .replace(/\{DISPLAYNAME\}/g, 'Steve')
    .replace(/\{USERNAME\}/g, 'Steve')
    .replace(/\{MESSAGE\}/g, 'Hello world!')
    .replace(/\{GROUP\}/g, 'default'),
)

async function loadAll() {
  try { modules.value = (await api.getModules()).data.modules } catch { /* ignore */ }
  try { const c = (await api.getChatModule()).data; chat.value = { format: c.format, groupFormats: c.groupFormats || {} }; chatInstalled.value = c.installed } catch { /* ignore */ }
  try { protect.value = (await api.getProtectModule()).data } catch { /* ignore */ }
  try { discord.value = (await api.getDiscordModule()).data } catch { /* ignore */ }
}

async function saveChat() {
  try { await api.saveChatModule(chat.value.format, chat.value.groupFormats); toast.success('Chat format saved') }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
}
async function saveProtect() {
  try { await api.saveProtectModule(protect.value.content); toast.success('Protect settings saved') }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
}
async function saveDiscord() {
  try { await api.saveDiscordModule(discord.value.content); toast.success('Discord config saved') }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed') }
}

onMounted(loadAll)
</script>
