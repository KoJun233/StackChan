<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import type { PairingCode } from '@/api/modules/devices'
import type { SerialPortLike } from '@/utils/serialProvisioning'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { createPairingCode, isPairingCodeExpired } from '@/api/modules/devices'
import {
  getWebSerialApi,
  provisionStackChan,
  updateStackChanServer,
  validateServerBaseUrl,
} from '@/utils/serialProvisioning'

defineOptions({ name: 'DevicePairing' })

const accountStore = useAppAccountStore()
const formRef = useTemplateRef<FormExpose>('formRef')
const model = ref({
  wifiSsid: '',
  wifiPassword: '',
  serverBaseUrl: '',
})
const pairingCode = ref<PairingCode | null>(null)
const selectedPort = shallowRef<SerialPortLike | null>(null)
const now = ref(Date.now())
const generating = ref(false)
const connecting = ref(false)
const provisioning = ref(false)
const provisioningMessage = ref('')
let countdownTimer: ReturnType<typeof setInterval> | undefined

const webSerialAvailable = typeof window !== 'undefined'
  && window.isSecureContext
  && getWebSerialApi() !== null

const validationSchema = toTypedSchema(z.object({
  wifiSsid: z.string().min(1, '请输入 Wi-Fi 名称').refine(value => new TextEncoder().encode(value).byteLength <= 32, 'Wi-Fi 名称不能超过 32 字节'),
  wifiPassword: z.string()
    .refine(value => new TextEncoder().encode(value).byteLength <= 63, 'Wi-Fi 密码不能超过 63 字节'),
  serverBaseUrl: z.string().trim().min(1, '请输入 StackChan 服务地址').refine(validateServerBaseUrl, '请输入 HTTPS 地址，或 LAN 固件使用的私有 IPv4 HTTP 地址'),
}))

const remainingSeconds = computed(() => {
  const expiresAt = pairingCode.value ? Date.parse(pairingCode.value.expiresAt) : Number.NaN
  return Number.isFinite(expiresAt)
    ? Math.max(0, Math.ceil((expiresAt - now.value) / 1000))
    : 0
})
const countdown = computed(() => {
  const minutes = Math.floor(remainingSeconds.value / 60)
  const seconds = String(remainingSeconds.value % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
})
const expired = computed(() => pairingCode.value !== null && isPairingCodeExpired(pairingCode.value, now.value))

function stopCountdown() {
  if (countdownTimer !== undefined) {
    clearInterval(countdownTimer)
    countdownTimer = undefined
  }
}

function updateCountdown() {
  now.value = Date.now()
  if (expired.value) {
    stopCountdown()
  }
}

function startCountdown() {
  stopCountdown()
  updateCountdown()
  if (!expired.value) {
    countdownTimer = setInterval(updateCountdown, 1000)
  }
}

async function generate() {
  generating.value = true
  try {
    pairingCode.value = await createPairingCode(accountStore.account || 'admin')
    startCountdown()
    useFaToast().success('配对码已生成', { description: '请在倒计时结束前完成 USB 安全配置。' })
  }
  catch {
    useFaToast().error('生成失败', { description: '无法生成配对码，请稍后重试。' })
  }
  finally {
    generating.value = false
  }
}

async function connectRobot() {
  const serial = getWebSerialApi()
  if (!window.isSecureContext || !serial) {
    useFaToast().error('浏览器不支持', { description: '请在 localhost 上使用最新版 Edge 或 Chrome。' })
    return
  }
  connecting.value = true
  try {
    selectedPort.value = await serial.requestPort()
    provisioningMessage.value = '已选择机器人串口，可以写入完整配置或只更新服务地址。'
    useFaToast().success('机器人已连接', { description: '下一步填写信息并选择需要的写入方式。' })
  }
  catch (error) {
    if (!(error instanceof DOMException && error.name === 'NotFoundError')) {
      useFaToast().error('连接失败', { description: error instanceof Error ? error.message : '无法选择机器人串口。' })
    }
  }
  finally {
    connecting.value = false
  }
}

const provisioningFailureMessages: Record<string, string> = {
  invalid_request: '机器人拒绝了配网数据，请检查地址格式后重试。',
  identity_clear_failed: '机器人无法安全清除旧身份，请重启后重试。',
  wifi_configuration_failed: '机器人无法保存 Wi-Fi 配置。',
  wifi_connection_failed: '机器人无法连接该 Wi-Fi，请检查名称、密码和信号。',
  claim_failed: 'Wi-Fi 已连接，但服务器配对失败；请检查服务地址和 LAN 服务状态。',
  identity_save_failed: '配对成功，但机器人无法安全保存设备身份。',
}

async function submit(values: typeof model.value) {
  if (!selectedPort.value) {
    useFaToast().error('尚未连接机器人', { description: '请先点击“通过 USB 连接机器人”。' })
    return
  }
  provisioning.value = true
  provisioningMessage.value = '正在生成一次性配对码…'
  const port = selectedPort.value
  try {
    if (!pairingCode.value || isPairingCodeExpired(pairingCode.value)) {
      pairingCode.value = await createPairingCode(accountStore.account || 'admin')
      startCountdown()
    }
    provisioningMessage.value = '正在通过 USB 写入配置，机器人连接 Wi-Fi 可能需要约 30 秒…'
    const result = await provisionStackChan(port, {
      wifiSsid: values.wifiSsid,
      wifiPassword: values.wifiPassword,
      serverBaseUrl: values.serverBaseUrl.trim(),
      pairingCode: pairingCode.value.value,
    }, (status) => {
      if (status === 'started') {
        provisioningMessage.value = '机器人已接收配置，正在连接 Wi-Fi 并完成配对…'
      }
    })
    if (result !== 'complete') {
      throw new Error(provisioningFailureMessages[result] ?? '机器人配网失败，请重试。')
    }
    provisioningMessage.value = '配网完成，机器人正在连接管理服务。'
    useFaToast().success('Wi-Fi 配置成功', { description: '请在设备总览中确认机器人已上线。' })
  }
  catch (error) {
    provisioningMessage.value = error instanceof Error ? error.message : '机器人配网失败，请重试。'
    useFaToast().error('配网失败', { description: provisioningMessage.value })
  }
  finally {
    model.value.wifiPassword = ''
    pairingCode.value = null
    selectedPort.value = null
    stopCountdown()
    provisioning.value = false
  }
}

async function submitForm() {
  await formRef.value?.submit()
}

async function updateServerOnly() {
  if (!selectedPort.value) {
    useFaToast().error('尚未连接机器人', { description: '请先点击“通过 USB 连接机器人”。' })
    return
  }
  const serverBaseUrl = model.value.serverBaseUrl.trim()
  if (!validateServerBaseUrl(serverBaseUrl)) {
    useFaToast().error('服务地址无效', { description: '请输入 HTTPS 地址，或 LAN 固件使用的私有 IPv4 HTTP 地址。' })
    return
  }
  provisioning.value = true
  provisioningMessage.value = '正在生成一次性配对码…'
  const port = selectedPort.value
  try {
    if (!pairingCode.value || isPairingCodeExpired(pairingCode.value)) {
      pairingCode.value = await createPairingCode(accountStore.account || 'admin')
      startCountdown()
    }
    provisioningMessage.value = '正在通过 USB 更新服务地址；现有 Wi-Fi 配置不会改变…'
    const result = await updateStackChanServer(port, {
      serverBaseUrl,
      pairingCode: pairingCode.value.value,
    }, (status) => {
      if (status === 'started') {
        provisioningMessage.value = '机器人已接收新地址，正在重新配对并保存身份…'
      }
    })
    if (result === 'invalid_request') {
      throw new Error('当前固件不支持仅更新服务地址，请先升级固件或使用完整配网。')
    }
    if (result !== 'complete') {
      throw new Error(provisioningFailureMessages[result] ?? '服务地址更新失败，请重试。')
    }
    provisioningMessage.value = '服务地址已更新，机器人正在重启并连接管理服务。'
    useFaToast().success('服务地址更新成功', { description: 'Wi-Fi 配置保持不变，请在设备总览确认机器人重新上线。' })
  }
  catch (error) {
    provisioningMessage.value = error instanceof Error ? error.message : '服务地址更新失败，请重试。'
    useFaToast().error('更新失败', { description: provisioningMessage.value })
  }
  finally {
    pairingCode.value = null
    selectedPort.value = null
    stopCountdown()
    provisioning.value = false
  }
}

async function copyCode() {
  if (!pairingCode.value) {
    return
  }
  if (isPairingCodeExpired(pairingCode.value)) {
    now.value = Date.now()
    stopCountdown()
    return
  }
  try {
    await navigator.clipboard.writeText(pairingCode.value.value)
    useFaToast().success('配对码已复制', { description: '请将它发送到固件的 USB 配置接口。' })
  }
  catch {
    useFaToast().error('复制失败', { description: '请检查浏览器的剪贴板权限后重试。' })
  }
}

onUnmounted(stopCountdown)
</script>

<template>
  <FaPageMain>
    <template #title>
      <div class="space-y-1">
        <div class="text-lg text-foreground font-semibold">
          设备配网与配对
        </div>
        <div class="text-sm text-muted-foreground">
          通过物理 USB 在浏览器中配置 Wi-Fi；密码只发送给机器人，不经过服务端。
        </div>
      </div>
    </template>

    <div class="space-y-4">
      <FaCard>
        <template #header>
          <div class="space-y-1">
            <div class="text-base font-semibold">
              USB 配置 Wi-Fi
            </div>
            <div class="text-sm text-muted-foreground">
              使用最新版 Edge 或 Chrome，并保持机器人通过 USB 连接到这台电脑。
            </div>
          </div>
        </template>

        <FaAlert
          v-if="!webSerialAvailable"
          title="当前浏览器无法使用 Web Serial"
          description="请通过 localhost 打开管理后台，并使用最新版 Microsoft Edge 或 Google Chrome。"
          class="mb-5"
        />
        <div v-else class="mb-5 p-4 border rounded-lg bg-muted/40 flex flex-wrap gap-3 items-center">
          <FaButton type="button" variant="outline" :loading="connecting" :disabled="provisioning" @click="connectRobot">
            {{ selectedPort ? '重新选择机器人' : '通过 USB 连接机器人' }}
          </FaButton>
          <span class="text-sm text-muted-foreground">
            {{ selectedPort ? '已选择串口，接下来可写入配置。' : '浏览器会弹出串口选择窗口。' }}
          </span>
        </div>

        <FaForm
          ref="formRef"
          :model="model"
          :validation-schema="validationSchema"
          scroll-to-error
          class="gap-x-8 gap-y-6 grid grid-cols-1 items-start md:grid-cols-2"
          @submit="submit"
        >
          <FaFormItem name="wifiSsid" label="Wi-Fi 名称" required>
            <FaInput autocomplete="off" placeholder="例如：家里的 Wi-Fi 或电脑热点" class="w-full" />
          </FaFormItem>
          <FaFormItem name="wifiPassword" label="Wi-Fi 密码" description="开放网络可留空；密码不会发送到管理服务或保存于浏览器。">
            <FaInput type="password" autocomplete="new-password" placeholder="请输入 Wi-Fi 密码" class="w-full" />
          </FaFormItem>
          <FaFormItem
            name="serverBaseUrl"
            label="StackChan 服务地址"
            required
            class="md:col-span-2"
            description="当前 LAN 开发固件填写 http://私有IPv4:8080（不能带末尾 /）；正式固件填写 HTTPS 地址。"
          >
            <FaInput autocomplete="off" placeholder="http://192.168.x.x:8080" class="w-full" />
          </FaFormItem>
        </FaForm>

        <div class="mt-6 flex flex-wrap gap-3 items-center">
          <FaButton
            type="button"
            :loading="provisioning"
            :disabled="!webSerialAvailable || !selectedPort"
            @click="submitForm"
          >
            完整写入 Wi-Fi 与服务
          </FaButton>
          <FaButton
            type="button"
            variant="outline"
            :loading="provisioning"
            :disabled="!webSerialAvailable || !selectedPort"
            @click="updateServerOnly"
          >
            仅更新服务地址
          </FaButton>
          <span v-if="provisioningMessage" class="text-sm text-muted-foreground">
            {{ provisioningMessage }}
          </span>
        </div>
        <div class="text-sm text-muted-foreground leading-6 mt-3">
          “仅更新服务地址”不会重写 Wi-Fi 名称或密码；需要机器人运行支持该功能的新固件。
        </div>
      </FaCard>

      <div class="gap-4 grid lg:grid-cols-2">
        <FaCard>
          <template #header>
            <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div class="space-y-1">
                <div class="text-base font-semibold">
                  一次性配对码
                </div>
                <div class="text-sm text-muted-foreground">
                  浏览器配网会自动生成；也可手动生成后用于其他串口工具。
                </div>
              </div>
              <FaButton :loading="generating" @click="generate">
                {{ pairingCode ? '重新生成' : '生成配对码' }}
              </FaButton>
            </div>
          </template>

          <FaAlert v-if="!pairingCode" title="尚未生成配对码" description="生成后可在有效期内复制并用于 USB 配置。" />
          <div v-else class="space-y-4">
            <div class="text-3xl tracking-widest font-mono font-semibold px-4 py-6 text-center border rounded-lg bg-muted/50 break-all">
              {{ pairingCode.value }}
            </div>
            <div class="flex flex-wrap gap-3 items-center justify-between">
              <div v-if="expired" class="text-sm text-destructive">
                配对码已过期，请重新生成。
              </div>
              <div v-else class="text-sm text-muted-foreground">
                剩余有效时间：<span class="text-foreground font-medium font-mono">{{ countdown }}</span>
              </div>
              <FaButton variant="outline" :disabled="expired" @click="copyCode">
                复制配对码
              </FaButton>
            </div>
          </div>
        </FaCard>

        <FaCard>
          <template #header>
            <div class="space-y-1">
              <div class="text-base font-semibold">
                安全配置说明
              </div>
              <div class="text-sm text-muted-foreground">
                配网数据在浏览器与机器人的 USB 串口之间直传。
              </div>
            </div>
          </template>

          <ol class="text-sm leading-6 ps-5 list-decimal space-y-3">
            <li>浏览器只向服务端申请一次性配对码，不会上传 Wi-Fi 名称或密码。</li>
            <li>Wi-Fi 配置只通过物理 USB 串口发送给当前连接的机器人。</li>
            <li>服务地址变化时可单独更新，机器人重新配对成功后会自动重启，原 Wi-Fi 配置保持不变。</li>
            <li>LAN HTTP 仅供受信任私有局域网开发；生产固件仍只接受 HTTPS。</li>
            <li>机器人报告完成后，可在设备总览确认在线状态和心跳。</li>
          </ol>
          <div class="text-sm text-muted-foreground leading-6 mt-5 p-4 border border-primary/20 rounded-lg bg-primary/5">
            配对码仅可使用一次；设备访问令牌绝不会在此页面显示。
          </div>
        </FaCard>
      </div>
    </div>
  </FaPageMain>
</template>
