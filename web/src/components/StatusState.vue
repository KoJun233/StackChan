<script setup lang="ts">
defineProps<{
  kind: 'loading' | 'empty' | 'error'
  message: string
}>()

defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="status-state" :class="`is-${kind}`" role="status">
    <p>{{ message }}</p>
    <button v-if="kind === 'error'" class="secondary-button" type="button" aria-label="重新加载设备" @click="$emit('retry')">
      重试
    </button>
  </div>
</template>
