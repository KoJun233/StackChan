import { createApp, defineComponent, h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

const agentApi = vi.hoisted(() => ({
  createMcpConnection: vi.fn(),
  deleteMcpConnection: vi.fn(),
  getAgentCapabilities: vi.fn(),
  importAgentSkill: vi.fn(),
  listAgentToolInvocations: vi.fn(),
  deleteAgentSkill: vi.fn(),
  updateAgentCapability: vi.fn(),
  updateAgentRuntime: vi.fn(),
  updateMcpConnection: vi.fn(),
  updateAgentSkill: vi.fn(),
}))

vi.mock('@/api/modules/agent', () => agentApi)

vi.mock('@fantastic-admin/components', () => {
  const container = defineComponent({
    props: { description: String, title: String },
    setup(props, { slots }) {
      return () => h('section', [
        props.title ? h('h2', props.title) : null,
        props.description ? h('p', props.description) : null,
        slots.header?.(),
        slots.default?.(),
      ])
    },
  })
  return {
    FaAlert: container,
    FaButton: defineComponent({
      emits: ['click'],
      setup(_, { emit, slots }) {
        return () => h('button', { onClick: () => emit('click') }, slots.default?.())
      },
    }),
    FaCard: container,
    FaLoading: container,
    FaInput: defineComponent({
      props: { modelValue: String },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () => h('input', {
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
        })
      },
    }),
    FaFileUpload: defineComponent({
      props: { modelValue: Array },
      setup() {
        return () => h('div')
      },
    }),
    FaPageHeader: container,
    FaPageMain: container,
    FaSelect: defineComponent({
      props: { modelValue: String },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () => h('select', {
          value: props.modelValue,
          onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
        })
      },
    }),
    FaSwitch: defineComponent({
      props: { disabled: Boolean, modelValue: Boolean },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () => h('button', {
          'aria-pressed': String(props.modelValue),
          'disabled': props.disabled,
          'onClick': () => emit('update:modelValue', !props.modelValue),
        })
      },
    }),
    FaTable: defineComponent({
      setup() {
        return () => h('div')
      },
    }),
    useFaToast: () => ({ error: vi.fn(), success: vi.fn() }),
    useFaModal: () => ({ confirm: vi.fn() }),
  }
})

import AgentCapabilities from './index.vue'

const capabilities = {
  framework: 'spring-ai-alibaba-react-agent',
  frameworkVersion: '1.1.2.2',
  runtime: {
    enabled: true,
    deploymentEnabled: true,
    adminEnabled: true,
    updatedAt: '2026-07-29T00:00:00Z',
  },
  limits: {
    maxToolCalls: 4,
    timeoutSeconds: 20,
    maxToolResultBytes: 8192,
    maxTotalToolResultBytes: 24576,
  },
  builtInTools: [{ id: 'current_date_time', description: '当前时间', enabled: true }],
  skills: [{
    id: '10000000-0000-0000-0000-000000000001',
    name: 'daily-routine',
    description: '每日流程',
    version: '1.0',
    enabled: false,
    contentSha256: 'a'.repeat(64),
    fileCount: 2,
    uncompressedBytes: 128,
    files: ['SKILL.md', 'references/checklist.md'],
    createdAt: '2026-07-29T00:00:00Z',
    updatedAt: '2026-07-29T00:00:00Z',
  }],
  mcp: {
    discoveredAt: '2026-07-29T00:00:00Z',
    connections: [],
    tools: [],
  },
}

describe('Agent capability management page', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('shows the privacy boundary and can immediately disable the Agent', async () => {
    agentApi.getAgentCapabilities.mockResolvedValue(capabilities)
    agentApi.listAgentToolInvocations.mockResolvedValue([])
    agentApi.updateAgentRuntime.mockResolvedValue({ ...capabilities.runtime, enabled: false, adminEnabled: false })
    const container = document.createElement('div')
    document.body.append(container)

    createApp(AgentCapabilities).mount(container)

    await vi.waitFor(() => expect(agentApi.getAgentCapabilities).toHaveBeenCalledWith(false))
    await vi.waitFor(() => expect(container.querySelector('[data-agent-runtime] button')).not.toBeNull())
    expect(container.textContent).toContain('不保存参数、结果正文、对话正文、端点或认证信息')
    expect(container.textContent).toContain('Spring AI Alibaba ReactAgent')
    expect(container.textContent).toContain('默认停用，权限不随包导入')

    container.querySelector<HTMLButtonElement>('[data-agent-runtime] button')?.click()

    await vi.waitFor(() => expect(agentApi.updateAgentRuntime).toHaveBeenCalledWith(false))
    await vi.waitFor(() => expect(agentApi.getAgentCapabilities).toHaveBeenCalledTimes(2))
  })
})
