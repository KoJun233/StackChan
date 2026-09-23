<script setup lang="ts">
import type { SettingsFormHandle } from '@/views/settings/interaction/useCompanionSettings'
import { useCompanionSettings } from '@/views/settings/interaction/useCompanionSettings'

defineOptions({ name: 'InteractionSettings' })
const settingsForm = useTemplateRef<SettingsFormHandle>('settingsForm')
const {
  targetBusy,
  loading,
  saving,
  settingsReady,
  settingsError,
  auxiliaryErrors,
  dirty,
  leaveDialog,
  stopping,
  topicCooldowns,
  topicRoleId,
  proactivePause,
  pauseAction,
  proactivePaused,
  topicRoleOptions,
  topicsLoading,
  topicsError,
  proactiveNextAt,
  silentPresenceNextAt,
  resumingTopic,
  model,
  bodyAction,
  activeSection,
  isWorkdayPage,
  pageTitle,
  pageDescription,
  sectionTabs,
  bodyMotions,
  deviceOptions,
  selectedDevice,
  missedPolicyOptions,
  validationSchema,
  loadSettings,
  loadWorkdayExtras,
  loadTopics,
  updateProactivePause,
  resumeTopic,
  submit,
  stopAudio,
  calibrateBody,
  configureBody,
  playBody,
  stopBody,
  finishLeave,
  saveAndLeave,
  changeDevice,
} = useCompanionSettings('care', settingsForm)
</script>

<template>
  <AppPageShell :title="pageTitle" :description="pageDescription">
    <FaAlert v-if="settingsError" variant="destructive" title="设置未加载" :description="settingsError">
      <template #action>
        <FaButton @click="loadSettings(model.deviceId)">
          重试
        </FaButton>
      </template>
    </FaAlert>
    <FaAlert v-if="auxiliaryErrors.length" title="部分信息暂不可用" :description="`${auxiliaryErrors.join('；')}。工作规则仍可编辑和保存。`">
      <template #action>
        <FaButton variant="outline" @click="loadWorkdayExtras()">
          重试
        </FaButton>
      </template>
    </FaAlert>
    <AppLoading :loading="loading">
      <FaForm
        id="interaction-settings-form"
        ref="settingsForm"
        :disabled="!settingsReady || saving"
        :model="model"
        :validation-schema="validationSchema"
        keep-values-on-unmount
        scroll-to-error
        class="mx-auto gap-6 grid grid-cols-1 max-w-6xl"
        @submit="submit"
      >
        <FaCard title="当前机器人" :description="!selectedDevice ? '请选择机器人' : selectedDevice.online ? '在线 · 设置作用于这台机器人' : '离线 · 保存到服务端，设备重新连接后应用'">
          <div class="max-w-xl space-y-2">
            <label class="text-sm font-medium">目标机器人</label>
            <FaSelect :model-value="model.deviceId" :options="deviceOptions" :disabled="targetBusy" class="w-full" @update:model-value="changeDevice" />
            <p class="text-sm text-muted-foreground">
              主动聊天查看伙伴：{{ topicRoleOptions.find(role => role.value === topicRoleId)?.label ?? '未获取' }}
            </p>
          </div>
        </FaCard>

        <FaTabs v-model="activeSection" :list="sectionTabs" list-class="justify-start overflow-x-auto" content-class="pt-5">
          <template #device>
            <div class="gap-6 grid grid-cols-1">
              <FaCard title="目标机器人与本地呈现" class="md:col-span-2">
                <div class="gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-2">
                  <FaFormItem name="volumePercent" label="播报音量" required description="0 为静音，100 为最大音量。">
                    <FaNumberField v-model="model.volumePercent" :min="0" :max="100" :step="5" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="nightMode" label="夜间显示" description="降低非屏保状态下的屏幕亮度。">
                    <FaSwitch v-model="model.nightMode" />
                  </FaFormItem>
                  <div class="flex items-end justify-end">
                    <FaButton type="button" variant="destructive" :loading="stopping" @click="stopAudio">
                      <FaIcon name="i-ri:stop-circle-line" />
                      立即停止播报
                    </FaButton>
                  </div>
                </div>
                <div class="mt-6 pt-6 border-t gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-2">
                  <FaFormItem
                    name="continuousConversationEnabled"
                    label="连续对话"
                    description="回答后仅在本地 VAD 检测到有效语音才上传；默认关闭。"
                  >
                    <FaSwitch v-model="model.continuousConversationEnabled" />
                  </FaFormItem>
                  <FaFormItem
                    name="followUpWindowSeconds"
                    label="跟进聆听窗口（秒）"
                    required
                    description="允许 3–8 秒；每次会话最多 3 个跟进回合且总计不超过 2 分钟。"
                  >
                    <FaNumberField v-model="model.followUpWindowSeconds" :min="3" :max="8" class="w-full" />
                  </FaFormItem>
                </div>
              </FaCard>

              <FaCard title="K151 安全身体感" class="md:col-span-2">
                <div class="gap-5 grid">
                  <FaAlert
                    title="动作默认关闭，校准不会主动转动舵机"
                    description="先扶正头部并执行中位校准，再显式启用。固件只接受下列五种固定动作；录音、播报、升级、离线、触摸停止、超时或反馈异常都会拒绝或立即断电。"
                  />
                  <div class="gap-4 grid lg:grid-cols-4 sm:grid-cols-2">
                    <FaCard title="运动安全状态">
                      <p class="text-lg font-semibold">
                        {{ selectedDevice?.body.motionState === 'RUNNING' ? '动作中' : selectedDevice?.body.motionState === 'ARMED' ? '已启用' : '已禁用' }}
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        {{ selectedDevice?.body.calibrated ? '已校准中位' : '尚未校准' }} · {{ selectedDevice?.body.servoFeedbackSupported ? '反馈正常' : '反馈未验证' }}
                      </p>
                    </FaCard>
                    <FaCard title="在场感知">
                      <p class="text-lg font-semibold">
                        {{ selectedDevice?.body.proximitySupported ? (selectedDevice.body.present ? '检测到在场' : '当前未在场') : '不支持' }}
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        只上报布尔结果，不保存原始距离
                      </p>
                    </FaCard>
                    <FaCard title="环境光">
                      <p class="text-lg font-semibold">
                        {{ selectedDevice?.body.ambientLightSupported ? selectedDevice.body.ambientLight : '不可用' }}
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        仅 DARK / DIM / NORMAL / BRIGHT 档位
                      </p>
                    </FaCard>
                    <FaCard title="最近安全诊断">
                      <p class="text-lg font-semibold">
                        {{ selectedDevice?.body.lastFailureCode ?? 'NONE' }}
                      </p>
                      <p class="text-sm text-muted-foreground mt-1">
                        累计 {{ selectedDevice?.body.failureCount ?? 0 }} 次拒绝或停止
                      </p>
                    </FaCard>
                  </div>
                  <div class="flex flex-wrap gap-3">
                    <FaButton
                      type="button"
                      variant="outline"
                      :disabled="!selectedDevice?.online || !selectedDevice?.body.bodyMotionSupported"
                      :loading="bodyAction === 'calibrate'"
                      @click="calibrateBody"
                    >
                      校准当前中位
                    </FaButton>
                    <FaButton
                      type="button"
                      :disabled="!selectedDevice?.online || !selectedDevice?.body.bodyMotionSupported || !selectedDevice?.body.calibrated"
                      :loading="bodyAction === 'enable'"
                      @click="configureBody(true)"
                    >
                      显式启用动作
                    </FaButton>
                    <FaButton
                      type="button"
                      variant="destructive"
                      :disabled="!selectedDevice?.online"
                      :loading="bodyAction === 'disable'"
                      @click="stopBody"
                    >
                      立即停止并禁用
                    </FaButton>
                  </div>

                  <div class="pt-5 border-t">
                    <div class="text-sm font-medium mb-3">
                      固件内置动作模板
                    </div>
                    <div class="flex flex-wrap gap-3">
                      <FaButton
                        v-for="motion in bodyMotions"
                        :key="motion.value"
                        type="button"
                        variant="outline"
                        :disabled="!selectedDevice?.online || selectedDevice?.body.motionState !== 'ARMED' || !!bodyAction"
                        :loading="bodyAction === motion.value"
                        @click="playBody(motion.value)"
                      >
                        {{ motion.label }}
                      </FaButton>
                    </div>
                  </div>
                </div>
              </FaCard>
            </div>
          </template>

          <template #care>
            <div class="space-y-5">
              <FaCard title="现在想安静一会儿？">
                <div class="space-y-3">
                  <div class="text-sm font-medium mb-3">
                    查看伙伴
                  </div>
                  <FaSelect v-model="topicRoleId" :options="topicRoleOptions" :disabled="!!resumingTopic || pauseAction" class="mb-3 w-full" @change="loadTopics" />
                  <p class="text-xs text-muted-foreground mb-3">
                    只查看和恢复所选伙伴的话题，不切换设备当前伙伴。
                  </p>
                  <div class="mb-4 space-y-3">
                    <p v-if="proactivePause && !topicsLoading && !topicsError" class="text-sm">
                      {{ proactivePaused ? `主动聊天暂停至 ${new Date(proactivePause.pausedUntil!).toLocaleString(undefined, { timeZone: model.zoneId })}（设备时间）` : '主动聊天未暂停' }}
                    </p>
                    <p class="text-xs text-muted-foreground">
                      暂停仅作用于所选伙伴的主动聊天，无声陪伴和提醒照常执行；到期自动解除，仍遵守原有时段和次数设置。
                    </p>
                    <div class="flex flex-wrap gap-2">
                      <FaButton type="button" variant="outline" :disabled="!topicRoleId || topicsLoading || !!topicsError || pauseAction" @click="updateProactivePause('today')">
                        今天别主动聊
                      </FaButton>
                      <FaButton type="button" variant="outline" :disabled="!topicRoleId || topicsLoading || !!topicsError || pauseAction" @click="updateProactivePause('hour')">
                        暂停一小时
                      </FaButton>
                      <FaButton v-if="proactivePaused" type="button" variant="outline" :disabled="topicsLoading || pauseAction" @click="updateProactivePause('resume')">
                        提前恢复
                      </FaButton>
                    </div>
                  </div>
                  <FaAlert v-if="topicsError" title="话题加载失败" :description="topicsError" />
                  <FaAlert v-else-if="topicsLoading" title="正在读取话题" />
                  <FaCollapsible>
                    <template #trigger>
                      <span class="text-sm underline">查看主动话题记录</span>
                    </template>
                    <FaAlert
                      v-if="!topicsError && !topicsLoading && topicCooldowns.length === 0"
                      title="暂无主题记录"
                      description="个性化主题成功进入主动提醒后才会出现；使用记录不复制记忆正文。"
                    />
                    <div v-for="topic in topicCooldowns" :key="topic.topicKey" class="text-sm mb-3 p-3 border rounded-md">
                      <div class="flex gap-3 items-center justify-between">
                        <div class="min-w-0">
                          <div class="font-medium truncate">
                            {{ topic.topicKey }}
                          </div>
                          <div class="text-xs text-muted-foreground mt-1">
                            冷却至 {{ new Date(topic.cooldownUntil).toLocaleString() }}
                            <span v-if="topic.userMuted"> · 用户已要求不再主动提及</span>
                          </div>
                        </div>
                        <FaButton
                          v-if="topic.userMuted || new Date(topic.cooldownUntil).getTime() > Date.now()"
                          type="button"
                          size="sm"
                          variant="outline"
                          :loading="resumingTopic === topic.topicKey"
                          :disabled="!!resumingTopic || topicsLoading"
                          @click="resumeTopic(topic.topicKey)"
                        >
                          恢复主动聊这个话题
                        </FaButton>
                      </div>
                    </div>
                  </FaCollapsible>
                </div>
              </FaCard>

              <FaCard title="语音开场">
                <div class="gap-6 grid">
                  <p class="text-sm text-muted-foreground">
                    每天最多三次，至少间隔一小时；离线、忙碌或免打扰时顺延。
                  </p>
                  <FaCollapsible>
                    <template #trigger>
                      <span class="text-sm underline">话题从哪里来？</span>
                    </template><p class="text-sm text-muted-foreground py-3">
                      个性化开场可匹配已确认兴趣与近期技术标题，来源保存在提醒记录。没有合适来源时使用普通问候。
                    </p>
                  </FaCollapsible>
                  <FaAlert
                    v-if="model.proactiveEnabled && proactiveNextAt"
                    title="下一次随机候选"
                    :description="`${new Date(proactiveNextAt).toLocaleString()}；到点时仍会检查在线、免打扰和忙碌状态。`"
                  />
                  <FaFormItem name="proactiveEnabled" label="允许语音主动开场">
                    <FaSwitch v-model="model.proactiveEnabled" />
                  </FaFormItem>
                  <FaFormItem
                    name="proactivePersonalizationEnabled"
                    label="个性化开场"
                    description="结合伙伴个性和已确认的兴趣选择话题。"
                  >
                    <FaSwitch v-model="model.proactivePersonalizationEnabled" />
                  </FaFormItem>
                  <FaFormItem name="proactiveStart" label="允许开始" required>
                    <FaInput v-model="model.proactiveStart" type="time" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="proactiveEnd" label="允许结束" required>
                    <FaInput v-model="model.proactiveEnd" type="time" class="w-full" />
                  </FaFormItem>
                  <FaCollapsible>
                    <template #trigger>
                      <span class="text-sm underline">频率与备用问候</span>
                    </template><div class="pt-4 space-y-4">
                      <FaFormItem name="proactiveMinIntervalMinutes" label="最小间隔（分钟）" required>
                        <FaNumberField v-model="model.proactiveMinIntervalMinutes" :min="60" :max="1440" :step="30" class="w-full" />
                      </FaFormItem>
                      <FaFormItem name="proactiveDailyLimit" label="每日最多次数" required>
                        <FaNumberField v-model="model.proactiveDailyLimit" :min="1" :max="3" class="w-full" />
                      </FaFormItem>
                      <FaFormItem name="proactiveContent" label="生成失败时的备用问候" required>
                        <FaTextarea v-model="model.proactiveContent" rows="4" align="block" class="w-full" />
                      </FaFormItem>
                    </div>
                  </FaCollapsible>
                </div>
              </FaCard>
              <FaCard title="无声陪伴">
                <div class="space-y-4">
                  <p class="text-sm text-muted-foreground">
                    无声陪伴每 30–90 分钟显示一次短表情，每天最多八次，与语音开场独立。
                  </p>
                  <FaAlert
                    v-if="model.silentPresenceEnabled && silentPresenceNextAt"
                    title="下一次无声表情候选"
                    :description="`${new Date(silentPresenceNextAt).toLocaleString()}；离线、免打扰、语音或提醒播放期间不会显示。`"
                  />
                  <FaFormItem
                    name="silentPresenceEnabled"
                    label="允许无声陪伴表情"
                    description="默认关闭。只使用现有动态表情能力，不读取摄像头、麦克风或电脑活动。"
                  >
                    <FaSwitch v-model="model.silentPresenceEnabled" />
                  </FaFormItem>
                </div>
              </FaCard>
              <FaCard title="免打扰与离线提醒">
                <div class="gap-6 grid">
                  <FaAlert title="播报边界" description="免打扰期间不会播放提醒或主动问候；到结束时间后再按规则处理。" />
                  <FaFormItem name="dndEnabled" label="启用免打扰">
                    <FaSwitch v-model="model.dndEnabled" />
                  </FaFormItem>
                  <FaFormItem name="dndStart" label="开始时间" required>
                    <FaInput v-model="model.dndStart" type="time" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="dndEnd" label="结束时间" required>
                    <FaInput v-model="model.dndEnd" type="time" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="zoneId" label="规则时区" required description="使用 IANA 时区处理跨日和夏令时。">
                    <FaInput v-model="model.zoneId" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="missedReminderPolicy" label="离线错过后" required>
                    <FaSelect v-model="model.missedReminderPolicy" :options="missedPolicyOptions" class="w-full" />
                  </FaFormItem>
                  <FaFormItem v-if="model.missedReminderPolicy === 'SNOOZE'" name="missedSnoozeMinutes" label="稍后分钟数" required>
                    <FaNumberField v-model="model.missedSnoozeMinutes" :min="1" :max="1440" class="w-full" />
                  </FaFormItem>
                </div>
              </FaCard>
            </div>
          </template>
        </FaTabs>

        <FaFixedBar position="bottom" class="flex gap-3 items-center justify-center">
          <span class="text-sm text-muted-foreground">{{ dirty ? '有未保存的修改' : '没有未保存的修改' }}</span>
          <FaButton type="submit" form="interaction-settings-form" :loading="saving" :disabled="!settingsReady || !dirty">
            {{ isWorkdayPage ? '保存工作规则' : '保存陪伴设置' }}
          </FaButton>
        </FaFixedBar>
      </FaForm>
    </AppLoading>
    <FaModal open-auto-focus :closable="!saving" :close-on-press-escape="!saving" :model-value="leaveDialog" title="保留当前修改？" :show-confirm-button="false" :close-on-click-overlay="false" @update:model-value="finishLeave(false)">
      当前机器人的设置尚未保存。
      <template #footer>
        <FaButton variant="ghost" :disabled="saving" @click="finishLeave(false)">
          留在当前页
        </FaButton><FaButton variant="outline" :disabled="saving" @click="finishLeave(true)">
          放弃修改
        </FaButton><FaButton :loading="saving" @click="saveAndLeave">
          保存并继续
        </FaButton>
      </template>
    </FaModal>
  </AppPageShell>
</template>
