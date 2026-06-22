<template>
  <span
    class="inline-flex items-center justify-center rounded overflow-hidden bg-brand-subtle text-brand font-semibold flex-shrink-0 select-none"
    :style="{ width: size + 'px', height: size + 'px', fontSize: Math.round(size * 0.45) + 'px' }"
  >
    <img
      v-if="src && !failed"
      :src="src"
      :width="size"
      :height="size"
      alt=""
      class="w-full h-full"
      loading="lazy"
      @error="onError"
    />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  uuid: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: Number, default: 32 },
})

// Provider fallback chain — if one host is blocked/offline we try the next,
// and finally fall back to a coloured initial so something always renders.
const providers = u => [
  `https://mc-heads.net/avatar/${u}/${props.size * 2}`,
  `https://minotar.net/helm/${u}/${props.size * 2}.png`,
  `https://crafatar.com/avatars/${u}?size=${props.size * 2}&overlay`,
]

const idx = ref(0)
const failed = ref(false)

const list = computed(() => (props.uuid ? providers(props.uuid) : []))
const src = computed(() => list.value[idx.value] || '')
const initial = computed(() => (props.name || '?').charAt(0).toUpperCase())

function onError() {
  if (idx.value < list.value.length - 1) idx.value++
  else failed.value = true
}

watch(() => props.uuid, () => { idx.value = 0; failed.value = false })
</script>
