<script setup lang="ts">
import { RefreshCw } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { listDevices } from '../lib/api'
import { currentListDevices, type DashboardDevice } from '../lib/deviceState'
import DeviceTable from './DeviceTable.vue'
import StatusState from './StatusState.vue'

const devices = ref<DashboardDevice[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorMessage = ref('无法加载设备。')

async function refresh() {
  state.value = 'loading'
  try {
    devices.value = currentListDevices(await listDevices())
    state.value = devices.value.length ? 'ready' : 'empty'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载设备。'
    state.value = 'error'
  }
}

function markDeviceOffline(deviceId: string) {
  devices.value = devices.value.map((device) => (
    device.id === deviceId ? { ...device, online: false } : device
  ))
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <section id="overview" class="console-section" aria-labelledby="device-status-heading">
    <div class="section-heading">
      <div>
        <p class="eyebrow">设备概览</p>
        <h1 id="device-status-heading">设备状态</h1>
      </div>
      <button
        class="icon-button"
        type="button"
        :disabled="state === 'loading'"
        aria-label="刷新设备状态"
        title="刷新设备状态"
        @click="refresh"
      >
        <RefreshCw :size="17" :class="{ spinning: state === 'loading' }" aria-hidden="true" />
      </button>
    </div>

    <DeviceTable v-if="state === 'ready'" :devices="devices" @offline="markDeviceOffline" />
    <StatusState v-else-if="state === 'loading'" kind="loading" message="正在加载设备…" />
    <StatusState v-else-if="state === 'empty'" kind="empty" message="尚未配对任何设备" />
    <StatusState v-else kind="error" :message="errorMessage" @retry="refresh" />
  </section>
</template>
