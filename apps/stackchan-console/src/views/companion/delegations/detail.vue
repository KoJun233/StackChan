<script setup lang="ts">
import type { Delegation, DelegationRun } from '@/api/modules/delegations'
import { listDelegationRuns, retryDelegationDelivery } from '@/api/modules/delegations'
import eventBus from '@/utils/eventBus'
import DetailForm from './components/DetailForm/index.vue'
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
const { pagination, getParams, onSizeChange, onCurrentChange } = usePagination()
let requestId = 0
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
  stopRefresh()
})
onBeforeUnmount(() => {
  active = false
  ++requestId
  stopRefresh()
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
        <DetailForm :id="taskId" ref="formRef" :example="example" @saved="savedTask = $event" />
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
    <FaFixedBar position="bottom" class="flex-center gap-4">
      <FaButton :disabled="submitting" @click="onSubmit">
        {{ taskId ? '更新任务' : '交给伙伴' }}
      </FaButton><FaButton variant="outline" @click="onCancel">
        返回列表
      </FaButton>
    </FaFixedBar>
  </div>
</template>
