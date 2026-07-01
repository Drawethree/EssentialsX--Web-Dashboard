<template>
  <span :class="{ 'italic-default': italicByDefault }">
    <span
      v-for="(seg, i) in segments"
      :key="i"
      :style="segStyle(seg)"
      :class="{ 'mc-obf': seg.obfuscated }"
    >{{ seg.text }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { parseMcText } from '../mcText'

const props = defineProps({
  text: { type: String, default: '' },
  // Default colour when a segment has no explicit colour code (e.g. lore renders purple in-game).
  defaultColor: { type: String, default: '' },
  // Lore lines are italic by default in vanilla unless an explicit format resets it.
  italicByDefault: { type: Boolean, default: false },
})

const segments = computed(() => parseMcText(props.text))

function segStyle(seg) {
  const decos = []
  if (seg.underline) decos.push('underline')
  if (seg.strike) decos.push('line-through')
  return {
    color: seg.color || props.defaultColor || undefined,
    fontWeight: seg.bold ? '700' : undefined,
    fontStyle: seg.italic ? 'italic' : undefined,
    textDecoration: decos.length ? decos.join(' ') : undefined,
  }
}
</script>

<style scoped>
.italic-default { font-style: italic; }
.mc-obf { /* approximate the obfuscated effect without animation noise */ }
</style>
