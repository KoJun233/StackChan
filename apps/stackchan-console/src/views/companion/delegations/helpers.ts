import type { Delegation, DelegationDeliveryStatus, DelegationInput, DelegationRun, DelegationRunStatus } from '@/api/modules/delegations'
import * as z from 'zod'

export function safeSourceUrl(value: string | null | undefined): string | undefined {
  if (!value) {
    return undefined
  }
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : undefined
  }
  catch {
    return undefined
  }
}

export function validTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat('zh-CN', { timeZone: value }).format()
    return Boolean(value.trim())
  }
  catch {
    return false
  }
}

export const delegationFormSchema = z.object({
  deviceId: z.string().min(1, '请选择设备'),
  roleId: z.string().min(1, '请选择伙伴'),
  description: z.string().trim().min(1, '告诉伙伴你想让它做什么').max(4000, '任务描述最多 4000 字'),
  zoneId: z.string().trim().refine(validTimeZone, '请输入有效时区，例如 Asia/Shanghai'),
  deliveryMode: z.enum(['AUTO', 'CONSOLE', 'BARK']),
  enabled: z.boolean(),
})

export type DelegationFormModel = z.infer<typeof delegationFormSchema>

export function emptyDelegationModel(): DelegationFormModel {
  return { deviceId: '', roleId: '', description: '', zoneId: 'Asia/Shanghai', deliveryMode: 'AUTO', enabled: true }
}

export const taskExamples = [
  { label: 'AI 日报', description: '每天北京时间早上 08:30，帮我整理最多 5 条值得关注的 AI 新进展，用中文说明内容和对我的影响，附原文来源，并推送到我的 iPhone。没有新内容时不用推送。' },
  { label: '回顾待办', description: '每个工作日下午 18:00，查看我和你一起记录的待办，帮我总结今天已完成和还没完成的事情，给出明天最值得先做的一件事。把结果保存在控制台。' },
  { label: '日常鼓励', description: '每天北京时间上午 09:00，写一句简短自然的鼓励，帮我轻松开始一天，不要重复套话。把结果保存在控制台。' },
] as const

export function aiDailyExample(): DelegationFormModel {
  return { ...emptyDelegationModel(), description: taskExamples[0].description }
}

export function delegationInput(model: DelegationFormModel): DelegationInput {
  return { deviceId: model.deviceId, roleId: model.roleId, description: model.description.trim(), zoneId: model.zoneId.trim(), enabled: model.enabled }
}

export function modelFromDelegation(task: Delegation): DelegationFormModel {
  return { deviceId: task.deviceId, roleId: task.roleId, description: task.description || task.instruction, zoneId: task.zoneId, deliveryMode: task.deliveryMode, enabled: task.enabled }
}

export function canRetryDelivery(run: DelegationRun): boolean {
  return run.status === 'SUCCEEDED' && run.deliveryStatus === 'FAILED' && Boolean(run.output?.trim() || run.items?.length)
}

export function runStatusLabel(status: DelegationRunStatus, hasActions = false): string {
  return { RUNNING: '正在处理', SUCCEEDED: hasActions ? '已完成' : '已生成内容', NO_UPDATES: '没有新内容', FAILED: '执行失败', BLOCKED: '需要处理', CANCELLED: '已取消' }[status]
}

export function deliveryStatusLabel(status: DelegationDeliveryStatus): string {
  return { NONE: '仅控制台', PENDING: '待投递', SENDING: '正在投递', SENT: 'Bark 已接受', FAILED: '投递失败', UNKNOWN: '投递结果不明', SKIPPED: '未投递' }[status]
}

export function formatDelegationTime(value: string | null | undefined, zoneId = 'Asia/Shanghai'): string {
  if (!value || !Number.isFinite(Date.parse(value))) {
    return '—'
  }
  return new Date(value).toLocaleString('zh-CN', { timeZone: validTimeZone(zoneId) ? zoneId : 'Asia/Shanghai', hour12: false })
}

export function scheduleLabel(task: Delegation): string {
  if (task.scheduleType === 'ONCE') {
    return `一次，${formatDelegationTime(task.onceAt, task.zoneId)}`
  }
  if (task.scheduleType === 'INTERVAL') {
    return `每 ${task.intervalMinutes} 分钟`
  }
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const repeat = task.scheduleType === 'DAILY' ? '每天' : task.daysOfWeek.map(day => days[day - 1]).join('、')
  return `${repeat} ${task.localTime?.slice(0, 5)}（${task.zoneId}）`
}

export function stepStatusLabel(status: string): string {
  return { SUCCEEDED: '完成', SUCCESS: '完成', FAILED: '失败', BLOCKED: '需要处理', RUNNING: '执行中', COMPLETED: '完成' }[status] || status
}

export function runErrorMessage(code: string | null): string {
  const messages: Record<string, string> = {
    DELEGATION_CAPABILITY_BLOCKED: '这次任务缺少所需能力或执行证据，请查看伙伴的说明。',
    DELEGATION_DELIVERY_FAILED: '结果已保存，手机投递失败。请检查通知设置后重新投递。',
    DELEGATION_DELIVERY_UNKNOWN: '手机渠道的响应未能确认，请先检查 Bark 历史。',
    DELEGATION_GENERATION_FAILED: '伙伴这次未能完成任务，请检查模型与连接后重试。',
    DELEGATION_RESULT_INVALID: '本次结果未通过检查，没有作为完成结果交付。',
    DELEGATION_CHANGED: '任务安排在执行期间发生变化，已停止这次执行。',
    DELEGATION_DISABLED: '任务已经暂停。',
    PUSH_CONTENT_TOO_LARGE: '结果已保存，内容过长无法完整推送到手机。请在这里阅读结果。',
    DELEGATION_CAPACITY: '伙伴正在处理其他任务，请稍后重试。',
    EXECUTION_CAPACITY_BUSY: '当前有任务在执行，稍后再试。任务与已有成果仍然保留。',
    SOURCE_SCOPE_VIOLATION: '这份资料超出了你允许的范围，没有读取。补充来源后再重新比较。',
    EVIDENCE_REQUIRED: '实际读取的资料不足以支持完整比较，请查看已获得的信息与缺失来源。',
    DEPENDENCY_UNAVAILABLE: '暂时无法取得所需资料，已有成果仍然保留。',
    INVALID_STATE: '当前任务状态不允许继续执行，请先查看任务是否已暂停或取消。',
  }
  return code ? messages[code] || '本次没有完成，请查看执行说明或稍后重试。' : ''
}
