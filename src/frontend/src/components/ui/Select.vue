<template>
  <SelectRoot :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)">
    <SelectTrigger
      class="input flex items-center justify-between gap-2 w-full"
      :class="triggerClass"
      :aria-label="ariaLabel"
    >
      <SelectValue :placeholder="placeholder" />
      <ChevronDownIcon class="w-4 h-4 text-muted flex-shrink-0" />
    </SelectTrigger>
    <SelectPortal>
      <SelectContent
        position="popper"
        :side-offset="4"
        class="z-[90] bg-overlay border border-edge rounded-lg shadow-lg overflow-hidden min-w-[var(--reka-select-trigger-width)] animate-slide-down-fade"
      >
        <SelectViewport class="p-1 max-h-60">
          <SelectItem
            v-for="opt in normalized"
            :key="opt.value"
            :value="opt.value"
            class="px-2.5 py-1.5 text-sm rounded-md text-secondary data-[highlighted]:bg-brand-subtle data-[highlighted]:text-brand cursor-pointer flex items-center justify-between outline-none select-none"
          >
            <SelectItemText>{{ opt.label }}</SelectItemText>
            <SelectItemIndicator><CheckIcon class="w-4 h-4 text-brand" /></SelectItemIndicator>
          </SelectItem>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>

<script setup>
import { computed } from 'vue'
import {
  SelectRoot, SelectTrigger, SelectValue, SelectPortal, SelectContent,
  SelectViewport, SelectItem, SelectItemText, SelectItemIndicator,
} from 'reka-ui'
import { ChevronDownIcon, CheckIcon } from '@heroicons/vue/24/outline'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // string[] or { value, label }[]
  placeholder: { type: String, default: 'Select…' },
  ariaLabel: { type: String, default: 'Select' },
  triggerClass: { type: String, default: '' },
})
defineEmits(['update:modelValue'])

const normalized = computed(() =>
  props.options.map(o => (typeof o === 'object' ? o : { value: o, label: String(o) })),
)
</script>
