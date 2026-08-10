<script setup lang="ts">
import type { Device } from '@/api/modules/devices'
import type { FirmwareRelease, FirmwareUpdateJob } from '@/api/modules/firmware'
import { createFirmwareUpdateJob, importFirmwareRelease, listFirmwareReleases, listFirmwareUpdateJobs } from '@/api/modules/firmware'
import { testLlmConnection, testSpeechConnection } from '@/api/modules/settings'
import type { ProviderStatus, SystemHealth } from '@/api/modules/systemHealth'
import { getSystemHealth } from '@/api/modules/systemHealth'

defineOptions({ name: 'DeviceHealth' })

interface StagedFile {
  file?: File
  name: string
  progress?: number
  size: number
  status?: 'uploading' | 'success' | 'error'
}

const health = ref<SystemHealth | null>(null)
const releases = ref<FirmwareRelease[]>([])
const jobs = ref<Record<string, FirmwareUpdateJob[]>>({})
const loading = ref(false)
const providerTesting = ref('')
const importing = ref(false)
const actionDeviceId = ref('')
const firmwareVersion = ref('')
const firmwareFiles = ref<StagedFile[]>([])
const selectedReleaseId = ref('')
const confirmations = ref<Record<string, string>>({})
let refreshTimer: ReturnType<typeof setTimeout> | undefined

const releaseOptions = computed(() => releases.value.map(release => ({
  label: `${release.version} · ${Math.ceil(release.artifactSize / 1024)} KiB · ${release.artifactSha256.slice(0, 10)}`,
  value: release.id,
})))

const hasPendingJob = computed(() => Object.values(jobs.value).flat().some(job => job.status === 'READY' || job.status === 'INSTALLING'))

function formatTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN') : '暂无'
}

function providerLabel(provider: ProviderStatus) {
  return provider.provider === 'llm' ? 'AI 模型' : '语音服务'
}

function providerStatus(provider: ProviderStatus) {
  const labels = {
    HEALTHY: '连接正常',
    FAILED: '连接失败',
    UNKNOWN: '尚未测试',
    NOT_CONFIGURED: '未配置',
  }
  return labels[provider.connectivity.status]
}

function latestJob(deviceId: string) {
  return jobs.value[deviceId]?.[0]
}

async function load() {
  loading.value = true
  try {
    const [nextHealth, nextReleases] = await Promise.all([getSystemHealth(), listFirmwareReleases()])
    health.value = nextHealth
    releases.value = nextReleases
    if (!selectedReleaseId.value) {
      selectedReleaseId.value = nextReleases[0]?.id ?? ''
    }
    const jobEntries = await Promise.all(nextHealth.devices.map(async device => (
      [device.id, await listFirmwareUpdateJobs(device.id)] as const
    )))
    jobs.value = Object.fromEntries(jobEntries)
  }
  catch (error) {
    useFaToast().error('健康信息加载失败', { description: error instanceof Error ? error.message : '无法读取系统健康状态。' })
  }
  finally {
    loading.value = false
    scheduleRefresh()
  }
}

function scheduleRefresh() {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
  refreshTimer = hasPendingJob.value
    ? setTimeout(() => void load(), 3000)
    : undefined
}

async function stageFirmware({ file, onProgress }: { file: File, onProgress: (percent: number) => void }) {
  if (!file.name.toLowerCase().endsWith('.bin') || file.size < 256 || file.size > 3 * 1024 * 1024) {
    throw new Error('请选择 256 字节到 3 MiB 的 .bin 应用镜像。')
  }
  onProgress(100)
  return { staged: true }
}

async function importRelease() {
  const file = firmwareFiles.value[0]?.file
  const version = firmwareVersion.value.trim()
  if (!file || !version) {
    useFaToast().error('请填写完整', { description: '请选择固件应用镜像并填写其内嵌版本。' })
    return
  }
  importing.value = true
  try {
    const release = await importFirmwareRelease(file, version)
    firmwareFiles.value = []
    firmwareVersion.value = ''
    selectedReleaseId.value = release.id
    await load()
    useFaToast().success('固件制品已校验', { description: '项目名、内嵌版本、大小与 SHA-256 均已确认。' })
  }
  catch (error) {
    useFaToast().error('固件导入失败', { description: error instanceof Error ? error.message : '制品校验未通过。' })
  }
  finally {
    importing.value = false
  }
}

async function startUpdate(device: Device) {
  if (!selectedReleaseId.value || confirmations.value[device.id]?.trim() !== device.firmwareVersion) {
    return
  }
  actionDeviceId.value = device.id
  try {
    await createFirmwareUpdateJob({
      deviceId: device.id,
      releaseId: selectedReleaseId.value,
      confirmedCurrentVersion: confirmations.value[device.id].trim(),
    })
    confirmations.value[device.id] = ''
    await load()
    useFaToast().success('OTA 任务已创建', { description: '设备将从同源认证端点下载并校验，启动不健康时自动回退。' })
  }
  catch (error) {
    useFaToast().error('OTA 任务创建失败', { description: error instanceof Error ? error.message : '设备状态或版本确认已变化。' })
  }
  finally {
    actionDeviceId.value = ''
  }
}

async function testProvider(provider: ProviderStatus) {
  providerTesting.value = provider.provider
  try {
    if (provider.provider === 'llm') {
      await testLlmConnection()
    }
    else {
      await testSpeechConnection()
    }
    useFaToast().success(`${providerLabel(provider)}连接正常`)
  }
  catch (error) {
    useFaToast().error(`${providerLabel(provider)}连接失败`, { description: error instanceof Error ? error.message : '服务暂时不可用。' })
  }
  finally {
    providerTesting.value = ''
    await load()
  }
}

onMounted(load)
onUnmounted(() => refreshTimer && clearTimeout(refreshTimer))
</script>

<template>
  <div>
    <FaPageHeader title="健康中心" description="集中查看服务、数据库、设备、供应商、备份与待处理任务，并手动发起安全的应用 OTA。" />
    <FaPageMain>
      <FaLoading :loading="loading">
        <FaAlert
          class="mb-6"
          title="应用 OTA 安全边界"
          description="这里只升级应用分区，不修改 NVS。旧固件不会收到未知命令；首次启用仍需一次逐次授权的 USB 引导。OTA 仅允许在线且已声明能力的设备，并要求手工输入当前版本确认。"
        />

        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <FaCard title="服务版本">
            <div class="text-2xl font-semibold">{{ health?.serverVersion ?? '—' }}</div>
            <p class="mt-2 text-sm text-muted-foreground">Flyway V{{ health?.databaseMigration ?? '—' }}</p>
          </FaCard>
          <FaCard title="待处理任务">
            <div class="text-2xl font-semibold">{{ health?.pendingJobs.firmwareUpdates ?? 0 }}</div>
            <p class="mt-2 text-sm text-muted-foreground">固件；另有唤醒 {{ health?.pendingJobs.wakeModels ?? 0 }}、表情 {{ health?.pendingJobs.expressionPacks ?? 0 }}、提醒 {{ health?.pendingJobs.reminders ?? 0 }}</p>
          </FaCard>
          <FaCard title="最近备份">
            <div class="text-base font-semibold">{{ formatTime(health?.backup.lastSuccessfulBackupAt) }}</div>
            <p class="mt-2 text-sm text-muted-foreground">日备份 {{ health?.backup.dailyBackupCount ?? 0 }} · 周备份 {{ health?.backup.weeklyBackupCount ?? 0 }}</p>
          </FaCard>
          <FaCard title="恢复验证">
            <div class="text-base font-semibold">{{ health?.backup.lastRestoreVerificationSuccessful === true ? '通过' : health?.backup.lastRestoreVerificationSuccessful === false ? '失败' : '未执行' }}</div>
            <p class="mt-2 text-sm text-muted-foreground">{{ formatTime(health?.backup.lastRestoreVerificationAt) }}</p>
          </FaCard>
        </div>

        <div class="mt-6 grid gap-6 xl:grid-cols-2">
          <FaCard title="供应商连接" description="状态仅记录最近一次人工连接测试，不展示地址、模型名或凭据。">
            <div class="space-y-3">
              <div v-for="provider in health?.providers ?? []" :key="provider.provider" class="flex items-center justify-between rounded-lg border p-3">
                <div>
                  <div class="font-medium">{{ providerLabel(provider) }}</div>
                  <div class="mt-1 text-xs text-muted-foreground">{{ providerStatus(provider) }} · {{ formatTime(provider.connectivity.checkedAt) }}</div>
                </div>
                <FaButton variant="outline" size="sm" :disabled="!provider.configured" :loading="providerTesting === provider.provider" @click="testProvider(provider)">
                  测试连接
                </FaButton>
              </div>
            </div>
          </FaCard>

          <FaCard title="近期安全错误" description="只显示类别、失败码、设备和时间，不包含音频、转写、回复或认证载荷。">
            <FaEmpty v-if="!health?.recentSafeErrors.length" description="近期没有安全错误" />
            <ul v-else class="space-y-2">
              <li v-for="error in health.recentSafeErrors" :key="`${error.category}-${error.deviceId}-${error.occurredAt}`" class="rounded-lg border p-3 text-sm">
                <div class="font-medium">{{ error.category }} · {{ error.failureCode }}</div>
                <div class="mt-1 text-xs text-muted-foreground">设备 {{ error.deviceId.slice(0, 8) }} · {{ formatTime(error.occurredAt) }}</div>
              </li>
            </ul>
          </FaCard>
        </div>

        <FaDivider class="my-8" />

        <FaCard title="导入应用固件制品" description="服务端会读取 ESP 应用描述并核对项目名 stackchan_firmware、内嵌版本、大小和 SHA-256。">
          <div class="grid gap-4 md:grid-cols-[1fr_1fr_auto] md:items-end">
            <div>
              <div class="mb-2 text-sm font-medium">应用镜像</div>
              <FaFileUpload v-model="firmwareFiles" :max="1" :http-request="stageFirmware" description="选择 3 MiB 以内的 .bin 文件" />
            </div>
            <div>
              <div class="mb-2 text-sm font-medium">内嵌版本</div>
              <FaInput v-model="firmwareVersion" placeholder="必须与镜像 app descriptor 一致" />
            </div>
            <FaButton :loading="importing" @click="importRelease">导入并校验</FaButton>
          </div>
        </FaCard>

        <FaCard class="mt-6" title="逐设备应用 OTA" description="选择已校验制品，并在每台设备上输入当前版本完成显式确认。">
          <div class="mb-5 max-w-xl">
            <div class="mb-2 text-sm font-medium">目标固件制品</div>
            <FaSelect v-model="selectedReleaseId" :options="releaseOptions" placeholder="请先导入固件制品" />
          </div>
          <FaEmpty v-if="!health?.devices.length" description="暂无已配对设备" />
          <div v-else class="space-y-4">
            <section v-for="device in health.devices" :key="device.id" class="rounded-lg border p-4">
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div class="font-semibold">{{ device.displayName }}</div>
                  <div class="mt-1 text-sm text-muted-foreground">版本 {{ device.firmwareVersion }} · RSSI {{ device.rssi ?? '—' }} · {{ device.online ? '在线' : '离线' }}</div>
                  <div class="mt-1 text-xs" :class="device.applicationOtaSupported ? 'text-emerald-600' : 'text-amber-600'">
                    {{ device.applicationOtaSupported ? '应用 OTA 已引导' : '旧固件：需一次 USB 引导，当前不会下发 OTA 命令' }}
                  </div>
                  <div v-if="latestJob(device.id)" class="mt-2 text-xs text-muted-foreground">
                    最近任务：{{ latestJob(device.id)?.fromVersion }} → {{ latestJob(device.id)?.targetVersion }} · {{ latestJob(device.id)?.status }}<span v-if="latestJob(device.id)?.failureCode"> · {{ latestJob(device.id)?.failureCode }}</span>
                  </div>
                </div>
                <div class="grid w-full gap-2 sm:w-auto sm:min-w-80">
                  <FaInput v-model="confirmations[device.id]" :placeholder="`输入当前版本 ${device.firmwareVersion} 确认`" />
                  <FaButton
                    variant="destructive"
                    :loading="actionDeviceId === device.id"
                    :disabled="!selectedReleaseId || !device.online || !device.commandAvailable || !device.applicationOtaSupported || confirmations[device.id]?.trim() !== device.firmwareVersion || latestJob(device.id)?.status === 'READY' || latestJob(device.id)?.status === 'INSTALLING'"
                    @click="startUpdate(device)"
                  >
                    发起应用 OTA
                  </FaButton>
                </div>
              </div>
            </section>
          </div>
        </FaCard>

        <div class="mt-6 flex justify-end">
          <FaButton variant="outline" :loading="loading" @click="load">刷新健康状态</FaButton>
        </div>
      </FaLoading>
    </FaPageMain>
  </div>
</template>
