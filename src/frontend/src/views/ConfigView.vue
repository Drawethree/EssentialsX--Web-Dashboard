<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-4 h-full flex flex-col">
    <div class="flex items-center justify-between">
      <div>
        <h1>Config Editor</h1>
        <p class="text-sm text-muted">Edit the EssentialsX <code class="text-brand">config.yml</code>. Saving reloads Essentials.</p>
      </div>
      <div class="flex gap-2">
        <button class="btn-ghost" @click="load" :disabled="loading">Reload</button>
        <button v-if="can('CONFIG_MANAGE')" class="btn-primary" @click="save" :disabled="saving || !valid">Save</button>
      </div>
    </div>

    <YamlEditor v-model="content" :readonly="!can('CONFIG_MANAGE')" @update:valid="v => valid = v" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import YamlEditor from '../components/YamlEditor.vue'

const auth = useAuthStore()
const toast = useToastStore()
const can = p => auth.hasPermission(p)

const content = ref('')
const loading = ref(false)
const saving = ref(false)
const valid = ref(true)

async function load() {
  loading.value = true
  try { content.value = (await api.getConfig()).data.content }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to load config') }
  finally { loading.value = false }
}

async function save() {
  saving.value = true
  try { await api.saveConfig(content.value); toast.success('Config saved and reloaded') }
  catch (err) { toast.error(err.response?.data?.error ?? 'Failed to save config') }
  finally { saving.value = false }
}

onMounted(load)
</script>
