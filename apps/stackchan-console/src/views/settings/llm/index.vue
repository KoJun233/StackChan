<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { getLlmSettings, saveLlmSettings, testLlmConnection } from '@/api/modules/settings'

defineOptions({ name: 'LlmSettings' })

interface LlmFormModel {
  apiKey: string
  baseUrl: string
  model: string
  systemPrompt: string
}

const model = ref<LlmFormModel>({
  baseUrl: '',
  model: 'qwen3.7-plus',
  apiKey: '',
  systemPrompt: '你是一位温暖、可靠、有边界感的 AI 陪伴伙伴。请使用简体中文，主动关心用户的长期状态，但不要编造记忆。',
})
const apiKeyConfigured = ref(false)
const loading = ref(false)
const testing = ref(false)

const validationSchema = toTypedSchema(z.object({
  baseUrl: z.string().url('请输入有效的 HTTP 或 HTTPS 接口地址').refine(value => /^https?:\/\//.test(value), '接口地址必须以 http:// 或 https:// 开头'),
  model: z.string().trim().min(1, '请输入模型名称'),
  apiKey: z.string().max(4096, 'API 密钥过长'),
  systemPrompt: z.string().max(12000, '角色设定不能超过 12000 个字符'),
}).superRefine((value, context) => {
  if (!apiKeyConfigured.value && !value.apiKey.trim()) {
    context.addIssue({ code: 'custom', message: '请填写 API 密钥', path: ['apiKey'] })
  }
}))

async function load() {
  loading.value = true
  try {
    const settings = await getLlmSettings()
    model.value = {
      baseUrl: settings.baseUrl,
      model: settings.model,
      apiKey: '',
      systemPrompt: settings.systemPrompt,
    }
    apiKeyConfigured.value = settings.apiKeyConfigured
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取 AI 配置。' })
  }
  finally {
    loading.value = false
  }
}

async function submit(values: LlmFormModel) {
  loading.value = true
  try {
    const settings = await saveLlmSettings(values)
    apiKeyConfigured.value = settings.apiKeyConfigured
    model.value.apiKey = ''
    useFaToast().success('已保存', { description: 'AI 配置已安全保存，密钥不会再次显示。' })
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存 AI 配置。' })
  }
  finally {
    loading.value = false
  }
}

async function testConnection() {
  testing.value = true
  try {
    const result = await testLlmConnection()
    useFaToast().success('连接成功', { description: result.message || '模型服务可以正常响应。' })
  }
  catch (error) {
    useFaToast().error('连接失败', { description: error instanceof Error ? error.message : '模型服务暂时不可用。' })
  }
  finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <FaPageMain title="AI 配置" description="使用 OpenAI 兼容接口连接你自己的模型服务。API 密钥仅在服务器端加密保存。">
    <FaLoading :loading="loading">
      <FaCard class="max-w-3xl">
        <FaForm :model="model" :validation-schema="validationSchema" scroll-to-error @submit="submit">
          <div class="grid gap-6 md:grid-cols-2">
            <FaFormItem name="baseUrl" label="接口地址" required class="md:col-span-2" description="例如 https://dashscope.aliyuncs.com/compatible-mode/v1">
              <FaInput v-model="model.baseUrl" placeholder="https://..." />
            </FaFormItem>
            <FaFormItem name="model" label="模型名称" required>
              <FaInput v-model="model.model" placeholder="qwen3.7-plus" />
            </FaFormItem>
            <FaFormItem name="apiKey" label="API 密钥" :required="!apiKeyConfigured" :description="apiKeyConfigured ? '已保存密钥；留空即可保留原密钥。' : '密钥只会发送到服务器，不会回显到此页面。'">
              <FaInput v-model="model.apiKey" type="password" autocomplete="new-password" placeholder="sk-..." />
            </FaFormItem>
            <FaFormItem name="systemPrompt" label="角色设定" class="md:col-span-2" description="这段设定会影响陪伴机器人的语气与边界。">
              <FaTextarea v-model="model.systemPrompt" rows="8" align="block" />
            </FaFormItem>
          </div>
          <div class="mt-6 gap-3 flex justify-end">
            <FaButton type="button" variant="outline" :loading="testing" @click="testConnection">
              测试连接
            </FaButton>
            <FaButton type="submit" :loading="loading">
              保存配置
            </FaButton>
          </div>
        </FaForm>
      </FaCard>
    </FaLoading>
  </FaPageMain>
</template>
