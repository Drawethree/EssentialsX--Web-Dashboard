<template>
  <Select
    v-if="options.length"
    :model-value="''"
    :options="options"
    placeholder="Apply template…"
    aria-label="Apply punishment template"
    @update:model-value="onPick"
  />
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import Select from './ui/Select.vue'

const emit = defineEmits(['apply'])
const templates = ref([])

const options = computed(() => templates.value.map(t => ({ value: t.id, label: t.label })))

function onPick(id) {
  const t = templates.value.find(x => x.id === id)
  if (t) emit('apply', t)
}

onMounted(async () => {
  try { templates.value = (await api.getTemplates()).data.templates || [] } catch { /* ignore */ }
})
</script>
