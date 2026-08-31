import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  completePersonalTask,
  createPersonalTask,
  deletePersonalTask,
  listPersonalTasks,
  reopenPersonalTask,
  updatePersonalTask,
} from './personalTasks'

describe('personal task API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('queries tasks with paging and filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ list: [], total: 0 }), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await listPersonalTasks({ query: '材料', status: 'OPEN', priority: 'HIGH', roleId: 'role-id', from: 20, limit: 10 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/personal-tasks?from=20&limit=10&query=%E6%9D%90%E6%96%99&status=OPEN&priority=HIGH&roleId=role-id',
      expect.any(Object),
    )
  })

  it('uses explicit CRUD and lifecycle endpoints', async () => {
    const input = {
      deviceId: 'a88e4a94-8536-4fa1-91ed-8681b597429d',
      roleId: '00000000-0000-0000-0000-000000000001',
      title: '整理会议材料',
      notes: null,
      priority: 'NORMAL' as const,
      dueAt: null,
      zoneId: 'Asia/Shanghai',
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(input, 201))
      .mockResolvedValueOnce(response(input))
      .mockResolvedValueOnce(response({ ...input, status: 'COMPLETED' }))
      .mockResolvedValueOnce(response({ ...input, status: 'OPEN' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await createPersonalTask(input)
    await updatePersonalTask('task-id', input)
    await completePersonalTask('task-id')
    await reopenPersonalTask('task-id')
    await deletePersonalTask('task-id')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/personal-tasks', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/personal-tasks/task-id', expect.objectContaining({ method: 'PUT' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/personal-tasks/task-id:complete', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/personal-tasks/task-id:reopen', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/personal-tasks/task-id', expect.objectContaining({ method: 'DELETE' }))
  })

  function response(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  }
})
