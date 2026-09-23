<script setup lang="ts">
withDefaults(defineProps<{ loading?: boolean }>(), { loading: false })
</script>

<template>
  <div class="min-h-8 min-w-0 relative" :aria-busy="loading">
    <div class="contents" :inert="loading" :aria-hidden="loading || undefined">
      <slot />
    </div>
    <div v-if="loading" class="text-sm py-3 rounded-inherit bg-background/85 flex gap-2 items-center inset-0 justify-center absolute z-10" role="status">
      <FaIcon name="i-line-md:loading-twotone-loop" aria-hidden="true" />
      正在加载…
    </div>
  </div>
</template>
