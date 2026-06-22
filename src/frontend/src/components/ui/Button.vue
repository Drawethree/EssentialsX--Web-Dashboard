<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="[variantClass, block ? 'w-full' : '']"
    class="relative"
  >
    <Spinner v-if="loading" :size="15" />
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'
import Spinner from './Spinner.vue'

const props = defineProps({
  variant: { type: String, default: 'primary' }, // primary | ghost | danger | subtle
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  block: { type: Boolean, default: false },
  type: { type: String, default: 'button' },
})

const variants = {
  primary: 'btn-primary',
  ghost: 'btn-ghost',
  danger: 'btn-danger',
  subtle: 'btn-subtle',
}
const variantClass = computed(() => variants[props.variant] || 'btn-primary')
</script>
