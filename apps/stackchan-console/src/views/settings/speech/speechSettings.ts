import * as z from 'zod'

export function isValidSpeechBaseUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol)
  }
  catch {
    return false
  }
}

export function isValidDashScopeWorkspaceId(value: string): boolean {
  return /^(?=.{3,63}$)[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/.test(value)
}

export function createSpeechSettingsSchema(apiKeyConfigured: () => boolean) {
  return z.object({
    providerType: z.enum(['OPENAI_COMPATIBLE', 'DASHSCOPE']),
    baseUrl: z.string().trim().max(2048, '接口地址不能超过 2048 个字符').optional(),
    workspaceId: z.string().trim().max(160, 'Workspace ID 不能超过 160 个字符').optional(),
    asrMode: z.enum(['REALTIME', 'NON_REALTIME']),
    asrModel: z.string().trim().min(1, '请输入语音识别模型').max(160, '模型名称不能超过 160 个字符'),
    ttsMode: z.enum(['REALTIME', 'NON_REALTIME']),
    ttsModel: z.string().trim().min(1, '请输入语音合成模型').max(160, '模型名称不能超过 160 个字符'),
    ttsVoice: z.string().trim().min(1, '请输入语音音色').max(160, '音色不能超过 160 个字符'),
    wakeSensitivity: z.enum(['NORMAL', 'SENSITIVE']),
    speechStartThreshold: z.number({ message: '请输入开始说话阈值' })
      .int('开始说话阈值必须是整数')
      .min(100, '开始说话阈值不能小于 100')
      .max(5000, '开始说话阈值不能大于 5000'),
    speechSilenceThreshold: z.number({ message: '请输入静音阈值' })
      .int('静音阈值必须是整数')
      .min(50, '静音阈值不能小于 50')
      .max(4000, '静音阈值不能大于 4000'),
    apiKey: z.string().max(4096, 'API 密钥过长'),
  }).superRefine((value, context) => {
    if (!apiKeyConfigured() && !value.apiKey.trim()) {
      context.addIssue({ code: 'custom', message: '请填写 API 密钥', path: ['apiKey'] })
    }
    if (value.providerType === 'OPENAI_COMPATIBLE' && !isValidSpeechBaseUrl(value.baseUrl ?? '')) {
      context.addIssue({ code: 'custom', message: '请输入有效的 HTTP 或 HTTPS 接口地址', path: ['baseUrl'] })
    }
    if (value.providerType === 'DASHSCOPE' && !isValidDashScopeWorkspaceId(value.workspaceId ?? '')) {
      context.addIssue({ code: 'custom', message: '请输入有效的阿里云 Workspace ID', path: ['workspaceId'] })
    }
    if (value.speechSilenceThreshold >= value.speechStartThreshold) {
      context.addIssue({
        code: 'custom',
        message: '静音阈值必须小于开始说话阈值',
        path: ['speechSilenceThreshold'],
      })
    }
  })
}

export function createWakeWordModelSchema() {
  return z.object({
    deviceId: z.string().uuid('请选择目标机器人'),
    modelName: z.string().trim().min(1, '请选择乐鑫内置唤醒词').max(32, '唤醒模型名称无效'),
  })
}
