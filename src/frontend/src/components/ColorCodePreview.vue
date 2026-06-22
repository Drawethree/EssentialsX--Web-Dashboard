<template>
  <div class="p-2 rounded-lg bg-elevated text-sm font-mono break-words whitespace-pre-wrap">
    <span class="text-xs text-muted font-sans mr-1">Preview:</span>
    <template v-if="segments.length">
      <span
        v-for="(s, i) in segments"
        :key="i"
        :style="s.color ? { color: s.color } : null"
        :class="[
          s.bold ? 'font-bold' : '',
          s.italic ? 'italic' : '',
          s.underline && s.strike ? 'underline line-through' : (s.underline ? 'underline' : (s.strike ? 'line-through' : '')),
          s.obfuscated ? 'mc-obfuscated' : '',
        ]"
      >{{ s.text }}</span>
    </template>
    <span v-else class="text-faint">{{ placeholder }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { parseMcColors } from '../utils'

const props = defineProps({
  text: { type: String, default: '' },
  placeholder: { type: String, default: 'Nothing to preview yet.' },
})

const segments = computed(() => parseMcColors(props.text).filter(s => s.text))
</script>

<style scoped>
/* Obfuscated text in Minecraft scrambles; approximate with a subtle blur so it reads as "hidden". */
.mc-obfuscated { filter: blur(0.18em); }
</style>
