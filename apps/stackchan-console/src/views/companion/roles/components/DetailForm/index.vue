<script setup lang="ts">
import type { FormExpose } from '@fantastic-admin/components'
import type { CompanionRoleInput } from '@/api/modules/roles'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { createRole, getRole, updateRole } from '@/api/modules/roles'

const props = withDefaults(defineProps<{ id?: string }>(), { id: '' })
const formRef = useTemplateRef<FormExpose>('formRef')
const loading = ref(false)
type RoleFormModel = Omit<CompanionRoleInput, 'ttsVoiceOverride'> & { ttsVoiceOverride: string }
const model = ref<RoleFormModel>({ name: '', tone: 'WARM', replyLength: 'BALANCED', proactivity: 'BALANCED', backgroundInstructions: '', topicBoundaries: '', taboos: '', ttsVoiceOverride: '', expressionThemeColor: '#FF4FA3' })
const schema = toTypedSchema(z.object({ name: z.string().trim().min(1).max(80), tone: z.enum(['WARM', 'CALM', 'LIVELY', 'PROFESSIONAL']), replyLength: z.enum(['SHORT', 'BALANCED', 'DETAILED']), proactivity: z.enum(['RESERVED', 'BALANCED', 'PROACTIVE']), backgroundInstructions: z.string().max(4000), topicBoundaries: z.string().max(2000), taboos: z.string().max(2000), ttsVoiceOverride: z.string().trim().max(160).nullable(), expressionThemeColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/) }))
const toneOptions = [{ label: '温暖亲切', value: 'WARM' }, { label: '平静克制', value: 'CALM' }, { label: '活泼有趣', value: 'LIVELY' }, { label: '专业清晰', value: 'PROFESSIONAL' }]
const lengthOptions = [{ label: '简短', value: 'SHORT' }, { label: '适中', value: 'BALANCED' }, { label: '详细', value: 'DETAILED' }]
const proactivityOptions = [{ label: '仅在需要时主动', value: 'RESERVED' }, { label: '适度主动', value: 'BALANCED' }, { label: '积极主动关心', value: 'PROACTIVE' }]

async function load(id: string) {
  if (!id) return
  loading.value = true
  try { const role = await getRole(id); model.value = { name: role.name, tone: role.tone, replyLength: role.replyLength, proactivity: role.proactivity, backgroundInstructions: role.backgroundInstructions, topicBoundaries: role.topicBoundaries, taboos: role.taboos, ttsVoiceOverride: role.ttsVoiceOverride ?? '', expressionThemeColor: role.expressionThemeColor ?? '#FF4FA3' } }
  catch (error) { useFaToast().error('加载失败', { description: error instanceof Error ? error.message : '无法读取角色。' }) }
  finally { loading.value = false }
}

async function submit() {
  if (!(await formRef.value?.validate())?.valid) return false
  loading.value = true
  try { const input = { ...model.value, name: model.value.name.trim(), backgroundInstructions: model.value.backgroundInstructions.trim(), topicBoundaries: model.value.topicBoundaries.trim(), taboos: model.value.taboos.trim(), ttsVoiceOverride: model.value.ttsVoiceOverride.trim() || null, expressionThemeColor: model.value.expressionThemeColor.toUpperCase() }; if (props.id) await updateRole(props.id, input); else await createRole(input); useFaToast().success(props.id ? '角色已更新' : '角色已创建'); return true }
  catch (error) { useFaToast().error('保存失败', { description: error instanceof Error ? error.message : '无法保存角色。' }); return false }
  finally { loading.value = false }
}
onMounted(() => load(props.id)); watch(() => props.id, load); defineExpose({ submit })
</script>

<template>
  <FaLoading :loading="loading"><FaForm ref="formRef" :model="model" :validation-schema="schema" :label-width="120" class="grid gap-6" scroll-to-error>
    <FaFormItem name="name" label="角色名称" required><FaInput v-model="model.name" placeholder="例如：个人助理" /></FaFormItem>
    <div class="gap-4 grid md:grid-cols-3"><FaFormItem name="tone" label="交流语气" required><FaSelect v-model="model.tone" :options="toneOptions" /></FaFormItem><FaFormItem name="replyLength" label="回复长度" required><FaSelect v-model="model.replyLength" :options="lengthOptions" /></FaFormItem><FaFormItem name="proactivity" label="主动程度" required><FaSelect v-model="model.proactivity" :options="proactivityOptions" /></FaFormItem></div>
    <FaFormItem name="backgroundInstructions" label="背景与行为" description="最多 4000 字，作为受限数据附加在基础安全规则之后。"><FaTextarea v-model="model.backgroundInstructions" rows="8" align="block" /></FaFormItem>
    <FaFormItem name="topicBoundaries" label="话题边界"><FaTextarea v-model="model.topicBoundaries" rows="5" align="block" /></FaFormItem>
    <FaFormItem name="taboos" label="交互禁忌"><FaTextarea v-model="model.taboos" rows="5" align="block" /></FaFormItem>
    <FaFormItem name="ttsVoiceOverride" label="角色音色" description="可选。填写当前语音供应商支持的音色标识；留空继承全局音色，覆盖音色不可用时也会自动回退全局音色。"><FaInput v-model="model.ttsVoiceOverride" placeholder="例如：longanhuan_v3.6" /></FaFormItem>
    <FaFormItem name="expressionThemeColor" label="表情主色" description="内置动态球体使用此主色派生高光、阴影和粒子；错误、离线和更新颜色不受角色覆盖。">
      <div class="flex items-center gap-3"><FaInput v-model="model.expressionThemeColor" type="color" class="w-20" /><FaInput v-model="model.expressionThemeColor" placeholder="#FF4FA3" class="max-w-48" /></div>
    </FaFormItem>
    <FaAlert title="严格隔离" description="新角色会拥有独立的网页/语音会话、长期记忆、提醒和主动话题状态；音量、免打扰、模型和语音供应商仍按设备共享，只有音色可按角色覆盖。" />
  </FaForm></FaLoading>
</template>
