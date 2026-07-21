import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import LlmSettingsPanel from './LlmSettingsPanel.vue'
import * as api from '../lib/api'

vi.mock('../lib/api', () => ({ getLlmSettings: vi.fn(), saveLlmSettings: vi.fn() }))

describe('LlmSettingsPanel', () => {
  it('shows that an existing API key is configured without displaying it', async () => {
    vi.mocked(api.getLlmSettings).mockResolvedValue({
      baseUrl: 'https://api.example.com/v1',
      model: 'companion-model',
      systemPrompt: 'prompt',
      apiKeyConfigured: true,
      updatedAt: '2026-07-17T12:00:00Z',
    })

    render(LlmSettingsPanel)

    expect(await screen.findByText('已配置（密钥不会显示或回传）。')).toBeVisible()
    expect(screen.getByLabelText('API 密钥')).toHaveValue('')
  })

  it('saves the entered provider configuration and clears the key field afterward', async () => {
    vi.mocked(api.getLlmSettings).mockResolvedValue({
      baseUrl: '', model: '', systemPrompt: '', apiKeyConfigured: false, updatedAt: null,
    })
    vi.mocked(api.saveLlmSettings).mockResolvedValue({
      baseUrl: 'https://api.example.com/v1',
      model: 'companion-model',
      systemPrompt: 'prompt',
      apiKeyConfigured: true,
      updatedAt: '2026-07-17T12:00:00Z',
    })

    render(LlmSettingsPanel)
    await screen.findByLabelText('接口地址')
    await fireEvent.update(screen.getByLabelText('接口地址'), 'https://api.example.com/v1')
    await fireEvent.update(screen.getByLabelText('模型名称'), 'companion-model')
    await fireEvent.update(screen.getByLabelText('API 密钥'), 'sk-secret')
    await fireEvent.update(screen.getByLabelText('角色设定'), 'prompt')
    await fireEvent.click(screen.getByRole('button', { name: '保存 AI 配置' }))

    expect(api.saveLlmSettings).toHaveBeenCalledWith(expect.objectContaining({
      baseUrl: 'https://api.example.com/v1', model: 'companion-model', systemPrompt: 'prompt', apiKey: 'sk-secret',
    }))
    expect(await screen.findByText('AI 配置已安全保存。')).toBeVisible()
    expect(screen.getByLabelText('API 密钥')).toHaveValue('')
  })
})
