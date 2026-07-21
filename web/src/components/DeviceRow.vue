<script setup lang="ts">
import { Square } from 'lucide-vue-next'
import { ref } from 'vue'
import { stopMotion } from '../lib/api'
import type { DashboardDevice } from '../lib/deviceState'

const props = defineProps<{ device: DashboardDevice }>()
const emit = defineEmits<{ offline: [deviceId: string] }>()

const isStopping = ref(false)
const outcome = ref<'pending' | 'offline' | 'error' | null>(null)

function safetyStateLabel(safetyState: string): string {
  const labels: Record<string, string> = {
    motion_disabled: '动作已禁用',
    motion_enabled: '动作已启用',
  }
  return labels[safetyState] ?? safetyState
}

async function requestStop() {
  isStopping.value = true
  outcome.value = null
  try {
    const result = await stopMotion(props.device.id)
    outcome.value = result.status
    if (result.status === 'offline') {
      emit('offline', props.device.id)
    }
  } catch {
    outcome.value = 'error'
  } finally {
    isStopping.value = false
  }
}
</script>

<template>
  <tr class="device-row">
    <td class="device-name-cell">
      <div class="device-identity">
        <span class="connection-indicator" :class="{ online: device.online, offline: !device.online }" aria-hidden="true"></span>
        <strong>{{ device.displayName }}</strong>
        <span class="connection-state" :class="{ offline: !device.online }">{{ device.online ? '在线' : '离线' }}</span>
      </div>
      <p v-if="outcome === 'pending'" class="command-status pending" role="status">停止动作指令已发送，等待设备确认</p>
      <p v-else-if="outcome === 'offline'" class="command-status offline" role="alert">设备已离线，未能发送停止动作指令。</p>
      <p v-else-if="outcome === 'error'" class="command-status error" role="alert">无法发送停止动作指令。</p>
    </td>
    <td class="device-field">
      <div class="device-field-content">
        <span class="field-label">固件版本</span>
        <span class="firmware-version">{{ device.firmwareVersion }}</span>
      </div>
    </td>
    <td class="device-field">
      <div class="device-field-content">
        <span class="field-label">安全状态</span>
        <span class="safety-state">{{ safetyStateLabel(device.safetyState) }}</span>
      </div>
    </td>
    <td class="device-action">
      <button
        class="icon-button stop-button"
        type="button"
        :disabled="isStopping || !device.online"
        :aria-label="`停止 ${device.displayName} 的动作`"
        :title="`停止 ${device.displayName} 的动作`"
        @click="requestStop"
      >
        <Square :size="15" fill="currentColor" aria-hidden="true" />
      </button>
    </td>
  </tr>
</template>
