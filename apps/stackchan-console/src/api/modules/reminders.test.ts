import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createReminder,
  deleteReminder,
  listReminders,
  toLocalDateTimeValue,
  toReminderInstant,
  updateReminder,
} from './reminders'

describe('reminder API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('queries reminders with paging and filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ list: [], total: 0 }), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await listReminders({ content: '外卖', status: 'PENDING', from: 20, limit: 10 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/reminders?from=20&limit=10&content=%E5%A4%96%E5%8D%96&status=PENDING',
      expect.any(Object),
    )
  })

  it('uses the real REST endpoints for create, update and delete', async () => {
    const input = {
      content: '去拿外卖',
      deviceId: 'a88e4a94-8536-4fa1-91ed-8681b597429d',
      scheduledAt: '2026-07-19T10:30:00.000Z',
      zoneId: 'Asia/Shanghai',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'reminder-id', ...input }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'reminder-id', ...input }), {
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await createReminder(input)
    await updateReminder('reminder-id', input)
    await deleteReminder('reminder-id')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/reminders', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/reminders/reminder-id', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(input),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/reminders/reminder-id', expect.objectContaining({
      method: 'DELETE',
    }))
  })

  it('converts between Shanghai local time and an ISO instant deterministically', () => {
    expect(toReminderInstant('2026-07-19T18:30', -480)).toBe('2026-07-19T10:30:00.000Z')
    expect(toLocalDateTimeValue('2026-07-19T10:30:00.000Z', -480)).toBe('2026-07-19T18:30')
  })
})
