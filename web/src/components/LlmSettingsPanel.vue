<script setup lang="ts">
import { CheckCircle2, Save } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import { getLlmSettings, saveLlmSettings } from '../lib/api'

const defaultSystemPrompt = '你是 StackChan，一位温柔、可靠、尊重边界的 AI 陪伴机器人。使用自然、简洁的中文交流；在不确定时诚实说明，不假装拥有现实世界的感受或经历。'

const form = reactive({
  baseUrl: '',
  model: '',
  systemPrompt: defaultSystemPrompt,
  apiKey: '',
})
const isLoading = ref(true)
const isSaving = ref(false)
const apiKeyConfigured = ref(false)
const successMessage = ref('')
const errorMessage = ref('')

async function loadSettings() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const settings = await getLlmSettings()
    form.baseUrl = settings.baseUrl
    form.model = settings.model
    form.systemPrompt = settings.systemPrompt || defaultSystemPrompt
    apiKeyConfigured.value = settings.apiKeyConfigured
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法读取 AI 配置。'
  } finally {
    isLoading.value = false
  }
}

async function saveSettings() {
  successMessage.value = ''
  errorMessage.value = ''
  isSaving.value = true
  try {
    const settings = await saveLlmSettings({ ...form })
    form.baseUrl = settings.baseUrl
    form.model = settings.model
    form.systemPrompt = settings.systemPrompt
    form.apiKey = ''
    apiKeyConfigured.value = settings.apiKeyConfigured
    successMessage.value = 'AI 配置已安全保存。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法保存 AI 配置。'
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section id="llm-settings" class="console-section" aria-labelledby="llm-settings-heading">
    <div class="section-heading">
      <div>
        <p class="eyebrow">AI 大脑</p>
        <h2 id="llm-settings-heading">LLM 服务配置</h2>
      </div>
    </div>

    <p class="section-description">填写兼容 OpenAI API 的服务地址、模型与角色设定。密钥会加密保存，读取时不会返回到浏览器。</p>

    <form class="llm-form" @submit.prevent="saveSettings">
      <label class="input-label" for="llm-base-url">接口地址</label>
      <input
        id="llm-base-url"
        v-model="form.baseUrl"
        class="text-input"
        type="url"
        maxlength="2048"
        placeholder="https://api.openai.com/v1"
        autocomplete="url"
        required
      />
      <p class="field-help">可填写云端服务或自建服务，例如本地 Ollama 的兼容接口。</p>

      <label class="input-label field-label-top" for="llm-model">模型名称</label>
      <input
        id="llm-model"
        v-model="form.model"
        class="text-input"
        type="text"
        maxlength="160"
        placeholder="例如：gpt-4.1-mini"
        autocomplete="off"
        required
      />

      <label class="input-label field-label-top" for="llm-api-key">API 密钥</label>
      <input
        id="llm-api-key"
        v-model="form.apiKey"
        class="text-input"
        type="password"
        maxlength="4096"
        autocomplete="new-password"
        :placeholder="apiKeyConfigured ? '已配置；留空即可保留当前密钥' : '首次保存必须填写'"
      />
      <p class="field-help" :class="{ 'configured-help': apiKeyConfigured }">
        {{ apiKeyConfigured ? '已配置（密钥不会显示或回传）。' : '首次保存时需要填写 API 密钥。' }}
      </p>

      <label class="input-label field-label-top" for="llm-system-prompt">角色设定</label>
      <textarea
        id="llm-system-prompt"
        v-model="form.systemPrompt"
        class="text-area"
        maxlength="12000"
        rows="6"
        autocomplete="off"
      />
      <p class="field-help">这段设定会作为机器人与人交流时的长期性格和边界基础。</p>

      <div class="form-actions">
        <button class="primary-button" type="submit" :disabled="isLoading || isSaving">
          <Save :size="16" aria-hidden="true" />
          {{ isSaving ? '正在保存…' : '保存 AI 配置' }}
        </button>
        <p v-if="successMessage" class="form-success" role="status">
          <CheckCircle2 :size="16" aria-hidden="true" />
          {{ successMessage }}
        </p>
      </div>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    </form>
  </section>
</template>
