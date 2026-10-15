<script setup lang="ts">
import type { Delegation, DelegationProjectSummary, DelegationRun } from '@/api/modules/delegations'
import { formatDelegationTime, safeSourceUrl } from '../helpers'

const props = defineProps<{ task: Delegation, run: DelegationRun, project?: DelegationProjectSummary, partner?: string, showOpen?: boolean }>()
defineEmits<{ open: [] }>()
const stale = computed(() => props.run.review?.reviewState === 'SUPERSEDED' || (props.task.revision !== undefined && props.run.taskRevision !== undefined && props.task.revision !== props.run.taskRevision))
const summary = computed(() => props.project?.summary || props.run.output?.slice(0, 280) || props.run.items?.[0]?.summary || '打开成果，查看伙伴已经获得的信息和需要你决定的下一步。')
const sources = computed(() => props.project?.scope || props.task.sourceUrls || [])
const sourceCount = computed(() => props.project?.sourceCount ?? new Set(props.run.evidence?.map(item => safeSourceUrl(item.url)).filter(Boolean)).size)
const reviewLabel = computed(() => stale.value ? '来自旧偏好' : props.run.review?.reviewState === 'ACCEPTED' ? '这份结果已认可' : props.run.review?.reviewState === 'REVISION_REQUESTED' ? '偏好已修改' : props.run.status === 'SUCCEEDED' ? '比较已生成，等你看看' : props.run.status === 'RUNNING' ? '正在整理允许范围内的资料' : '有部分信息需要你看看')
</script>

<template>
  <FaCard :title="task.title" class="min-w-0">
    <div class="text-sm mb-3 flex flex-wrap gap-x-4 gap-y-2 items-center">
      <FaTag variant="outline">
        {{ reviewLabel }}
      </FaTag>
      <span v-if="partner">伙伴：{{ partner }}</span>
      <span>结果版本 {{ run.taskRevision ?? '未知' }}</span>
      <span>{{ formatDelegationTime(run.completedAt || run.createdAt, task.zoneId) }}</span>
    </div>
    <p class="text-sm leading-7 whitespace-pre-wrap break-words">
      {{ summary }}
    </p>
    <p class="text-sm text-muted-foreground mt-3">
      已读取来源 {{ sourceCount }} 份；资料范围 {{ sources.length }} 个公开页面。
    </p>
    <ul v-if="sources.length" class="text-sm mt-2 space-y-1">
      <li v-for="source in sources" :key="source" class="min-w-0 break-words">
        <a v-if="safeSourceUrl(source)" :href="safeSourceUrl(source)" target="_blank" rel="noopener noreferrer" class="text-primary underline">{{ source }}</a>
        <span v-else>来源链接不可用</span>
      </li>
    </ul>
    <p v-if="project?.missingInformation" class="text-sm leading-6 mt-3 whitespace-pre-wrap break-words">
      待确认：{{ project.missingInformation }}
    </p>
    <p class="text-sm leading-6 mt-3 break-words">
      {{ project?.nextDecision || (stale ? '可以查看旧成果；请按当前偏好重新比较后再决定。' : '查看来源和全文后，你可以认可结果或修改偏好。') }}
    </p>
    <FaButton v-if="showOpen" variant="outline" class="mt-4" @click="$emit('open')">
      查看成果与反馈
    </FaButton>
  </FaCard>
</template>
