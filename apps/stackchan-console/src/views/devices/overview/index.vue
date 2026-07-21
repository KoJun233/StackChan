<script setup lang="ts">
import type { Device } from '@/api/modules/devices'
import { listDevices, stopDeviceMotion } from '@/api/modules/devices'

defineOptions({ name: 'DeviceOverview' })

const devices = ref<Device[]>([])
const loading = ref(false)
const commandDeviceId = ref<string | null>(null)
const columns = [
  { accessorKey: 'displayName', header: '设备名称' },
  { accessorKey: 'onlineLabel', header: '状态' },
  { accessorKey: 'firmwareVersion', header: '固件版本' },
  { accessorKey: 'safetyState', header: '安全状态' },
  { accessorKey: 'lastSeenLabel', header: '最近心跳' },
  { id: 'actions', header: '安全操作' },
]
const rows = computed(() => devices.value.map(device => ({
  ...device,
  onlineLabel: device.online ? '在线' : '离线',
  lastSeenLabel: device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString('zh-CN') : '从未上报',
})))

async function load() {
  loading.value = true
  try {
    devices.value = await listDevices()
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法获取设备状态。' })
  }
  finally {
    loading.value = false
  }
}

async function stopDevice(device: Device) {
  if (!device.commandAvailable || commandDeviceId.value) {
    return
  }

  commandDeviceId.value = device.id
  try {
    await stopDeviceMotion(device.id)
    useFaToast().success('安全停止命令已发送', { description: '设备将仅接收停机命令，不会启用任何舵机动作。' })
    await load()
  }
  catch (error) {
    useFaToast().error('安全停止失败', { description: error instanceof Error ? error.message : '设备当前无法接收安全停止命令。' })
  }
  finally {
    commandDeviceId.value = null
  }
}

onMounted(load)
</script>

<template>
  <FaPageMain>
    <template #title>
      <div>
        <div>设备总览</div>
        <p class="mt-1 text-xs text-muted-foreground">在线状态由服务器按最近心跳计算；安全停止只发送停机命令，不会启用任何舵机动作。</p>
      </div>
    </template>
    <FaCard>
      <template #header>
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold">设备状态</h2>
            <p class="text-sm text-muted-foreground">查看设备在线状态、固件与安全状态。</p>
          </div>
          <FaButton variant="outline" size="sm" :loading="loading" @click="load">刷新</FaButton>
        </div>
      </template>
      <FaTable
        :columns="columns"
        :data="rows"
        :empty-text="loading ? '正在加载设备…' : '暂无已配对的设备'"
        row-key="id"
        border
      >
        <template #cell-actions="{ row }">
          <FaButton
            variant="outline"
            size="sm"
            :disabled="!row.original.commandAvailable || commandDeviceId !== null"
            :loading="commandDeviceId === row.original.id"
            @click="stopDevice(row.original)"
          >
            安全停止
          </FaButton>
        </template>
      </FaTable>
    </FaCard>
  </FaPageMain>
</template>
