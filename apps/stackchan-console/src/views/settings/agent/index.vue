<script setup lang="ts">
import type {
  AgentCapability,
  AgentCapabilityType,
  AgentSkill,
  AgentToolInvocation,
  McpConnection,
  McpConnectionInput,
  McpTool,
} from '@/api/modules/agent'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import {
  createMcpConnection,
  deleteAgentSkill,
  deleteMcpConnection,
  getAgentCapabilities,
  importAgentSkill,
  listAgentToolInvocations,
  updateAgentCapability,
  updateAgentRuntime,
  updateAgentSkill,
  updateMcpConnection,
} from '@/api/modules/agent'

defineOptions({ name: 'AgentCapabilities' })

const capabilities = ref<Awaited<ReturnType<typeof getAgentCapabilities>> | null>(null)
const invocations = ref<AgentToolInvocation[]>([])
const loading = ref(false)
const refreshingMcp = ref(false)
const updatingKey = ref<string | null>(null)
const stagedSkill = ref<{ file?: File, name: string, size: number }[]>([])
const importingSkill = ref(false)
const editingMcpId = ref<string | null>(null)
const mcpForm = ref<McpConnectionInput>({
  connectionName: '',
  url: '',
  endpoint: '/mcp',
  authType: 'NONE',
  bearerToken: '',
})
const mcpSchema = toTypedSchema(z.object({
  connectionName: z.string().trim().min(1, '请输入连接名称'),
  url: z.string().trim().url('请输入完整服务地址').refine(value => /^https?:\/\//i.test(value), '仅支持 HTTP 或 HTTPS'),
  endpoint: z.string().trim().startsWith('/', '路径应以 / 开头'),
  authType: z.enum(['NONE', 'BEARER']),
  bearerToken: z.string().optional(),
}))
const mcpAuthOptions = [
  { label: '无认证', value: 'NONE' },
  { label: 'Bearer Token', value: 'BEARER' },
]

const capabilityColumns = [
  { accessorKey: 'id', header: '能力标识' },
  { accessorKey: 'description', header: '用途' },
  { id: 'enabled', header: '授权状态' },
]
const skillColumns = [
  { accessorKey: 'name', header: 'Skill' },
  { accessorKey: 'description', header: '用途' },
  { accessorKey: 'packageLabel', header: '包内容' },
  { id: 'enabled', header: '启用' },
  { id: 'actions', header: '操作' },
]
const connectionColumns = [
  { accessorKey: 'connectionName', header: '连接' },
  { accessorKey: 'serverLabel', header: '服务端' },
  { accessorKey: 'healthLabel', header: '发现状态' },
  { accessorKey: 'authLabel', header: '认证' },
  { accessorKey: 'discoveredToolCount', header: 'Tool 数量' },
  { id: 'enabled', header: '连接总开关' },
  { id: 'actions', header: '操作' },
]
const mcpToolColumns = [
  { accessorKey: 'originalName', header: 'Tool' },
  { accessorKey: 'connectionName', header: '连接' },
  { accessorKey: 'descriptionLabel', header: '用途' },
  { accessorKey: 'schemaLabel', header: 'Schema 指纹' },
  { id: 'enabled', header: '逐工具授权' },
]
const invocationColumns = [
  { accessorKey: 'createdAtLabel', header: '时间' },
  { accessorKey: 'channelLabel', header: '入口' },
  { accessorKey: 'sourceLabel', header: '来源' },
  { accessorKey: 'toolName', header: 'Tool' },
  { accessorKey: 'outcomeLabel', header: '结果' },
  { accessorKey: 'metricsLabel', header: '耗时 / 返回量' },
]

const builtInRows = computed(() => capabilities.value?.builtInTools ?? [])
const skillRows = computed(() => (capabilities.value?.skills ?? []).map(skill => ({
  ...skill,
  packageLabel: `${skill.fileCount} 个文件 / ${formatBytes(skill.uncompressedBytes)}${skill.version ? ` / v${skill.version}` : ''}`,
})))
const connectionRows = computed(() => (capabilities.value?.mcp.connections ?? []).map(connection => ({
  ...connection,
  serverLabel: `${connection.serverName} · ${connection.serverVersion}`,
  healthLabel: connection.healthy ? '正常' : `失败 · ${connection.failureCode ?? 'discovery_failed'}`,
  authLabel: connection.authType === 'BEARER' && connection.authenticationConfigured ? 'Bearer · 已加密保存' : '无认证',
})))
const mcpToolRows = computed(() => (capabilities.value?.mcp.tools ?? []).map(tool => ({
  ...tool,
  descriptionLabel: tool.description || '未提供说明',
  schemaLabel: shortHash(tool.schemaSha256),
})))
const outcomeLabels: Record<AgentToolInvocation['outcome'], string> = {
  SUCCESS: '成功',
  TOOL_FAILED: '安全失败',
  RESULT_TRUNCATED: '成功，结果已截断',
  RESULT_BUDGET_EXCEEDED: '结果预算已用尽',
}
const invocationRows = computed(() => invocations.value.map(invocation => ({
  ...invocation,
  createdAtLabel: new Date(invocation.createdAt).toLocaleString('zh-CN'),
  channelLabel: invocation.channel === 'VOICE' ? '设备语音' : '网页文字',
  sourceLabel: sourceLabel(invocation),
  outcomeLabel: outcomeLabels[invocation.outcome],
  metricsLabel: `${invocation.durationMs} ms / ${formatBytes(invocation.resultBytes)}${invocation.truncated ? ' · 已截断' : ''}`,
})))

async function load(refreshMcp = false) {
  if (refreshMcp) {
    refreshingMcp.value = true
  }
  else {
    loading.value = true
  }
  try {
    const [nextCapabilities, nextInvocations] = await Promise.all([
      getAgentCapabilities(refreshMcp),
      listAgentToolInvocations(),
    ])
    capabilities.value = nextCapabilities
    invocations.value = nextInvocations
  }
  catch (error) {
    useFaToast().error('加载失败', {
      description: error instanceof Error ? error.message : '无法读取 Agent 能力状态。',
    })
  }
  finally {
    loading.value = false
    refreshingMcp.value = false
  }
}

async function setRuntime(enabled: boolean) {
  await mutate('runtime', async () => {
    await updateAgentRuntime(enabled)
  }, enabled ? 'Agent 已启用' : 'Agent 已紧急停用')
}

function onRuntimeToggle(enabled: boolean | undefined) {
  if (enabled !== undefined) {
    void setRuntime(enabled)
  }
}

async function setCapability(type: AgentCapabilityType, capabilityId: string, enabled: boolean) {
  await mutate(capabilityKey(type, capabilityId), async () => {
    await updateAgentCapability(type, capabilityId, enabled)
  }, enabled ? '能力已授权' : '能力已停用')
}

function onCapabilityToggle(
  type: AgentCapabilityType,
  capabilityId: string,
  enabled: boolean | undefined,
) {
  if (enabled !== undefined) {
    void setCapability(type, capabilityId, enabled)
  }
}

async function stageSkill({ file, onProgress }: { file: File, onProgress: (percent: number) => void }) {
  if (!file.name.toLowerCase().endsWith('.zip') || file.size > 4 * 1024 * 1024) {
    throw new Error('请选择不超过 4 MiB 的 ZIP 压缩包。')
  }
  onProgress(100)
  return { url: '' }
}

async function submitSkill() {
  const archive = stagedSkill.value[0]?.file
  if (!archive || importingSkill.value) {
    return
  }
  importingSkill.value = true
  try {
    await importAgentSkill(archive)
    stagedSkill.value = []
    useFaToast().success('Skill 已导入', { description: '导入后默认停用，请检查文件清单后手动启用。' })
    await load()
  }
  catch (error) {
    useFaToast().error('导入失败', { description: error instanceof Error ? error.message : '无法导入 Skill 压缩包。' })
  }
  finally {
    importingSkill.value = false
  }
}

async function setSkill(skill: AgentSkill, enabled: boolean) {
  await mutate(`skill:${skill.id}`, async () => {
    await updateAgentSkill(skill.id, enabled)
  }, enabled ? 'Skill 已启用' : 'Skill 已停用')
}

function removeSkill(skill: AgentSkill) {
  useFaModal().confirm({
    title: `删除 ${skill.name}`,
    content: '将永久删除该 Skill 的 SKILL.md 和包内全部附属文件。',
    confirmButtonText: '确认删除',
    async onConfirm() {
      await mutate(`skill:${skill.id}`, async () => {
        await deleteAgentSkill(skill.id)
      }, 'Skill 已删除')
    },
  })
}

function resetMcpForm() {
  editingMcpId.value = null
  mcpForm.value = {
    connectionName: '',
    url: '',
    endpoint: '/mcp',
    authType: 'NONE',
    bearerToken: '',
  }
}

function editMcpConnection(connection: McpConnection) {
  if (!connection.id || !connection.managed) {
    return
  }
  editingMcpId.value = connection.id
  mcpForm.value = {
    connectionName: connection.connectionName,
    url: connection.url ?? '',
    endpoint: connection.endpoint ?? '/mcp',
    authType: connection.authType,
    bearerToken: '',
  }
}

async function submitMcpConnection() {
  const input = {
    ...mcpForm.value,
    connectionName: mcpForm.value.connectionName.trim().toLowerCase(),
    url: mcpForm.value.url.trim(),
    endpoint: mcpForm.value.endpoint.trim() || '/mcp',
  }
  if (!input.connectionName || !input.url) {
    useFaToast().error('请填写连接名称和 URL')
    return
  }
  const key = editingMcpId.value ? `mcp-edit:${editingMcpId.value}` : 'mcp-create'
  await mutate(key, async () => {
    if (editingMcpId.value) {
      await updateMcpConnection(editingMcpId.value, input)
    }
    else {
      await createMcpConnection(input)
    }
    resetMcpForm()
  }, editingMcpId.value ? 'MCP 连接已更新，授权已重置' : 'MCP 连接已添加，默认停用')
}

function removeMcpConnection(connection: McpConnection) {
  if (!connection.id || !connection.managed) {
    return
  }
  useFaModal().confirm({
    title: `删除 ${connection.connectionName}`,
    content: '将删除连接配置和加密认证信息；已发现 Tool 将立即不可用。',
    confirmButtonText: '确认删除',
    async onConfirm() {
      await mutate(`mcp-delete:${connection.id}`, async () => {
        await deleteMcpConnection(connection.id!)
        if (editingMcpId.value === connection.id) {
          resetMcpForm()
        }
      }, 'MCP 连接已删除')
    },
  })
}

async function mutate(key: string, action: () => Promise<void>, successMessage: string) {
  if (updatingKey.value) {
    return
  }
  updatingKey.value = key
  try {
    await action()
    useFaToast().success(successMessage)
    await load()
  }
  catch (error) {
    useFaToast().error('更新失败', {
      description: error instanceof Error ? error.message : '无法更新 Agent 能力。',
    })
  }
  finally {
    updatingKey.value = null
  }
}

function capabilityKey(type: AgentCapabilityType, id: string) {
  return `${type}:${id}`
}

function shortHash(value: string) {
  return value.length > 12 ? `${value.slice(0, 12)}…` : value
}

function sourceLabel(invocation: AgentToolInvocation) {
  if (invocation.source === 'MCP') {
    return `MCP · ${invocation.sourceId ?? '未知连接'}`
  }
  if (invocation.source === 'SKILL') {
    return `Skill · ${invocation.skillId ?? 'read_skill'}`
  }
  return '内置 Tool'
}

function formatBytes(bytes: number) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KiB`
}

function capabilityEnabled(row: AgentCapability) {
  return row.enabled
}

function connectionEnabled(row: McpConnection) {
  return row.enabled
}

function mcpToolEnabled(row: McpTool) {
  return row.enabled
}

const activeSection = ref<'audit' | 'mcp' | 'overview' | 'tools'>('tools')
const sectionTabs = [
  { label: '能帮你做什么', value: 'tools', icon: 'i-ri:tools-line' },
  { label: '运行策略（高级）', value: 'overview', icon: 'i-ri:dashboard-line' },
  { label: '连接外部能力（高级）', value: 'mcp', icon: 'i-ri:plug-line' },
  { label: '调用审计', value: 'audit', icon: 'i-ri:file-list-3-line' },
]

onMounted(() => load())
</script>

<template>
  <AppPageShell title="可用帮助与扩展" description="" width="normal">
    <AppLoading :loading="loading">
      <FaTabs v-model="activeSection" :list="sectionTabs" list-class="justify-start overflow-x-auto" content-class="pt-5">
        <template #overview>
          <div class="space-y-4">
            <FaAlert
              title="受控执行边界"
              description="当前仅开放只读能力。审计只保存 Tool 名、来源、结果类别、耗时和返回量，不保存参数、结果正文、对话正文、端点或认证信息。"
            />

            <div class="gap-4 grid xl:grid-cols-3">
              <FaCard title="运行总开关" description="管理员紧急开关关闭后，新请求立即回退普通聊天。">
                <div v-if="capabilities" data-agent-runtime class="flex gap-4 items-center justify-between">
                  <div>
                    <p class="font-medium">
                      {{ capabilities.runtime.enabled ? 'Agent 正在生效' : 'Agent 当前关闭' }}
                    </p>
                    <p class="text-sm text-muted-foreground mt-1">
                      部署开关：{{ capabilities.runtime.deploymentEnabled ? '允许' : '已禁用' }}
                    </p>
                  </div>
                  <FaSwitch
                    :model-value="capabilities.runtime.adminEnabled"
                    :disabled="!capabilities.runtime.deploymentEnabled || updatingKey !== null"
                    :before-change="() => !updatingKey"
                    @update:model-value="onRuntimeToggle"
                  />
                </div>
              </FaCard>

              <FaCard title="执行预算" description="达到限制时停止继续调用，禁止猜测结果。">
                <dl v-if="capabilities" class="text-sm gap-3 grid grid-cols-2">
                  <div>
                    <dt class="text-muted-foreground">
                      最多调用
                    </dt><dd class="font-medium mt-1">
                      {{ capabilities.limits.maxToolCalls }} 次
                    </dd>
                  </div>
                  <div>
                    <dt class="text-muted-foreground">
                      总超时
                    </dt><dd class="font-medium mt-1">
                      {{ capabilities.limits.timeoutSeconds }} 秒
                    </dd>
                  </div>
                  <div>
                    <dt class="text-muted-foreground">
                      单次结果
                    </dt><dd class="font-medium mt-1">
                      {{ formatBytes(capabilities.limits.maxToolResultBytes) }}
                    </dd>
                  </div>
                  <div>
                    <dt class="text-muted-foreground">
                      回合结果
                    </dt><dd class="font-medium mt-1">
                      {{ formatBytes(capabilities.limits.maxTotalToolResultBytes) }}
                    </dd>
                  </div>
                </dl>
              </FaCard>

              <FaCard title="运行框架" description="LLM 供应商继续复用现有 AI 配置。">
                <div v-if="capabilities" class="text-sm">
                  <p class="font-medium">
                    Spring AI Alibaba ReactAgent
                  </p>
                  <p class="text-muted-foreground mt-1">
                    {{ capabilities.framework }} · {{ capabilities.frameworkVersion }}
                  </p>
                </div>
              </FaCard>
            </div>
          </div>
        </template>

        <template #tools>
          <div class="space-y-4">
            <FaCard title="日常事实查询" description="查看各项用途并按需授权。关闭后机器人不会调用该能力，不能用猜测替代查询结果。">
              <FaTable :columns="capabilityColumns" :data="builtInRows" empty-text="暂无内置 Tool" row-key="id" border>
                <template #cell-enabled="{ row }">
                  <FaSwitch
                    :model-value="capabilityEnabled(row.original)"
                    :disabled="updatingKey !== null"
                    @update:model-value="value => onCapabilityToggle('BUILTIN_TOOL', row.original.id, value)"
                  />
                </template>
              </FaTable>
            </FaCard>

            <FaCollapsible>
              <template #trigger="{ open }">
                <span class="text-sm px-4 py-2 border rounded-md inline-block">{{ open ? '收起' : '展开' }}自定义工作流（高级）</span>
              </template>
              <FaCard title="自定义 Skill 包" description="有重复使用的明确场景时再配置。上传工作流说明不会自动获得外部工具权限。">
                <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end">
                  <FaFileUpload
                    v-model="stagedSkill"
                    :max="1"
                    :http-request="stageSkill"
                    :after-upload="() => ''"
                    accept=".zip,application/zip"
                    description="选择不超过 4 MiB 的 ZIP；包内只能有一个 Skill 根目录"
                    class="flex-1 min-w-0"
                  />
                  <FaButton :disabled="stagedSkill.length === 0" :loading="importingSkill" @click="submitSkill">
                    导入 Skill
                  </FaButton>
                </div>
                <FaAlert
                  title="默认停用，权限不随包导入"
                  description="Skill 只提供文字工作流，不会自动获得 Shell、Python、文件系统或新的 Tool/MCP 权限。"
                  class="mb-4"
                />
                <FaTable :columns="skillColumns" :data="skillRows" empty-text="暂无自定义 Skill" row-key="id" border>
                  <template #cell-packageLabel="{ row }">
                    <div class="max-w-72">
                      <p>{{ row.original.packageLabel }}</p>
                      <p class="text-xs text-muted-foreground mt-1 truncate" :title="row.original.files.join(' · ')">
                        {{ row.original.files.join(' · ') }}
                      </p>
                    </div>
                  </template>
                  <template #cell-enabled="{ row }">
                    <FaSwitch
                      :model-value="row.original.enabled"
                      :disabled="updatingKey !== null"
                      @update:model-value="value => value !== undefined && setSkill(row.original, value)"
                    />
                  </template>
                  <template #cell-actions="{ row }">
                    <FaButton
                      variant="destructive"
                      size="sm"
                      :disabled="row.original.enabled || updatingKey !== null"
                      @click="removeSkill(row.original)"
                    >
                      删除
                    </FaButton>
                  </template>
                </FaTable>
              </FaCard>
            </FaCollapsible>
          </div>
        </template>

        <template #mcp>
          <div class="space-y-4">
            <FaCard>
              <template #header>
                <div class="flex flex-wrap gap-4 items-center justify-between">
                  <div>
                    <h2 class="text-base font-semibold">
                      MCP 连接
                    </h2>
                    <p class="text-sm text-muted-foreground">
                      连接配置跨重启保存；Bearer Token 加密存储且不会再次显示。
                    </p>
                  </div>
                  <FaButton variant="outline" size="sm" :loading="refreshingMcp" @click="load(true)">
                    刷新 MCP 发现
                  </FaButton>
                </div>
              </template>
              <FaForm :model="mcpForm" :validation-schema="mcpSchema" scroll-to-error class="mb-5 pb-5 border-b gap-4 grid md:grid-cols-2" @submit="submitMcpConnection">
                <FaFormItem name="connectionName" label="连接名称" required>
                  <FaInput v-model="mcpForm.connectionName" placeholder="例如 my-coffee" class="w-full" />
                </FaFormItem>
                <FaFormItem name="url" label="服务地址" required>
                  <FaInput v-model="mcpForm.url" placeholder="https://mcp.example.com" class="w-full" />
                </FaFormItem>
                <FaFormItem name="endpoint" label="连接路径" required>
                  <FaInput v-model="mcpForm.endpoint" placeholder="/mcp" class="w-full" />
                </FaFormItem>
                <FaFormItem name="authType" label="认证方式">
                  <FaSelect v-model="mcpForm.authType" :options="mcpAuthOptions" class="w-full" />
                </FaFormItem>
                <FaFormItem v-if="mcpForm.authType === 'BEARER'" name="bearerToken" label="Bearer Token" :description="editingMcpId ? '留空则保留现有 Token。' : '只用于当前连接，加密保存后不再展示。'" class="md:col-span-2">
                  <FaInput v-model="mcpForm.bearerToken" type="password" autocomplete="new-password" class="w-full" />
                </FaFormItem>
                <div class="flex flex-wrap gap-2 md:col-span-2">
                  <FaButton type="submit" :loading="updatingKey === 'mcp-create' || updatingKey?.startsWith('mcp-edit:')">
                    {{ editingMcpId ? '保存连接' : '添加连接' }}
                  </FaButton>
                  <FaButton v-if="editingMcpId" type="button" variant="outline" @click="resetMcpForm">
                    取消
                  </FaButton>
                </div>
              </FaForm>
              <FaTable :columns="connectionColumns" :data="connectionRows" empty-text="暂无已配置的 MCP 连接" row-key="connectionName" border>
                <template #cell-enabled="{ row }">
                  <FaSwitch
                    :model-value="connectionEnabled(row.original)"
                    :disabled="updatingKey !== null"
                    @update:model-value="value => onCapabilityToggle('MCP_SERVER', row.original.connectionName, value)"
                  />
                </template>
                <template #cell-actions="{ row }">
                  <div v-if="row.original.managed" class="flex gap-2">
                    <FaButton variant="outline" size="sm" :disabled="updatingKey !== null" @click="editMcpConnection(row.original)">
                      编辑
                    </FaButton>
                    <FaButton variant="destructive" size="sm" :disabled="updatingKey !== null" @click="removeMcpConnection(row.original)">
                      删除
                    </FaButton>
                  </div>
                  <span v-else class="text-sm text-muted-foreground">部署配置</span>
                </template>
              </FaTable>
            </FaCard>

            <FaCard title="MCP Tool 授权" description="发现不等于授权；连接身份或 Schema 指纹变化后，既有授权自动失效。">
              <FaTable :columns="mcpToolColumns" :data="mcpToolRows" empty-text="尚未发现 MCP Tool" row-key="capabilityId" border>
                <template #cell-enabled="{ row }">
                  <FaSwitch
                    :model-value="mcpToolEnabled(row.original)"
                    :disabled="updatingKey !== null"
                    @update:model-value="value => onCapabilityToggle('MCP_TOOL', row.original.capabilityId, value)"
                  />
                </template>
              </FaTable>
            </FaCard>
          </div>
        </template>

        <template #audit>
          <FaCard title="最近 Tool 调用" description="仅展示隐私安全元数据；最多显示最近 50 条。">
            <FaTable :columns="invocationColumns" :data="invocationRows" empty-text="暂无 Tool 调用记录" row-key="id" border />
          </FaCard>
        </template>
      </FaTabs>
    </AppLoading>
  </AppPageShell>
</template>
