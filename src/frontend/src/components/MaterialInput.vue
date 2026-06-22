<template>
  <div class="flex items-center gap-2">
    <span class="w-9 h-9 rounded border border-edge bg-elevated flex items-center justify-center flex-shrink-0">
      <ItemIcon :material="modelValue" :size="28" />
    </span>
    <div class="flex-1">
      <input
        :value="modelValue"
        :list="listId"
        class="input font-mono"
        :placeholder="placeholder"
        autocomplete="off"
        @input="$emit('update:modelValue', $event.target.value)"
      />
      <datalist :id="listId">
        <option v-for="m in filtered" :key="m" :value="m" />
      </datalist>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ItemIcon from './ItemIcon.vue'
import { useMaterials } from '../stores/materials'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: 'diamond_sword' },
})
defineEmits(['update:modelValue'])

const { list } = useMaterials()
const listId = `mats-${Math.random().toString(36).slice(2, 8)}`

// Native <datalist> filters as you type; we cap the option count so the DOM stays light.
const filtered = computed(() => {
  const q = (props.modelValue || '').toLowerCase().trim()
  const all = list.value
  if (!q) return all.slice(0, 150)
  return all.filter(m => m.includes(q)).slice(0, 150)
})
</script>
