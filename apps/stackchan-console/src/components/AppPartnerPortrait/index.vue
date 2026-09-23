<script setup lang="ts">
const props = withDefaults(defineProps<{ name?: string, color?: string, compact?: boolean }>(), {
  name: '伙伴',
  color: '',
  compact: false,
})
const accent = computed(() => /^#[\da-f]{6}$/i.test(props.color) ? props.color : 'oklch(var(--primary))')
</script>

<template>
  <div class="partner-portrait" :class="{ compact }" :style="{ '--partner-color': accent }" role="img" :aria-label="`${name}的形象示意`">
    <svg viewBox="0 0 160 160" aria-hidden="true">
      <rect x="13" y="19" width="134" height="122" rx="46" fill="currentColor" opacity=".12" />
      <rect x="25" y="35" width="110" height="90" rx="33" fill="currentColor" opacity=".18" />
      <path d="M54 65v22m52-22v22" stroke="currentColor" stroke-width="15" stroke-linecap="round" />
      <path d="M72 100q8 7 16 0" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
    </svg>
  </div>
</template>

<style scoped>
.partner-portrait {
  flex: 0 0 auto;
  width: clamp(100px, 12vw, 160px);
  color: var(--partner-color);
}

.partner-portrait.compact {
  width: 44px;
}

.partner-portrait svg {
  display: block;
  width: 100%;
}
</style>
