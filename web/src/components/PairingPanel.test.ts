import { fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PairingPanel from './PairingPanel.vue'
import * as api from '../lib/api'

vi.mock('../lib/api', () => ({ createPairingCode: vi.fn() }))

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('PairingPanel', () => {
  it('renders a pairing code returned by the API', async () => {
    vi.mocked(api.createPairingCode).mockResolvedValue('SC-1284')
    render(PairingPanel)

    await fireEvent.update(screen.getByLabelText('创建人'), 'Console operator')
    await fireEvent.click(screen.getByRole('button', { name: '创建配对码' }))

    expect(await screen.findByDisplayValue('SC-1284')).toBeVisible()
  })

  it('shows an API error and retains no generated code', async () => {
    vi.mocked(api.createPairingCode).mockRejectedValue(new Error('配对码暂时不可用。'))
    render(PairingPanel)

    await fireEvent.update(screen.getByLabelText('创建人'), 'Console operator')
    await fireEvent.click(screen.getByRole('button', { name: '创建配对码' }))

    expect(await screen.findByText('配对码暂时不可用。')).toBeVisible()
    expect(screen.queryByDisplayValue('SC-1284')).not.toBeInTheDocument()
  })

  it('copies a generated pairing code through the Clipboard API', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    vi.mocked(api.createPairingCode).mockResolvedValue('SC-1284')
    render(PairingPanel)

    await fireEvent.update(screen.getByLabelText('创建人'), 'Console operator')
    await fireEvent.click(screen.getByRole('button', { name: '创建配对码' }))
    await fireEvent.click(await screen.findByRole('button', { name: '复制配对码' }))

    expect(writeText).toHaveBeenCalledWith('SC-1284')
    expect(await screen.findByText('已复制')).toBeVisible()
  })

  it('shows a clear failure when the Clipboard API rejects and no fallback is available', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error('Denied')) } })
    vi.mocked(api.createPairingCode).mockResolvedValue('SC-1284')
    render(PairingPanel)

    await fireEvent.update(screen.getByLabelText('创建人'), 'Console operator')
    await fireEvent.click(screen.getByRole('button', { name: '创建配对码' }))
    await fireEvent.click(await screen.findByRole('button', { name: '复制配对码' }))

    expect(await screen.findByText('复制失败，请选中配对码后手动复制。')).toBeVisible()
  })
})
