import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDelegation, DelegationOperationPendingError, DelegationSubmissionError, getBarkSettings, getDelegationOperation, getDelegationProjectSummary, listDelegationReviewReady, listDelegations, newDelegationOperationKey, pauseDelegation, resumeDelegation, retryDelegationDelivery, runDelegation, runDelegationRevision, submitDelegationFeedback, updateBarkSettings, updateDelegation } from './delegations'

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
  it('待阅入口独立于任务分页，只请求当前筛选范围至多三项', async () => {
    const fetchMock = stubFetch()
    await listDelegationReviewReady({ deviceId: 'a&b', roleId: '伙伴/一' })
    const url = new URL(fetchMock.mock.calls[0][0], 'https://console.example')
    expect(url.pathname).toBe('/api/v1/delegations/review-ready')
    expect(url.searchParams.get('limit')).toBe('3')
    expect(url.searchParams.get('deviceId')).toBe('a&b')
    expect(url.searchParams.get('roleId')).toBe('伙伴/一')
    expect(url.searchParams.has('from')).toBe(false)
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
  it('新建与反馈用随机键、版本和同源 CSRF，认可不携带执行模式', async () => {
    const fetchMock = stubFetch()
    const key = newDelegationOperationKey()
    expect(key).toMatch(/^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/)
    const scope = { deviceId: 'device', roleId: 'role' }
    const input = { kind: 'ACCEPT_RESULT' as const, comment: '', expectedRevision: 3, resourceVersion: 2 }
    await createDelegation({ ...scope, description: '现在比较公开资料', enabled: true }, key)
    await submitDelegationFeedback('task', 'run/id', input, key, scope)
    expect(fetchMock.mock.calls[0][1].headers['Idempotency-Key']).toBe(key)
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/delegation-runs/run%2Fid/feedback', expect.objectContaining({ credentials: 'same-origin', body: JSON.stringify(input), headers: expect.objectContaining({ 'Idempotency-Key': key }) }))
  })
  it('查询收据始终带设备伙伴范围，创建查询可省资源 ID', async () => {
    const fetchMock = stubFetch()
    await getDelegationOperation('anonymous-key-12345', 'DELEGATION_CREATE', { deviceId: 'a&b', roleId: '角色/一' })
    const query = new URL(fetchMock.mock.calls[0][0], 'https://console.example').searchParams
    expect(query.get('deviceId')).toBe('a&b')
    expect(query.get('roleId')).toBe('角色/一')
    expect(query.get('action')).toBe('DELEGATION_CREATE')
    expect(query.has('resourceId')).toBe(false)
  })
  it('创建响应丢失后先查收据，再 GET 原任务，POST 只有一次', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'COMMITTED', resourceIds: { taskId: 'original-task' } })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'original-task' })))
    vi.stubGlobal('fetch', fetchMock)
    const result = await createDelegation({ deviceId: 'd', roleId: 'r', description: '现在比较', enabled: true }, 'lost-create-response-1')
    expect(result.id).toBe('original-task')
    expect(fetchMock.mock.calls.map(call => call[1]?.method || 'GET')).toEqual(['POST', 'GET', 'GET'])
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/delegations/original-task')
  })
  it('处理中收据保留原键，下一次只核对状态，不再写反馈', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('{}', { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'PENDING', resourceIds: {} })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'COMMITTED', resourceIds: { taskId: 'task', feedbackId: 'f' } })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ task: { id: 'task' }, summary: '原成果' })))
    vi.stubGlobal('fetch', fetchMock)
    const scope = { deviceId: 'd', roleId: 'r' }
    const input = { kind: 'ACCEPT_RESULT' as const, comment: '', expectedRevision: 1, resourceVersion: 1 }
    await expect(submitDelegationFeedback('task', 'run', input, 'pending-feedback-key-1', scope)).rejects.toBeInstanceOf(DelegationOperationPendingError)
    await expect(submitDelegationFeedback('task', 'run', input, 'pending-feedback-key-1', scope)).resolves.toMatchObject({ recovered: true, project: { summary: '原成果' } })
    expect(fetchMock.mock.calls.filter(call => call[1]?.method === 'POST')).toHaveLength(1)
    expect(fetchMock.mock.calls[2][0]).toContain('pending-feedback-key-1')
    expect(fetchMock.mock.calls[2][0]).toContain('resourceId=run')
  })
  it('明确的版本冲突保留固定码且不触发收据或新运行', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'REVISION_CONFLICT', message: '成果已更新' }), { status: 409 }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(runDelegationRevision('task', 2, 'conflicting-revision-key', { deviceId: 'd', roleId: 'r' })).rejects.toMatchObject({ code: 'REVISION_CONFLICT', status: 409 })
    expect(fetchMock).toHaveBeenCalledOnce()
  })
  it('服务端重复请求仍处理中时不当成失败，不释放原键', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OPERATION_PENDING' }), { status: 409 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'PENDING', resourceIds: {} })))
    vi.stubGlobal('fetch', fetchMock)
    await expect(runDelegationRevision('task', 2, 'server-pending-key-1', { deviceId: 'd', roleId: 'r' })).rejects.toBeInstanceOf(DelegationOperationPendingError)
    expect(fetchMock.mock.calls.map(call => call[1]?.method || 'GET')).toEqual(['POST', 'GET'])
  })
  it('历史成果接口的401进入现有会话过期流程', async () => {
    const expired = vi.fn()
    window.addEventListener('stackchan:session-expired', expired)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), { status: 401 })))
    await expect(getDelegationProjectSummary('task')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledOnce()
    window.removeEventListener('stackchan:session-expired', expired)
  })
  it('同一天的修订使用新入口、当前版本和原幂等键，不调用旧 run', async () => {
    const fetchMock = stubFetch()
    await runDelegationRevision('task/id', 7, 'revision-run-key-1234', { deviceId: 'd', roleId: 'r' })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/delegations/task%2Fid:run-revision', expect.objectContaining({ method: 'POST', body: JSON.stringify({ expectedRevision: 7 }), headers: expect.objectContaining({ 'Idempotency-Key': 'revision-run-key-1234' }) }))
  })
})
