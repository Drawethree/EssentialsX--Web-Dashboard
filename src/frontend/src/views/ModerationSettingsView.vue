<template>
  <div class="p-4 sm:p-6 max-w-4xl mx-auto space-y-6">
    <div>
      <h1>Moderation Settings</h1>
      <p class="text-sm text-muted">Punishment templates and warning escalation thresholds.</p>
    </div>

    <!-- Templates -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <h3>Punishment Templates</h3>
        <Button variant="subtle" class="text-xs" @click="addTemplate">Add template</Button>
      </div>
      <p class="text-xs text-muted">Presets staff can apply in one click when warning, muting or banning.</p>
      <div v-for="(t, i) in templates" :key="i" class="flex flex-col gap-2 sm:grid sm:grid-cols-12 sm:items-center border-b border-edge/50 pb-3 sm:border-0 sm:pb-0">
        <input v-model="t.label" class="input sm:col-span-3" placeholder="Label" />
        <Select v-model="t.type" :options="typeOptions" class="sm:col-span-2" aria-label="Type" />
        <input v-model="t.reason" class="input sm:col-span-4" placeholder="Reason" />
        <div class="flex items-center gap-2 sm:col-span-3">
          <input v-model.number="t.minutes" type="number" min="0" class="input flex-1" placeholder="Minutes (0 = permanent)" title="Duration in minutes (0 = permanent)" />
          <button class="text-red-500 hover:text-red-600 text-sm flex-shrink-0 px-2" aria-label="Remove template" @click="templates.splice(i, 1)">✕</button>
        </div>
      </div>
      <EmptyState v-if="!templates.length" :icon="DocumentTextIcon" title="No templates" hint="Add your first preset above." />
      <div class="flex justify-end">
        <Button :loading="savingTemplates" @click="saveTemplates">Save Templates</Button>
      </div>
    </div>

    <!-- Escalation -->
    <div class="card space-y-3">
      <div class="flex items-center justify-between">
        <h3>Warning Escalation</h3>
        <Button variant="subtle" class="text-xs" @click="addRule">Add rule</Button>
      </div>
      <p class="text-xs text-muted">When a player's warning count reaches a threshold, the action is applied automatically.</p>
      <div v-for="(r, i) in rules" :key="i" class="flex flex-col gap-2 sm:grid sm:grid-cols-12 sm:items-center border-b border-edge/50 pb-3 sm:border-0 sm:pb-0">
        <div class="sm:col-span-4 flex items-center gap-2">
          <span class="text-sm text-muted">At</span>
          <input v-model.number="r.warns" type="number" min="1" class="input w-20" />
          <span class="text-sm text-muted">warnings</span>
        </div>
        <Select v-model="r.type" :options="escalationTypes" class="sm:col-span-3" aria-label="Action" />
        <div class="flex items-center gap-2 sm:col-span-5">
          <input v-model.number="r.minutes" type="number" min="0" class="input flex-1" placeholder="Duration (min, 0 = permanent)" />
          <button class="text-red-500 hover:text-red-600 text-sm flex-shrink-0 px-2" aria-label="Remove rule" @click="rules.splice(i, 1)">✕</button>
        </div>
      </div>
      <EmptyState v-if="!rules.length" :icon="ShieldExclamationIcon" title="No escalation rules" hint="Warnings will only be recorded, not auto-escalated." />
      <div class="flex justify-end">
        <Button :loading="savingRules" @click="saveRules">Save Escalation</Button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import Button from '../components/ui/Button.vue'
import Select from '../components/ui/Select.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { DocumentTextIcon, ShieldExclamationIcon } from '@heroicons/vue/24/outline'

const toast = useToastStore()
const templates = ref([])
const rules = ref([])
const savingTemplates = ref(false)
const savingRules = ref(false)

const typeOptions = [{ value: 'MUTE', label: 'Mute' }, { value: 'BAN', label: 'Ban' }, { value: 'WARN', label: 'Warn' }]
const escalationTypes = [{ value: 'MUTE', label: 'Mute' }, { value: 'BAN', label: 'Ban' }]

function slug(s) {
  return (s || 'template').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || `t${Date.now()}`
}

function addTemplate() {
  templates.value.push({ id: `t${Date.now()}`, label: '', type: 'MUTE', reason: '', minutes: 0 })
}
function addRule() {
  rules.value.push({ warns: (rules.value.at(-1)?.warns || 0) + 1, type: 'MUTE', minutes: 60 })
}

async function load() {
  try {
    const t = (await api.getTemplates()).data.templates || []
    templates.value = t.map(x => ({ ...x, minutes: x.durationMs ? Math.round(x.durationMs / 60000) : 0 }))
  } catch { /* ignore */ }
  try {
    const r = (await api.getEscalation()).data.rules || []
    rules.value = r.map(x => ({ ...x, minutes: x.durationMs ? Math.round(x.durationMs / 60000) : 0 }))
  } catch { /* ignore */ }
}

async function saveTemplates() {
  savingTemplates.value = true
  try {
    const payload = templates.value
      .filter(t => t.label.trim())
      .map(t => ({ id: t.id || slug(t.label), label: t.label.trim(), type: t.type, reason: t.reason, durationMs: (t.minutes || 0) * 60000 }))
    await api.saveTemplates(payload)
    toast.success('Templates saved')
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed to save') }
  finally { savingTemplates.value = false }
}

async function saveRules() {
  savingRules.value = true
  try {
    const payload = rules.value
      .filter(r => r.warns > 0)
      .map(r => ({ warns: r.warns, type: r.type, durationMs: (r.minutes || 0) * 60000 }))
      .sort((a, b) => a.warns - b.warns)
    await api.saveEscalation(payload)
    toast.success('Escalation saved')
  } catch (err) { toast.error(err.response?.data?.error ?? 'Failed to save') }
  finally { savingRules.value = false }
}

onMounted(load)
</script>
