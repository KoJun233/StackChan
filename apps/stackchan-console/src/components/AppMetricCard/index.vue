<script setup lang="ts">
defineOptions({ name: 'AppMetricCard' })

const props = withDefaults(defineProps<{
  description?: string
  icon?: string
  label: string
  tone?: 'default' | 'danger' | 'success' | 'warning'
  value: number | string
}>(), {
  description: '',
  icon: '',
  tone: 'default',
})

const toneClass = computed(() => ({
  danger: 'border-destructive/30 bg-destructive/5 text-destructive',
  default: 'border-border bg-card text-foreground',
  success: 'border-emerald-500/25 bg-emerald-500/5 text-emerald-700 dark:text-emerald-300',
  warning: 'border-amber-500/25 bg-amber-500/5 text-amber-700 dark:text-amber-300',
}[props.tone]))
</script>

<template>
  <div class="p-4 border rounded-xl transition-colors" :class="toneClass">
    <div class="flex gap-3 items-start justify-between">
      <div class="min-w-0">
        <p class="text-xs font-medium opacity-75">
          {{ label }}
        </p>
        <p class="text-2xl tracking-tight font-semibold mt-2">
          {{ value }}
        </p>
      </div>
      <div v-if="icon" class="p-2 rounded-lg bg-background/70 shadow-sm">
        <FaIcon :name="icon" class="size-5" />
      </div>
    </div>
    <p v-if="description" class="text-xs leading-relaxed mt-2 opacity-70">
      {{ description }}
    </p>
  </div>
</template>
