import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDelegation, DelegationSubmissionError, getBarkSettings, listDelegations, pauseDelegation, resumeDelegation, retryDelegationDelivery, runDelegation, updateBarkSettings, updateDelegation } from './delegations'

describe('持续委托真实 API', () => {
  afterEach(() => vi.unstubAllGlobals())
  function stubFetch() {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { headers: { 'Content-Type': 'application/json' } })))
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }
  it('按设备、伙伴和分页查询，用户输入不会变成 URL 路径', async () => {
    const fetchMock = stubFetch()
    await listDelegations({ deviceId: 'a&b', roleId: '伙伴/一', from: 20, limit: 10 })
    const query = new URL(fetchMock.mock.calls[0][0], 'https://console.example').searchParams
    expect(query.get('deviceId')).toBe('a&b')
    expect(query.get('roleId')).toBe('伙伴/一')
    expect(query.get('from')).toBe('20')
    expect(query.get('limit')).toBe('10')
  })
  it('只提交自然任务描述与伙伴上下文，无需填写来源和预设类别', async () => {
    const fetchMock = stubFetch()
    const input = { deviceId: 'device', roleId: 'role', description: '每周六上午九点，给我一句自然的鼓励。', enabled: true }
    await createDelegation(input)
    await updateDelegation('task/id', input)
    await pauseDelegation('task/id')
    await resumeDelegation('task/id')
    await runDelegation('task/id')
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/delegations', expect.objectContaining({ method: 'POST', body: JSON.stringify(input) }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/delegations/task%2Fid', expect.objectContaining({ method: 'PUT', body: JSON.stringify(input) }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/delegations/task%2Fid:pause', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/delegations/task%2Fid:resume', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/delegations/task%2Fid:run', expect.objectContaining({ method: 'POST' }))
  })
  it('保留追问的结构化信息，提交继续使用CSRF与同源认证', async () => {
    document.cookie = 'XSRF-TOKEN=test-csrf; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'DELEGATION_CLARIFICATION_REQUIRED', message: '你希望每天几点执行？' }), { status: 409 }))
    vi.stubGlobal('fetch', fetchMock)
    const input = { deviceId: 'd', roleId: 'r', description: '每天给我鼓励', enabled: true }
    await expect(createDelegation(input)).rejects.toMatchObject({ code: 'DELEGATION_CLARIFICATION_REQUIRED', message: '你希望每天几点执行？' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/delegations', expect.objectContaining({ credentials: 'same-origin', headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-csrf' }) }))
    expect(new DelegationSubmissionError('code', 'message')).toBeInstanceOf(Error)
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/'
  })
  it('失败投递只针对已保存运行，不调用生成接口', async () => {
    const fetchMock = stubFetch()
    await retryDelegationDelivery('run/id')
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/delegation-runs/run%2Fid:retry-delivery', expect.objectContaining({ method: 'POST' }))
  })
  it('bark Key 仅在设置写入体中传输，查询不传凭据', async () => {
    const fetchMock = stubFetch()
    await getBarkSettings()
    const input = { serverUrl: 'https://api.day.app', deviceKey: 'test-key', clearDeviceKey: false, enabled: true }
    await updateBarkSettings(input)
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/delegation-push/settings', expect.not.objectContaining({ body: expect.anything() }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/delegation-push/settings', expect.objectContaining({ method: 'PUT', body: JSON.stringify(input) }))
  })
})
