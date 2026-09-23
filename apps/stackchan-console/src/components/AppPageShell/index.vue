<script setup lang="ts">
defineOptions({ name: 'AppPageShell' })

withDefaults(defineProps<{
  contentClass?: string
  description?: string
  title: string
  width?: 'normal' | 'form' | 'wide'
}>(), {
  contentClass: '',
  description: '',
  width: 'normal',
})
</script>

<template>
  <div class="app-page-shell" :class="`app-page-shell--${width}`">
    <FaPageHeader :title="title" :description="description" class="mb-0 px-0 pb-5 pt-0 border-0 bg-transparent" main-class="min-w-0 flex-1" default-class="max-w-full">
      <slot name="actions" />
    </FaPageHeader>
    <FaPageMain class="m-0 border-0 rounded-none bg-transparent flex-1 min-h-0 shadow-none" :main-class="['p-0 space-y-5', contentClass]">
      <slot />
    </FaPageMain>
  </div>
</template>

<style scoped>
.app-page-shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  max-width: 1248px;
  min-height: 0;
  padding: 24px;
  margin-inline: auto;
}

.app-page-shell--form { max-width: 848px; }
.app-page-shell--wide { max-width: 1600px; }

@media (width <= 639px) {
  .app-page-shell { padding: 16px 12px; }
}
</style>
