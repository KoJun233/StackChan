<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import type { Delegation, DelegationFeedbackInput, DelegationProjectSummary, DelegationRun } from '@/api/modules/delegations'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { DelegationOperationPendingError, DelegationSubmissionError, getDelegationProjectSummary, listDelegationRuns, newDelegationOperationKey, retryDelegationDelivery, runDelegationRevision, submitDelegationFeedback } from '@/api/modules/delegations'
import { listRoles } from '@/api/modules/roles'
import eventBus from '@/utils/eventBus'
import DetailForm from './components/DetailForm/index.vue'
import ProjectCard from './components/ProjectCard.vue'
import { canRetryDelivery, deliveryStatusLabel, formatDelegationTime, runErrorMessage, runStatusLabel, safeSourceUrl, stepStatusLabel } from './helpers'

defineOptions({ name: 'CompanionDelegationDetail' })
const route = useRoute()
const router = useRouter()
const taskId = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const example = computed(() => !taskId.value && route.query.example === 'ai-daily')
const formRef = useTemplateRef<InstanceType<typeof DetailForm>>('formRef')
const submitting = ref(false)
const savedTask = ref<Delegation | null>(null)
const historyLoading = ref(false)
const historyError = ref('')
const runs = ref<DelegationRun[]>([])
const retrying = ref('')
const project = ref<DelegationProjectSummary | null>(null)
const projectLoading = ref(false)
const projectError = ref('')
const roleNames = ref(new Map<string, string>())
const feedbackBusy = ref(false)
const feedbackError = ref('')
const mutationPending = ref(false)
const reviseOpen = ref(false)
const revisionFormRef = useTemplateRef<FormExpose>('revisionFormRef')
const revisionModel = ref({ comment: '' })
const revisionSchema = toTypedSchema(z.object({ comment: z.string().trim().min(1, '请说一条想调整的偏好').refine(value => Array.from(value).length <= 2000, '修改意见最多 2000 字') }))
const revisionTarget = ref<{ runId: string, expectedRevision: number, resourceVersion: number } | null>(null)
type PendingMutation = { task: Delegation, runId: string, key: string, input: DelegationFeedbackInput } | { task: Delegation, key: string, expectedRevision: number }
let pendingMutation: PendingMutation | null = null
const currentRun = computed(() => project.value?.latestRun)
const currentReview = computed(() => project.value?.review)
const currentArtifact = computed(() => Boolean(project.value?.task.revision && currentRun.value?.taskRevision === project.value.task.revision && currentReview.value && !project.value.task.cancelledAt))
const canAccept = computed(() => currentArtifact.value && currentRun.value?.status === 'SUCCEEDED' && currentReview.value?.reviewState === 'UNREVIEWED')
const canRevise = computed(() => currentArtifact.value && currentRun.value?.status !== 'RUNNING' && currentReview.value?.reviewState !== 'SUPERSEDED')
const canContinue = computed(() => Boolean(project.value?.task.revision && project.value.task.enabled && currentReview.value?.reviewState === 'REVISION_REQUESTED' && currentRun.value?.status !== 'RUNNING' && !project.value.task.cancelledAt))
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
let requestId = 0
let projectRequestId = 0
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let active = true

function stopRefresh() {
  clearTimeout(refreshTimer)
  refreshTimer = undefined
}
async function loadHistory(background = false) {
  stopRefresh()
  if (!taskId.value || !active) {
    return
  }
  const id = taskId.value
  const request = ++requestId
  void loadProject(background)
  historyLoading.value = !background
  historyError.value = ''
  try {
    const result = await listDelegationRuns(id, getParams())
    if (request !== requestId || id !== taskId.value || !active) {
      return
    }
    runs.value = result.list
    pagination.value.total = result.total
    if (runs.value.some(run => run.status === 'RUNNING' || run.deliveryStatus === 'PENDING' || run.deliveryStatus === 'SENDING')) {
      refreshTimer = setTimeout(loadHistory, 5000, true)
    }
  }
  catch (error) {
    if (request === requestId) {
      historyError.value = error instanceof Error ? error.message : '无法读取历史成果。'
    }
  }
  finally {
    if (request === requestId) {
      historyLoading.value = false
    }
  }
}
async function loadProject(background = false) {
  if (!taskId.value || !active) {
    return
  }
  const id = taskId.value
  const request = ++projectRequestId
  projectLoading.value = !background
  projectError.value = ''
  try {
    const result = await getDelegationProjectSummary(id)
    if (request !== projectRequestId || id !== taskId.value || !active) {
      return
    }
    project.value = result
  }
  catch (error) {
    if (request === projectRequestId && id === taskId.value && active) {
      projectError.value = error instanceof Error ? error.message : '无法读取成果摘要。'
    }
  }
  finally {
    if (request === projectRequestId) {
      projectLoading.value = false
    }
  }
}
function feedbackIntent(input: DelegationFeedbackInput, runId: string): PendingMutation | null {
  return project.value ? { task: project.value.task, runId, input, key: newDelegationOperationKey() } : null
}
async function performMutation(intent: PendingMutation | null) {
  if (!intent || feedbackBusy.value) {
    return
  }
  const id = taskId.value
  if (intent.task.id !== id) {
    return
  }
  feedbackBusy.value = true
  feedbackError.value = ''
  pendingMutation = intent
  try {
    if ('input' in intent) {
      await submitDelegationFeedback(id, intent.runId, intent.input, intent.key, intent.task)
    }
    else {
      await runDelegationRevision(id, intent.expectedRevision, intent.key, intent.task)
    }
    if (id !== taskId.value) {
      return
    }
    mutationPending.value = false
    pendingMutation = null
    reviseOpen.value = false
    if (!active) {
      return
    }
    useFaToast().success('input' in intent && intent.input.kind === 'ACCEPT_RESULT' ? '这份成果已认可' : 'input' in intent && intent.input.kind === 'REVISE_PREFERENCES' && intent.input.runMode === 'SAVE_ONLY' ? '偏好已保存，等待你决定何时重新比较' : 'input' in intent && intent.input.kind === 'CANCEL' ? '任务已取消，已有成果仍可查看' : '操作已保存，请查看当前进展')
    await loadHistory()
    eventBus.emit('get-delegation-list')
  }
  catch (error) {
    if (id !== taskId.value) {
      return
    }
    mutationPending.value = error instanceof DelegationOperationPendingError
    if (!mutationPending.value) {
      pendingMutation = null
    }
    feedbackError.value = error instanceof DelegationSubmissionError && error.code === 'REVISION_CONFLICT'
      ? '成果已更新，请先查看最新版本，再决定是否提交这条反馈。'
      : error instanceof DelegationSubmissionError && error.status === 429
        ? '当前有任务在执行，稍后再试。已保存的任务与成果仍然保留。'
        : error instanceof Error ? error.message : '反馈尚未保存，请稍后重试。'
    if (error instanceof DelegationSubmissionError && error.code === 'REVISION_CONFLICT') {
      reviseOpen.value = false
      await loadProject()
    }
  }
  finally {
    if (id === taskId.value) {
      feedbackBusy.value = false
    }
  }
}
function sendFeedback(kind: 'ACCEPT_RESULT' | 'PAUSE' | 'CANCEL') {
  const run = currentRun.value
  const task = project.value?.task
  const review = currentReview.value
  if (!run || !task?.revision || !review || mutationPending.value || task.cancelledAt) {
    return
  }
  const intent = feedbackIntent({ kind, comment: '', expectedRevision: task.revision, resourceVersion: review.resourceVersion }, run.id)
  if (kind === 'ACCEPT_RESULT') {
    void performMutation(intent)
    return
  }
  useFaModal().confirm({
    title: kind === 'CANCEL' ? '取消这项任务？' : '暂停这项任务？',
    content: kind === 'CANCEL' ? '取消后不再执行，已有成果仍可查看。需要继续时请明确新建任务。' : '暂停后不再启动新的工作，已有成果仍可查看。',
    confirmButtonText: kind === 'CANCEL' ? '取消任务' : '暂停任务',
    onConfirm: () => performMutation(intent),
  })
}
function openRevision() {
  if (!canRevise.value || !currentRun.value || !project.value?.task.revision || !currentReview.value || mutationPending.value) {
    return
  }
  revisionTarget.value = { runId: currentRun.value.id, expectedRevision: project.value.task.revision, resourceVersion: currentReview.value.resourceVersion }
  revisionModel.value = { comment: '' }
  feedbackError.value = ''
  reviseOpen.value = true
}
async function saveRevision(runMode: 'SAVE_ONLY' | 'SAVE_AND_RUN') {
  if (mutationPending.value || !revisionTarget.value || !(await revisionFormRef.value?.validate())?.valid) {
    return
  }
  const { runId, ...versions } = revisionTarget.value
  await performMutation(feedbackIntent({ kind: 'REVISE_PREFERENCES', comment: revisionModel.value.comment.trim(), runMode, ...versions }, runId))
}
function continueRevision() {
  const task = project.value?.task
  if (task?.revision && canContinue.value && !mutationPending.value) {
    void performMutation({ task, expectedRevision: task.revision, key: newDelegationOperationKey() })
  }
}
function currentChange(page = 1) {
  return onCurrentChange(page).then(() => loadHistory())
}
function sizeChange(size: number) {
  return onSizeChange(size).then(() => loadHistory())
}
async function onSubmit() {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    if (await formRef.value?.submit()) {
      eventBus.emit('get-delegation-list')
      if (!taskId.value && savedTask.value) {
        await router.replace({ name: 'companionDelegationDetail', params: { id: savedTask.value.id } })
      }
      else {
        loadHistory()
      }
    }
  }
  finally {
    submitting.value = false
  }
}
function onCancel() {
  router.back({ name: 'companionDelegationList' })
}
function retryDelivery(run: DelegationRun) {
  if (!canRetryDelivery(run) || retrying.value) {
    return
  }
  useFaModal().confirm({
    title: '重新投递已保存成果',
    content: `将「${run.taskTitle}」的已有结果重新发送到 Bark，不重新执行任务。`,
    confirmButtonText: '重新投递',
    onConfirm: async () => {
      retrying.value = run.id
      try {
        await retryDelegationDelivery(run.id)
        await loadHistory()
        eventBus.emit('get-delegation-list')
        useFaToast().success('已请求重新投递')
      }
      catch (error) {
        useFaToast().error('重新投递失败', { description: error instanceof Error ? error.message : '无法重试投递。' })
      }
      finally {
        retrying.value = ''
      }
    },
  })
}
watch(taskId, () => {
  ++requestId
  ++projectRequestId
  project.value = null
  projectError.value = ''
  feedbackError.value = ''
  pendingMutation = null
  mutationPending.value = false
  feedbackBusy.value = false
  reviseOpen.value = false
  runs.value = []
  historyError.value = ''
  pagination.value.total = 0
  currentChange()
}, { immediate: true })
onActivated(() => {
  active = true
  loadHistory()
})
onDeactivated(() => {
  active = false
  ++requestId
  ++projectRequestId
  stopRefresh()
})
onBeforeUnmount(() => {
  active = false
  ++requestId
  ++projectRequestId
  stopRefresh()
})
onMounted(() => {
  listRoles().then(roles => roleNames.value = new Map(roles.map(role => [role.id, role.name]))).catch(() => {})
})
</script>

<template>
  <div class="pb-20">
    <FaFixedBar position="top" class="p-0">
      <FaPageHeader :title="taskId ? '任务与执行记录' : '交代一个任务'" class="mb-0 border-b-none">
        <FaButton variant="outline" size="sm" class="rounded-full" @click="onCancel">
          <FaIcon name="i-ep:arrow-left" />返回
        </FaButton>
      </FaPageHeader>
    </FaFixedBar>
    <FaPageMain>
      <div class="mx-auto max-w-5xl w-full space-y-8">
        <section v-if="taskId" aria-label="当前成果" aria-live="polite" class="min-w-0 space-y-4">
          <FaAlert v-if="projectError" :title="project ? '当前连接中断，保留上次成果' : '成果摘要未加载'" :description="projectError" variant="destructive">
            <template #action>
              <FaButton variant="outline" @click="loadProject()">
                重试读取
              </FaButton>
            </template>
          </FaAlert>
          <ProjectCard v-if="project && currentRun" :task="project.task" :run="currentRun" :project="project" :partner="roleNames.get(project.task.roleId)" />
          <FaAlert v-else-if="!projectError" :title="projectLoading ? '正在读取成果…' : '还没有成果，接下任务后会出现在这里'" description="后台处理期间可以继续聊天，或稍后查看进展。" />
          <FaAlert v-if="project?.task.cancelledAt" title="任务已取消" :description="project.task.cancellationReason || '不再执行，已有成果仍可查看。'" />
          <FaAlert v-else-if="project && !project.task.enabled" title="任务已暂停" description="不会继续执行，已有成果仍保留。" />
          <div v-if="currentRun && currentReview && !project?.task.cancelledAt" class="flex flex-wrap gap-3 items-center">
            <FaButton :disabled="!canAccept || feedbackBusy || mutationPending" @click="sendFeedback('ACCEPT_RESULT')">
              这份结果可以了
            </FaButton>
            <FaButton variant="outline" :disabled="!canRevise || feedbackBusy || mutationPending" @click="openRevision">
              修改偏好
            </FaButton>
            <FaButton v-if="canContinue" variant="outline" :disabled="feedbackBusy || mutationPending" @click="continueRevision">
              继续比较
            </FaButton>
            <FaDropdown :items="[[{ label: '暂停任务', disabled: feedbackBusy || mutationPending || !project?.task.enabled, handle: () => sendFeedback('PAUSE') }, { label: '取消任务', variant: 'destructive', disabled: feedbackBusy || mutationPending, handle: () => sendFeedback('CANCEL') }]]">
              <FaButton variant="outline" :disabled="feedbackBusy || mutationPending" aria-label="更多任务操作">
                更多操作
              </FaButton>
            </FaDropdown>
          </div>
          <p v-if="currentReview?.reviewState === 'ACCEPTED'" class="text-sm text-muted-foreground">
            你认可了这份成果；购买或其他实际操作是否完成，需要另外确认。
          </p>
          <FaAlert v-if="feedbackError" :title="mutationPending ? '正在核对操作状态' : '反馈未保存'" :description="feedbackError" variant="destructive">
            <template v-if="mutationPending" #action>
              <FaButton variant="outline" :disabled="feedbackBusy" @click="performMutation(pendingMutation)">
                核对保存状态
              </FaButton>
            </template>
          </FaAlert>
          <FaCollapsible v-if="project?.feedbackHistory.length">
            <template #trigger="{ open }">
              <span class="text-sm px-3 py-2 border rounded-md inline-block">{{ open ? '收起' : '查看' }}反馈记录</span>
            </template>
            <ol class="text-sm pt-3 space-y-3">
              <li v-for="feedback in project.feedbackHistory" :key="feedback.id" class="leading-6 whitespace-pre-wrap break-words">
                {{ formatDelegationTime(feedback.createdAt) }} · {{ { ACCEPT_RESULT: '认可成果', REVISE_PREFERENCES: '修改偏好', PAUSE: '暂停任务', CANCEL: '取消任务' }[feedback.kind] }}{{ feedback.comment ? `：${feedback.comment}` : '' }}
              </li>
            </ol>
          </FaCollapsible>
        </section>
        <FaCollapsible v-if="taskId">
          <template #trigger="{ open }">
            <span class="text-sm px-3 py-2 border rounded-md inline-block">{{ open ? '收起' : '查看' }}任务安排与设置</span>
          </template>
          <DetailForm :id="taskId" ref="formRef" class="pt-4" @saved="savedTask = $event" />
        </FaCollapsible>
        <DetailForm v-else ref="formRef" :example="example" @saved="savedTask = $event" />
        <section v-if="taskId" aria-label="执行记录" class="space-y-4">
          <div class="flex gap-3 items-center justify-between">
            <h2 class="text-lg font-semibold">
              执行记录
            </h2>
            <FaButton variant="outline" :disabled="historyLoading" @click="loadHistory()">
              刷新记录
            </FaButton>
          </div>
          <FaAlert v-if="historyError" variant="destructive" title="执行记录未加载" :description="historyError">
            <template #action>
              <FaButton variant="outline" @click="loadHistory()">
                重试
              </FaButton>
            </template>
          </FaAlert>
          <FaAlert v-else-if="!runs.length" :title="historyLoading ? '正在读取执行记录…' : '尚无执行记录'" description="启用后按安排执行，也可以在列表中选择「立即执行一次」。" />
          <div v-loading="historyLoading" class="space-y-4">
            <FaCard v-for="run in runs" :key="run.id" :title="`${formatDelegationTime(run.scheduledAt || run.createdAt)} · ${run.taskTitle}`">
              <div class="mb-4 flex flex-wrap gap-2 items-center">
                <FaTag :variant="run.status === 'FAILED' || run.status === 'BLOCKED' ? 'destructive' : 'secondary'">
                  {{ runStatusLabel(run.status, Boolean(run.steps?.length)) }}
                </FaTag>
                <FaTag :variant="run.deliveryStatus === 'FAILED' || run.deliveryStatus === 'UNKNOWN' ? 'destructive' : 'outline'">
                  {{ deliveryStatusLabel(run.deliveryStatus) }}
                </FaTag>
                <span class="text-xs text-muted-foreground">{{ formatDelegationTime(run.completedAt || run.createdAt) }} · 投递尝试 {{ run.deliveryAttempts }} 次</span>
                <FaButton v-if="canRetryDelivery(run)" variant="outline" size="sm" :disabled="Boolean(retrying)" @click="retryDelivery(run)">
                  重新投递
                </FaButton>
              </div>
              <FaAlert v-if="run.deliveryStatus === 'UNKNOWN'" title="投递结果不明" description="请求可能已被渠道接收；请先检查 Bark 历史。这条成果不会自动重复发送。" class="mb-4" />
              <FaAlert v-if="run.errorCode" title="本次未完成" :description="runErrorMessage(run.errorCode)" :variant="run.status === 'FAILED' || run.status === 'BLOCKED' || run.deliveryStatus === 'FAILED' ? 'destructive' : 'default'" class="mb-4" />
              <FaAlert v-if="run.status === 'BLOCKED'" title="伙伴需要你的帮助" description="查看下面的执行说明，补充缺少的信息或接入所需能力后，再继续任务。" class="mb-4" />
              <FaAlert v-if="run.status === 'NO_UPDATES'" title="本次没有新内容" description="伙伴已检查本次任务，没有需要交付的新结果。" class="mb-4" />
              <FaTag v-if="run.review" variant="outline" class="mb-3">
                结果版本 {{ run.taskRevision ?? '未知' }} · {{ run.review.reviewState === 'SUPERSEDED' || (project?.task.revision && run.taskRevision !== project.task.revision) ? '来自旧偏好' : run.review.reviewState === 'ACCEPTED' ? '这份结果已认可' : run.review.reviewState === 'REVISION_REQUESTED' ? '已请求修改' : '等你查看' }}
              </FaTag>
              <p v-if="run.output" class="text-sm leading-7 whitespace-pre-wrap break-words">
                {{ run.output }}
              </p>
              <FaCollapsible v-if="run.steps?.length" class="mt-4">
                <template #trigger="{ open }">
                  <span class="text-sm px-3 py-2 border rounded-md inline-block">{{ open ? '收起' : '查看' }}执行过程</span>
                </template>
                <ol class="text-sm pt-4 space-y-3">
                  <li v-for="(step, index) in run.steps" :key="`${run.id}-step-${index}`" class="space-y-1">
                    <div class="font-medium flex flex-wrap gap-2 items-center">
                      <span>{{ step.toolName }}</span><FaTag variant="outline">
                        {{ stepStatusLabel(step.status) }}
                      </FaTag>
                    </div>
                    <p class="leading-6 whitespace-pre-wrap break-words">
                      {{ step.summary }}
                    </p>
                  </li>
                </ol>
              </FaCollapsible>
              <div v-if="run.evidence?.length" class="text-sm mt-4 pt-4 border-t space-y-3">
                <div v-for="(evidence, index) in run.evidence" :key="`${run.id}-evidence-${index}`">
                  <a v-if="safeSourceUrl(evidence.url)" :href="safeSourceUrl(evidence.url)" target="_blank" rel="noopener noreferrer" class="text-primary underline break-words">{{ evidence.title }}</a>
                  <p v-else class="font-medium">
                    {{ evidence.title }}
                  </p>
                  <p v-if="evidence.detail" class="text-muted-foreground leading-6 whitespace-pre-wrap break-words">
                    {{ evidence.detail }}
                  </p>
                  <p v-if="evidence.readAt" class="text-xs text-muted-foreground mt-1">
                    读取时间：{{ formatDelegationTime(evidence.readAt, project?.task.zoneId) }}
                  </p>
                  <p v-if="evidence.inputUrl && evidence.inputUrl !== evidence.url" class="text-xs text-muted-foreground mt-1 break-words">
                    原始资料：<a v-if="safeSourceUrl(evidence.inputUrl)" :href="safeSourceUrl(evidence.inputUrl)" target="_blank" rel="noopener noreferrer" class="text-primary underline">{{ evidence.inputUrl }}</a><span v-else>链接不可用</span>
                  </p>
                </div>
              </div>
              <div v-if="run.sourceWarnings?.length" class="text-sm text-muted-foreground mb-4 space-y-1">
                <p v-for="(warning, index) in run.sourceWarnings" :key="index">
                  {{ warning }}
                </p>
              </div>
              <div v-if="!run.output && run.items?.length" class="divide-y">
                <article v-for="(item, index) in run.items" :key="`${run.id}-${index}`" class="py-4 space-y-3 first:pt-0 last:pb-0">
                  <h3 class="text-base font-semibold break-words">
                    {{ index + 1 }}. {{ item.title }}
                  </h3>
                  <p class="text-sm leading-7 whitespace-pre-wrap break-words">
                    {{ item.summary }}
                  </p>
                  <p class="text-sm leading-7 whitespace-pre-wrap break-words">
                    <span class="font-medium">影响：</span>{{ item.impact }}
                  </p>
                  <div class="text-xs text-muted-foreground flex flex-wrap gap-2">
                    <a v-if="safeSourceUrl(item.url)" :href="safeSourceUrl(item.url)" target="_blank" rel="noopener noreferrer" class="text-primary underline break-words">{{ item.sourceName }} · 阅读原文</a>
                    <span v-else>{{ item.sourceName }} · 来源链接不可用</span>
                    <span v-if="item.publishedAt">{{ formatDelegationTime(item.publishedAt) }}</span>
                  </div>
                </article>
              </div>
            </FaCard>
          </div>
          <FaPagination :page="pagination.page" :size="pagination.size" :total="pagination.total" @page-change="currentChange" @size-change="sizeChange" />
        </section>
      </div>
    </FaPageMain>
    <FaModal v-model="reviseOpen" title="修改这项任务的偏好" open-auto-focus :closable="!feedbackBusy && !mutationPending" :close-on-press-escape="!feedbackBusy && !mutationPending" :close-on-click-overlay="false" :show-confirm-button="false">
      <p class="text-sm text-muted-foreground leading-6 mb-4">
        正在修改结果版本 {{ revisionTarget?.expectedRevision }}。只改变这项任务，不自动记成长期偏好；旧成果和来源会保留。
      </p>
      <FaForm ref="revisionFormRef" :model="revisionModel" :validation-schema="revisionSchema" :disabled="feedbackBusy || mutationPending" label-placement="top" scroll-to-error>
        <FaFormItem name="comment" label="希望怎样调整？" required>
          <FaTextarea rows="5" align="block" placeholder="例如：更在意线缆少，缺少的预算信息先不用猜。" />
        </FaFormItem>
      </FaForm>
      <FaAlert v-if="feedbackError" :title="mutationPending ? '正在核对操作状态' : '反馈未保存'" :description="feedbackError" variant="destructive" class="mt-4" />
      <template #footer>
        <FaButton v-if="mutationPending" :disabled="feedbackBusy" @click="performMutation(pendingMutation)">
          核对保存状态
        </FaButton>
        <template v-else>
          <FaButton variant="ghost" :disabled="feedbackBusy" @click="reviseOpen = false">
            返回成果
          </FaButton>
          <FaButton variant="outline" :disabled="feedbackBusy" @click="saveRevision('SAVE_ONLY')">
            只保存，稍后执行
          </FaButton>
          <FaButton :disabled="feedbackBusy" @click="saveRevision('SAVE_AND_RUN')">
            保存并重新比较
          </FaButton>
        </template>
      </template>
    </FaModal>
    <FaFixedBar position="bottom" class="flex-center gap-4">
      <FaButton :disabled="submitting || Boolean(project?.task.cancelledAt)" @click="onSubmit">
        {{ taskId ? '更新任务' : '交给伙伴' }}
      </FaButton><FaButton variant="outline" @click="onCancel">
        返回列表
      </FaButton>
    </FaFixedBar>
  </div>
</template>
