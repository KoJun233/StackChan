import type { Component } from 'vue'
import { createApp, defineComponent, h, nextTick, Teleport } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const settingsApi = vi.hoisted(() => ({
  getSpeechSettings: vi.fn(),
  saveSpeechSettings: vi.fn(),
  testSpeechConnection: vi.fn(),
}))

const toast = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
}))

vi.mock('@/api/modules/settings', () => settingsApi)

vi.mock('@fantastic-admin/components', () => {
  const passthrough = defineComponent({
    setup(_, { slots }) {
      return () => h('div', [slots.title?.(), slots.header?.(), slots.default?.()])
    },
  })

  return {
    FaAlert: passthrough,
    FaButton: defineComponent({
      inheritAttrs: false,
      props: { loading: Boolean },
      setup(_, { attrs, slots }) {
        return () => h('button', { ...attrs }, slots.default?.())
      },
    }),
    FaCard: passthrough,
    FaFixedBar: defineComponent({
      setup(_, { slots }) {
        return () => h(Teleport as unknown as Component, { to: '#fixed-content-after-area' }, slots.default?.())
      },
    }),
    FaForm: defineComponent({
      inheritAttrs: false,
      props: {
        keepValuesOnUnmount: Boolean,
        model: { type: Object, required: true },
      },
      emits: ['submit'],
      setup(props, { attrs, emit, slots }) {
        return () => h('form', {
          ...attrs,
          'data-keep-values-on-unmount': String(props.keepValuesOnUnmount),
          onSubmit: (event: Event) => {
            event.preventDefault()
            emit('submit', { ...props.model })
          },
        }, slots.default?.())
      },
    }),
    FaFormItem: passthrough,
    FaInput: defineComponent({
      inheritAttrs: false,
      props: { modelValue: { type: String, default: '' } },
      emits: ['update:modelValue'],
      setup(props, { attrs, emit }) {
        return () => h('input', {
          ...attrs,
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
        })
      },
    }),
    FaLoading: passthrough,
    FaNumberField: defineComponent({
      inheritAttrs: false,
      props: { modelValue: { type: Number, required: true } },
      emits: ['update:modelValue'],
      setup(props, { attrs, emit }) {
        return () => h('input', {
          ...attrs,
          type: 'number',
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', Number((event.target as HTMLInputElement).value)),
        })
      },
    }),
    FaPageHeader: passthrough,
    FaPageMain: passthrough,
    FaSelect: defineComponent({
      props: {
        modelValue: { type: String, required: true },
        options: { type: Array, required: true },
      },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () => h('select', {
          value: props.modelValue,
          onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
        }, (props.options as Array<{ label: string, value: string }>).map(option => h(
          'option',
          { value: option.value },
          option.label,
        )))
      },
    }),
    useFaToast: () => toast,
  }
})

import SpeechSettings from './index.vue'

describe('speech settings form', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('submits the selected DashScope provider through the native form button', async () => {
    settingsApi.getSpeechSettings.mockResolvedValue({
      apiKeyConfigured: true,
      asrMode: 'REALTIME',
      asrModel: 'fun-asr-flash-2026-06-15',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      providerType: 'OPENAI_COMPATIBLE',
      speechSilenceThreshold: 200,
      speechStartThreshold: 350,
      ttsMode: 'NON_REALTIME',
      ttsModel: 'qwen-audio-3.0-tts-flash',
      ttsVoice: 'longyingtian',
      updatedAt: '2026-07-19T14:59:23Z',
      wakeSensitivity: 'SENSITIVE',
      workspaceId: '',
    })
    settingsApi.saveSpeechSettings.mockImplementation(async input => ({
      ...input,
      apiKeyConfigured: true,
      updatedAt: '2026-07-20T00:10:00Z',
    }))

    const container = document.createElement('div')
    const fixedContentAfterArea = document.createElement('div')
    fixedContentAfterArea.id = 'fixed-content-after-area'
    document.body.append(fixedContentAfterArea)
    document.body.append(container)
    createApp(SpeechSettings).mount(container)

    await vi.waitFor(() => expect(settingsApi.getSpeechSettings).toHaveBeenCalledOnce())
    await nextTick()

    expect(container.querySelector('form')?.dataset.keepValuesOnUnmount).toBe('true')

    const provider = container.querySelector<HTMLSelectElement>('select')
    expect(provider).not.toBeNull()
    provider!.value = 'DASHSCOPE'
    provider!.dispatchEvent(new Event('change', { bubbles: true }))
    await nextTick()

    const modeSelects = container.querySelectorAll<HTMLSelectElement>('select')
    expect(modeSelects).toHaveLength(4)
    modeSelects[1]!.value = 'NON_REALTIME'
    modeSelects[1]!.dispatchEvent(new Event('change', { bubbles: true }))
    modeSelects[2]!.value = 'REALTIME'
    modeSelects[2]!.dispatchEvent(new Event('change', { bubbles: true }))
    modeSelects[3]!.value = 'NORMAL'
    modeSelects[3]!.dispatchEvent(new Event('change', { bubbles: true }))

    const thresholds = container.querySelectorAll<HTMLInputElement>('input[type="number"]')
    expect(thresholds).toHaveLength(2)
    thresholds[0]!.value = '420'
    thresholds[0]!.dispatchEvent(new Event('input', { bubbles: true }))
    thresholds[1]!.value = '210'
    thresholds[1]!.dispatchEvent(new Event('input', { bubbles: true }))

    const asrModel = container.querySelector<HTMLInputElement>('input[placeholder="请输入语音识别模型名"]')
    const ttsModel = container.querySelector<HTMLInputElement>('input[placeholder="请输入语音合成模型名"]')
    expect(asrModel).not.toBeNull()
    expect(ttsModel).not.toBeNull()
    asrModel!.value = 'future-provider-asr-model'
    asrModel!.dispatchEvent(new Event('input', { bubbles: true }))
    ttsModel!.value = 'future-provider-tts-model'
    ttsModel!.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()

    const workspace = container.querySelector<HTMLInputElement>('input[placeholder="llm-..."]')
    expect(workspace).not.toBeNull()
    workspace!.value = 'llm-example-workspace'
    workspace!.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()

    const saveButton = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find(button => button.textContent?.includes('保存配置'))
    expect(saveButton?.type).toBe('submit')
    expect(saveButton?.form?.id).toBe('speech-settings-form')
    saveButton!.click()

    await vi.waitFor(() => expect(settingsApi.saveSpeechSettings).toHaveBeenCalledWith({
      apiKey: '',
      asrMode: 'NON_REALTIME',
      asrModel: 'future-provider-asr-model',
      baseUrl: '',
      providerType: 'DASHSCOPE',
      speechSilenceThreshold: 210,
      speechStartThreshold: 420,
      ttsMode: 'REALTIME',
      ttsModel: 'future-provider-tts-model',
      ttsVoice: 'longyingtian',
      wakeSensitivity: 'NORMAL',
      workspaceId: 'llm-example-workspace',
    }))
    expect(toast.success).toHaveBeenCalledWith('已保存', {
      description: '语音服务与本地唤醒参数已保存；在线机器人会立即接收。',
    })
  })
})
