import type { Delegation, DelegationRun } from '@/api/modules/delegations'
import { describe, expect, it } from 'vitest'
import { aiDailyExample, canRetryDelivery, delegationFormSchema, delegationInput, emptyDelegationModel, runStatusLabel, safeSourceUrl, scheduleLabel, taskExamples } from './helpers'

describe('持续任务描述与结果保护', () => {
  it('不同目标只需自然描述，示例不会附加RSS或研究类型', () => {
    for (const example of taskExamples) {
      const model = { ...emptyDelegationModel(), deviceId: 'device', roleId: 'role', description: example.description }
      expect(delegationFormSchema.safeParse(model).success).toBe(true)
      expect(delegationInput(model)).toEqual({ deviceId: 'device', roleId: 'role', description: example.description, zoneId: 'Asia/Shanghai', enabled: true })
    }
    expect(aiDailyExample().description).toContain('08:30')
    expect(aiDailyExample().description).toContain('5 条')
  })
  it('校验描述与上下文，时间不完整交给AI追问而非表单固定必填', () => {
    const model = { ...emptyDelegationModel(), deviceId: 'd', roleId: 'r', description: ' 每天给我一句鼓励 ' }
    expect(delegationFormSchema.safeParse(model).success).toBe(true)
    expect(delegationInput(model).description).toBe('每天给我一句鼓励')
    expect(delegationFormSchema.safeParse({ ...model, description: ' ' }).success).toBe(false)
    expect(delegationFormSchema.safeParse({ ...model, description: 'a'.repeat(4001) }).success).toBe(false)
    expect(delegationFormSchema.safeParse({ ...model, deviceId: '' }).success).toBe(false)
    expect(delegationFormSchema.safeParse({ ...model, zoneId: 'Mars/Base' }).success).toBe(false)
  })
  it('仅渲染无凭据HTTPS证据链接', () => {
    expect(safeSourceUrl('https://example.com/article?q=1')).toBe('https://example.com/article?q=1')
    for (const value of ['javascript:alert(1)', 'data:text/html,hello', 'http://example.com', 'https://key@example.com', null]) {
      expect(safeSourceUrl(value)).toBeUndefined()
    }
  })
  it('通用正文也可重试失败投递，未知投递与未完成任务不可重发', () => {
    const run = { status: 'SUCCEEDED', output: '今天已完成两项待办。', items: [], deliveryStatus: 'FAILED' } as unknown as DelegationRun
    expect(canRetryDelivery(run)).toBe(true)
    expect(canRetryDelivery({ ...run, deliveryStatus: 'UNKNOWN' })).toBe(false)
    expect(canRetryDelivery({ ...run, status: 'BLOCKED' })).toBe(false)
    expect(canRetryDelivery({ ...run, output: '' })).toBe(false)
  })
  it('纯内容生成不宣称已经执行外部动作', () => {
    expect(runStatusLabel('SUCCEEDED')).toBe('已生成内容')
    expect(runStatusLabel('SUCCEEDED', true)).toBe('已完成')
    expect(runStatusLabel('BLOCKED', true)).toBe('需要处理')
  })
  it('一次、每周与间隔安排可读，不将所有任务显示成每日研究', () => {
    const task = { zoneId: 'Asia/Shanghai', localTime: '18:00:00', scheduleType: 'WEEKLY', daysOfWeek: [1, 2, 3, 4, 5] } as Delegation
    expect(scheduleLabel(task)).toBe('周一、周二、周三、周四、周五 18:00（Asia/Shanghai）')
    expect(scheduleLabel({ ...task, scheduleType: 'INTERVAL', intervalMinutes: 30 })).toBe('每 30 分钟')
    expect(scheduleLabel({ ...task, scheduleType: 'ONCE', onceAt: '2026-10-10T00:00:00Z' })).toContain('一次，')
  })
})
