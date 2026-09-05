<script setup lang="ts">
defineOptions({ name: 'AppPageShell' })

withDefaults(defineProps<{
  contentClass?: string
  description: string
  title: string
}>(), {
  contentClass: '',
})
</script>

<template>
  <div class="flex flex-1 flex-col min-h-0">
    <FaPageHeader :title="title" :description="description" class="mb-0 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
      <slot name="actions" />
    </FaPageHeader>
    <FaPageMain class="flex-1 min-h-0" :main-class="['space-y-4', contentClass]">
      <slot />
    </FaPageMain>
  </div>
</template>
