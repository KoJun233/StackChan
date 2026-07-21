<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import type { SpeechAccessMode, SpeechProviderType, VoiceWakeSensitivity } from '@/api/modules/settings'
import {
  getSpeechSettings,
  saveSpeechSettings,
  testSpeechConnection,
} from '@/api/modules/settings'
import {
  createSpeechSettingsSchema,
} from './speechSettings'

defineOptions({ name: 'SpeechSettings' })

interface SpeechFormModel {
  apiKey: string
  asrMode: SpeechAccessMode
  asrModel: string
  baseUrl: string
  providerType: SpeechProviderType
  speechSilenceThreshold: number
  speechStartThreshold: number
  ttsMode: SpeechAccessMode
  ttsModel: string
  ttsVoice: string
  wakeSensitivity: VoiceWakeSensitivity
  workspaceId: string
}

type SpeechFormSubmitValues = Omit<SpeechFormModel, 'baseUrl' | 'workspaceId'> & {
  baseUrl?: string
  workspaceId?: string
}

const model = ref<SpeechFormModel>({
  apiKey: '',
  asrMode: 'NON_REALTIME',
  asrModel: '',
  baseUrl: '',
  providerType: 'OPENAI_COMPATIBLE',
  speechSilenceThreshold: 200,
  speechStartThreshold: 350,
  ttsMode: 'NON_REALTIME',
  ttsModel: '',
  ttsVoice: '',
  wakeSensitivity: 'SENSITIVE',
  workspaceId: '',
})
const apiKeyConfigured = ref(false)
const loading = ref(false)
const testing = ref(false)

const providerOptions = [
  { label: 'OpenAI-compatible', value: 'OPENAI_COMPATIBLE' },
  { label: '阿里云百炼（华北 2）', value: 'DASHSCOPE' },
]

const accessModeOptions = [
  { label: '非实时（HTTP）', value: 'NON_REALTIME' },
  { label: '实时（WebSocket）', value: 'REALTIME' },
]

const wakeSensitivityOptions = [
  { label: '普通（误唤醒更少）', value: 'NORMAL' },
  { label: '灵敏（推荐）', value: 'SENSITIVE' },
]

const validationSchema = toTypedSchema(createSpeechSettingsSchema(() => apiKeyConfigured.value))

async function load() {
  loading.value = true
  try {
    const settings = await getSpeechSettings()
    model.value = {
      apiKey: '',
      asrMode: settings.asrMode || 'NON_REALTIME',
      asrModel: settings.asrModel || '',
      baseUrl: settings.baseUrl,
      providerType: settings.providerType || 'OPENAI_COMPATIBLE',
      speechSilenceThreshold: settings.speechSilenceThreshold ?? 200,
      speechStartThreshold: settings.speechStartThreshold ?? 350,
      ttsMode: settings.ttsMode || 'NON_REALTIME',
      ttsModel: settings.ttsModel || '',
      ttsVoice: settings.ttsVoice || '',
      wakeSensitivity: settings.wakeSensitivity || 'SENSITIVE',
      workspaceId: settings.workspaceId || '',
    }
    apiKeyConfigured.value = settings.apiKeyConfigured
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取语音配置。' })
  }
  finally {
    loading.value = false
  }
}

async function submit(values: SpeechFormSubmitValues) {
  loading.value = true
  try {
    const settings = await saveSpeechSettings({
      ...values,
      baseUrl: values.providerType === 'OPENAI_COMPATIBLE' ? values.baseUrl ?? '' : '',
      workspaceId: values.providerType === 'DASHSCOPE' ? values.workspaceId ?? '' : '',
    })
    apiKeyConfigured.value = settings.apiKeyConfigured
    model.value.apiKey = ''
    useFaToast().success('已保存', { description: '语音服务与本地唤醒参数已保存；在线机器人会立即接收。' })
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存语音配置。' })
  }
  finally {
    loading.value = false
  }
}

function invalidSubmit(context: { errors: Record<string, string | undefined> }) {
  const description = Object.values(context.errors).find((message): message is string => Boolean(message))
    ?? '请检查表单中的红色错误提示，修正后再保存。'
  useFaToast().error('无法保存', { description })
}

async function testConnection() {
  testing.value = true
  try {
    const result = await testSpeechConnection()
    useFaToast().success('连接成功', { description: result.message || '测试音频已成功生成。' })
  }
  catch (error) {
    useFaToast().error('连接失败', { description: error instanceof Error ? error.message : '语音服务暂时不可用。' })
  }
  finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader title="语音配置" />
    <FaPageMain>
      <FaLoading :loading="loading">
        <FaCard class="mx-auto max-w-4xl">
          <FaForm
            id="speech-settings-form"
            :model="model"
            :validation-schema="validationSchema"
            keep-values-on-unmount
            scroll-to-error
            class="grid grid-cols-1 gap-x-8 gap-y-6 items-start md:grid-cols-2"
            @submit="submit"
            @invalid-submit="invalidSubmit"
          >
            <FaFormItem name="providerType" label="语音服务商" required class="md:col-span-2">
              <FaSelect v-model="model.providerType" :options="providerOptions" class="w-full" />
            </FaFormItem>
            <FaAlert
              title="接入方式严格决定协议"
              description="实时只走 WebSocket，非实时只走 HTTP。模型名会按输入原样发送，不会根据名称自动切换协议，也不会失败后回退到另一种协议。"
              class="md:col-span-2"
            />
            <FaAlert
              v-if="model.providerType === 'OPENAI_COMPATIBLE'"
              title="OpenAI-compatible 协议范围"
              description="当前 OpenAI-compatible 适配器只实现非实时 HTTP 音频接口；选择实时时不会改走 HTTP，连接测试和实际调用会直接失败。"
              class="md:col-span-2"
            />
            <FaFormItem
              v-if="model.providerType === 'OPENAI_COMPATIBLE'"
              name="baseUrl"
              label="接口地址"
              required
              class="md:col-span-2"
              description="OpenAI-compatible 基础地址，例如 https://api.openai.com/v1"
            >
              <FaInput v-model="model.baseUrl" placeholder="https://..." class="w-full" />
            </FaFormItem>
            <FaFormItem
              v-if="model.providerType === 'DASHSCOPE'"
              name="workspaceId"
              label="Workspace ID"
              required
              class="md:col-span-2"
              description="填写百炼业务空间 ID；它不是 API Key。"
            >
              <FaInput v-model="model.workspaceId" autocomplete="off" placeholder="llm-..." class="w-full" />
            </FaFormItem>
            <FaFormItem name="asrMode" label="语音识别接入方式" required>
              <FaSelect v-model="model.asrMode" :options="accessModeOptions" class="w-full" />
            </FaFormItem>
            <FaFormItem name="asrModel" label="语音识别模型" required description="模型名原样传给当前服务商，不限制命名。">
              <FaInput v-model="model.asrModel" placeholder="请输入语音识别模型名" class="w-full" />
            </FaFormItem>
            <FaFormItem name="ttsMode" label="语音合成接入方式" required>
              <FaSelect v-model="model.ttsMode" :options="accessModeOptions" class="w-full" />
            </FaFormItem>
            <FaFormItem name="ttsModel" label="语音合成模型" required description="模型名原样传给当前服务商，不限制命名。">
              <FaInput v-model="model.ttsModel" placeholder="请输入语音合成模型名" class="w-full" />
            </FaFormItem>
            <FaFormItem name="ttsVoice" label="语音音色" required>
              <FaInput v-model="model.ttsVoice" placeholder="请输入语音音色" class="w-full" />
            </FaFormItem>
            <FaFormItem name="apiKey" label="API 密钥" :required="!apiKeyConfigured" :description="apiKeyConfigured ? '已保存密钥；留空即可保留原密钥。' : '密钥只会发送到服务器，不会回显到此页面。'">
              <FaInput v-model="model.apiKey" type="password" autocomplete="new-password" placeholder="sk-..." class="w-full" />
            </FaFormItem>
            <FaAlert
              title="机器人本地唤醒与录音"
              description="保存后会立即同步到在线机器人，并在机器人每次重新连接时补发。降低能量阈值会更容易听到较轻的说话声，但过低可能把环境噪声当作语音。"
              class="md:col-span-2"
            />
            <FaFormItem name="wakeSensitivity" label="唤醒灵敏度" required class="md:col-span-2">
              <FaSelect v-model="model.wakeSensitivity" :options="wakeSensitivityOptions" class="w-full" />
            </FaFormItem>
            <FaFormItem
              name="speechStartThreshold"
              label="开始说话阈值"
              required
              description="平均能量达到此值后开始记录有效语音；数值越低越灵敏。推荐 350。"
            >
              <FaNumberField v-model="model.speechStartThreshold" :min="100" :max="5000" :step="10" class="w-full" />
            </FaFormItem>
            <FaFormItem
              name="speechSilenceThreshold"
              label="静音阈值"
              required
              description="低于此值连续 0.75 秒后结束录音，必须小于开始说话阈值。推荐 200。"
            >
              <FaNumberField v-model="model.speechSilenceThreshold" :min="50" :max="4000" :step="10" class="w-full" />
            </FaFormItem>
            <FaFixedBar position="bottom" class="md:col-span-2 flex gap-3 justify-center">
              <FaButton type="button" variant="outline" :loading="testing" @click="testConnection">
                测试语音识别与合成
              </FaButton>
              <FaButton type="submit" form="speech-settings-form" :loading="loading">
                保存配置
              </FaButton>
            </FaFixedBar>
          </FaForm>
        </FaCard>
      </FaLoading>
    </FaPageMain>
  </div>
</template>
