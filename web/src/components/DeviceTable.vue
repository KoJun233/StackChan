<script setup lang="ts">
import type { DashboardDevice } from '../lib/deviceState'
import DeviceRow from './DeviceRow.vue'

defineProps<{ devices: DashboardDevice[] }>()

const emit = defineEmits<{ offline: [deviceId: string] }>()
</script>

<template>
  <table class="device-table" aria-label="已配对设备">
    <thead>
      <tr>
        <th scope="col">设备</th>
        <th scope="col">固件版本</th>
        <th scope="col">安全状态</th>
        <th scope="col">操作</th>
      </tr>
    </thead>
    <tbody>
      <DeviceRow v-for="device in devices" :key="device.id" :device="device" @offline="emit('offline', $event)" />
    </tbody>
  </table>
</template>
