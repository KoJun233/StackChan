<script setup lang="ts">
import type { PersonaInput } from '@/api/modules/personaMemory'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { getPersona, savePersona } from '@/api/modules/personaMemory'

defineOptions({ name: 'CompanionPersona' })

const loading = ref(false)
const model = ref<PersonaInput>({
  displayName: 'StackChan',
  tone: 'WARM',
  replyLength: 'BALANCED',
  proactivity: 'BALANCED',
  topicBoundaries: '',
  taboos: '',
})

const toneOptions = [
  { label: '温暖亲切', value: 'WARM' },
  { label: '平静克制', value: 'CALM' },
  { label: '活泼有趣', value: 'LIVELY' },
  { label: '专业清晰', value: 'PROFESSIONAL' },
]

const replyLengthOptions = [
  { label: '简短', value: 'SHORT' },
  { label: '适中', value: 'BALANCED' },
  { label: '详细', value: 'DETAILED' },
]

const proactivityOptions = [
  { label: '仅在需要时主动', value: 'RESERVED' },
  { label: '适度主动', value: 'BALANCED' },
  { label: '积极主动关心', value: 'PROACTIVE' },
]

const validationSchema = toTypedSchema(z.object({
  displayName: z.string().trim().min(1, '请输入机器人名字').max(80, '名字不能超过 80 个字符'),
  tone: z.enum(['WARM', 'CALM', 'LIVELY', 'PROFESSIONAL']),
  replyLength: z.enum(['SHORT', 'BALANCED', 'DETAILED']),
  proactivity: z.enum(['RESERVED', 'BALANCED', 'PROACTIVE']),
  topicBoundaries: z.string().max(2000, '话题边界不能超过 2000 个字符'),
  taboos: z.string().max(2000, '禁忌不能超过 2000 个字符'),
}))

async function load() {
  loading.value = true
  try {
    const persona = await getPersona()
    model.value = {
      displayName: persona.displayName,
      tone: persona.tone,
      replyLength: persona.replyLength,
      proactivity: persona.proactivity,
      topicBoundaries: persona.topicBoundaries,
      taboos: persona.taboos,
    }
  }
  catch (error) {
    useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取机器人设定。' })
  }
  finally {
    loading.value = false
  }
}

async function submit(values: PersonaInput) {
  loading.value = true
  try {
    const saved = await savePersona({
      ...values,
      displayName: values.displayName.trim(),
      topicBoundaries: values.topicBoundaries.trim(),
      taboos: values.taboos.trim(),
    })
    model.value = {
      displayName: saved.displayName,
      tone: saved.tone,
      replyLength: saved.replyLength,
      proactivity: saved.proactivity,
      topicBoundaries: saved.topicBoundaries,
      taboos: saved.taboos,
    }
    useFaToast().success('人设已保存', { description: '新的设定会从下一轮文本和语音对话开始生效。' })
  }
  catch (error) {
    useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存机器人设定。' })
  }
  finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <FaPageMain title="人设设置" description="用结构化选项控制机器人的名字、语气、回复长度、主动程度和交互边界。">
    <div class="max-w-4xl space-y-4">
      <FaAlert
        title="人设与底层系统规则分开管理"
        description="这里的设置会叠加到 AI 配置中的系统提示词之后；安全边界和用户当前明确表达始终拥有更高优先级。"
      />
      <FaLoading :loading="loading">
        <FaCard>
          <FaForm :model="model" :validation-schema="validationSchema" scroll-to-error @submit="submit">
            <div class="gap-6 grid md:grid-cols-2">
              <FaFormItem name="displayName" label="机器人名字" required class="md:col-span-2">
                <FaInput v-model="model.displayName" placeholder="例如：小栈" />
              </FaFormItem>
              <FaFormItem name="tone" label="交流语气" required>
                <FaSelect v-model="model.tone" :options="toneOptions" class="w-full" />
              </FaFormItem>
              <FaFormItem name="replyLength" label="回复长度" required>
                <FaSelect v-model="model.replyLength" :options="replyLengthOptions" class="w-full" />
              </FaFormItem>
              <FaFormItem name="proactivity" label="主动程度" required class="md:col-span-2">
                <FaSelect v-model="model.proactivity" :options="proactivityOptions" class="w-full" />
              </FaFormItem>
              <FaFormItem
                name="topicBoundaries"
                label="话题边界"
                class="md:col-span-2"
                description="描述哪些话题不要主动提起，或只在用户主动询问时讨论。"
              >
                <FaTextarea v-model="model.topicBoundaries" rows="5" align="block" placeholder="例如：不要主动讨论工作压力，除非用户先提起。" />
              </FaFormItem>
              <FaFormItem
                name="taboos"
                label="交互禁忌"
                class="md:col-span-2"
                description="描述机器人绝不应采用的表达方式或行为。"
              >
                <FaTextarea v-model="model.taboos" rows="5" align="block" placeholder="例如：不要挖苦、说教或假装记得未确认的信息。" />
              </FaFormItem>
            </div>
            <div class="mt-6 flex justify-end">
              <FaButton type="submit" :loading="loading">
                保存人设
              </FaButton>
            </div>
          </FaForm>
        </FaCard>
      </FaLoading>
    </div>
  </FaPageMain>
</template>
