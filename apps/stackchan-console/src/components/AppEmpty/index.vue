<script setup lang="ts">
defineProps<{ description: string }>()
</script>

<template>
  <div class="text-sm text-muted-foreground px-4 py-8 text-center flex flex-col gap-3 items-center justify-center" role="status">
    <p>{{ description }}</p>
    <slot />
  </div>
</template>
