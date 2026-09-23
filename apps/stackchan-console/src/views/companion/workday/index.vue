<script setup lang="ts">
import type { SettingsFormHandle } from '@/views/settings/interaction/useCompanionSettings'
import { useCompanionSettings } from '@/views/settings/interaction/useCompanionSettings'

defineOptions({ name: 'WorkdayCompanion' })
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
  model,
  workdayRuntime,
  workdayMetrics,
  workdayPilot,
  workdayWeather,
  icloudConnection,
  calendarEvents,
  calendarEventsLoading,
  calendarEventsError,
  icloudAccountEmail,
  icloudAppSpecificPassword,
  calendarAction,
  weatherAction,
  workdayAction,
  pilotAction,
  activeSection,
  isWorkdayPage,
  pageTitle,
  pageDescription,
  sectionTabs,
  runtimeStateLabels,
  deviceOptions,
  selectedDevice,
  validationSchema,
  loadSettings,
  loadWorkdayExtras,
  submit,
  weatherStatusText,
  weatherIcon,
  weatherStatusIcon,
  weatherFailureText,
  testWeather,
  syncWeather,
  formatFocus,
  formatBriefStatus,
  calendarStatusText,
  calendarFailureText,
  loadCalendarEvents,
  calendarEventDate,
  calendarEventTime,
  testCalendar,
  connectCalendar,
  saveAllowedCalendars,
  syncCalendar,
  disconnectCalendar,
  beginWorkday,
  endWorkday,
  respondToRest,
  startPilot,
  restartPilot,
  markFalseTrigger,
  undoFalseTrigger,
  pilotStatusText,
  finishLeave,
  saveAndLeave,
  changeDevice,
} = useCompanionSettings('workday', settingsForm)
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
          </div>
        </FaCard>

        <FaTabs v-model="activeSection" :list="sectionTabs" list-class="justify-start overflow-x-auto" content-class="pt-5">
          <template #workday-overview>
            <FaCard title="工作日桌面陪伴">
              <div class="gap-6 grid">
                <FaAlert
                  title="工作陪伴由你显式开始"
                  description="开始后会按设备在场状态累计专注，首次简报读取新鲜天气和允许的日历缓存；离开、免打扰、活动语音和临近日程会抑制打扰。"
                />
                <div class="flex flex-wrap gap-3">
                  <FaButton
                    type="button"
                    :loading="workdayAction === 'start'"
                    :disabled="Boolean(workdayAction) || workdayRuntime?.state !== 'OFF' || !selectedDevice?.online"
                    @click="beginWorkday"
                  >
                    开始工作
                  </FaButton>
                  <FaButton
                    type="button"
                    variant="outline"
                    :loading="workdayAction === 'stop'"
                    :disabled="Boolean(workdayAction) || !workdayRuntime || workdayRuntime.state === 'OFF'"
                    @click="endWorkday"
                  >
                    结束工作
                  </FaButton>
                  <template v-if="workdayRuntime?.state === 'REST_PROMPTED'">
                    <FaButton type="button" variant="outline" :loading="workdayAction === 'START_REST'" :disabled="Boolean(workdayAction)" @click="respondToRest('START_REST')">
                      开始休息
                    </FaButton>
                    <FaButton type="button" variant="outline" :loading="workdayAction === 'SNOOZE'" :disabled="Boolean(workdayAction)" @click="respondToRest('SNOOZE')">
                      稍后 10 分钟
                    </FaButton>
                    <FaButton type="button" variant="outline" :loading="workdayAction === 'SKIP_FOR_DAY'" :disabled="Boolean(workdayAction)" @click="respondToRest('SKIP_FOR_DAY')">
                      今天跳过
                    </FaButton>
                  </template>
                </div>
                <div class="gap-4 grid lg:grid-cols-4 sm:grid-cols-2">
                  <FaCard title="当前状态">
                    <p class="text-lg font-semibold">
                      {{ workdayRuntime ? runtimeStateLabels[workdayRuntime.state] : '尚未加载' }}
                    </p>
                    <p class="text-sm text-muted-foreground mt-1">
                      {{ workdayRuntime?.workDate ? `工作日 ${workdayRuntime.workDate}` : '当前没有活动工作日' }}
                    </p>
                  </FaCard>
                  <FaCard title="本轮专注" description="只累计明确在场时间。">
                    <p class="text-lg font-semibold">
                      {{ formatFocus(workdayRuntime?.focusSeconds ?? 0) }}
                    </p>
                    <p class="text-sm text-muted-foreground mt-1">
                      目标 {{ formatFocus(workdayRuntime?.focusTargetSeconds ?? model.focusMinutes * 60) }}
                    </p>
                  </FaCard>
                  <FaCard title="今日首次简报" description="占位后即使重启也不会重复。">
                    <p class="text-lg font-semibold">
                      {{ formatBriefStatus(workdayRuntime?.briefStatus ?? null) }}
                    </p>
                    <p class="text-sm text-muted-foreground mt-1">
                      成功、部分成功或取消后当天不重复
                    </p>
                  </FaCard>
                  <FaCard title="近 90 天" description="仅保存本地聚合，不含正文。">
                    <p class="text-lg font-semibold">
                      {{ workdayMetrics?.summary.activeWorkdays ?? 0 }} 个活跃工作日
                    </p>
                    <p class="text-sm text-muted-foreground mt-1">
                      专注 {{ formatFocus(workdayMetrics?.summary.focusSeconds ?? 0) }} · 休息 {{ workdayMetrics?.summary.restStartedCount ?? 0 }} 次
                    </p>
                  </FaCard>
                </div>
              </div>
            </FaCard>
          </template>

          <template #pilot>
            <FaCard title="工作陪伴工程观察" description="仅检查已有工程门槛，不衡量你是否愿意聊天、记忆是否准确或伙伴是否讨喜。">
              <div class="gap-6 grid">
                <div class="pt-6 border-t gap-4 grid">
                  <div class="flex flex-wrap gap-3 items-center justify-between">
                    <div>
                      <h3 class="text-base font-semibold">
                        十四天私用观察
                      </h3>
                      <p class="text-sm text-muted-foreground mt-1">
                        十四天内至少八个计划工作日发生显式工作会话；每周误播报不超过两次，且无动作失败或设备重启。
                      </p>
                    </div>
                    <div class="flex flex-wrap gap-2">
                      <FaButton
                        v-if="!workdayPilot?.started"
                        type="button"
                        :loading="pilotAction === 'start'"
                        :disabled="Boolean(pilotAction)"
                        @click="startPilot"
                      >
                        开始十四天观察
                      </FaButton>
                      <template v-else>
                        <template v-if="!workdayPilot.windowComplete">
                          <FaButton type="button" variant="outline" :loading="pilotAction === 'mark'" :disabled="Boolean(pilotAction)" @click="markFalseTrigger">
                            标记误播报
                          </FaButton>
                          <FaButton type="button" variant="outline" :loading="pilotAction === 'undo'" :disabled="Boolean(pilotAction)" @click="undoFalseTrigger">
                            撤销一次
                          </FaButton>
                        </template>
                        <FaButton type="button" variant="outline" :loading="pilotAction === 'restart'" :disabled="Boolean(pilotAction)" @click="restartPilot">
                          重新开始
                        </FaButton>
                      </template>
                    </div>
                  </div>
                  <FaAlert
                    v-if="!workdayPilot?.started"
                    title="观察尚未开始"
                    description="开始操作只冻结本轮日期、时区和工作日规则；不会删除已有聚合，也不会启用身体动作。"
                  />
                  <template v-else>
                    <div class="gap-4 grid lg:grid-cols-4 sm:grid-cols-2">
                      <FaCard title="当前结论" :description="`${workdayPilot.startedOn} 至 ${workdayPilot.endsOn}`">
                        <p class="text-lg font-semibold">
                          {{ pilotStatusText() }}
                        </p>
                        <p class="text-sm text-muted-foreground mt-1">
                          已观察 {{ workdayPilot.elapsedDays }}/14 天
                        </p>
                      </FaCard>
                      <FaCard title="使用进度" :description="`已走过 ${workdayPilot.elapsedPlannedWorkdays}/${workdayPilot.plannedWorkdays} 个计划工作日`">
                        <p class="text-lg font-semibold">
                          {{ workdayPilot.activeWorkdays }}/{{ workdayPilot.activeWorkdayTarget }} 个活跃工作日
                        </p>
                        <p class="text-sm text-muted-foreground mt-1">
                          每日最多 {{ workdayPilot.maximumDailyBriefs }} 次简报
                        </p>
                      </FaCard>
                      <FaCard title="打扰质量" :description="`每周上限 ${workdayPilot.falseTriggerWeeklyLimit} 次`">
                        <p class="text-lg font-semibold">
                          {{ workdayPilot.firstWeekFalseTriggers }} / {{ workdayPilot.secondWeekFalseTriggers }} 次
                        </p>
                        <p class="text-sm text-muted-foreground mt-1">
                          日历失败 {{ workdayPilot.calendarFailureCount }} · 天气失败 {{ workdayPilot.weatherFailureCount }}
                        </p>
                      </FaCard>
                      <FaCard title="设备安全" description="安全拒绝只记录，不算动作失败。">
                        <p class="text-lg font-semibold">
                          失败 {{ workdayPilot.motionFailedCount }} · 重启 {{ workdayPilot.deviceRestartCount }}
                        </p>
                        <p class="text-sm text-muted-foreground mt-1">
                          动作安全拒绝 {{ workdayPilot.motionRejectedCount }} 次
                        </p>
                      </FaCard>
                    </div>
                    <FaAlert
                      :title="workdayPilot.status === 'PASS' ? '工程门槛通过，陪伴体验仍需实际使用判断' : workdayPilot.status === 'FAIL' ? '观察结束，但至少一项门槛未通过' : '正在收集本地匿名聚合'"
                      :description="workdayPilot.externalFailureAttributionComplete ? '日历和天气失败均保留受控来源与失败码；不保存日程、天气响应或播报正文。' : '存在未归属的外部服务失败，请先排查再判断门槛。'"
                    />
                  </template>
                </div>
              </div>
            </FaCard>
          </template>

          <template #workday-rules>
            <FaCard title="工作规则" description="定义工作日、专注与休息节奏；运行仍需你在当天显式开始。">
              <div class="gap-6 grid">
                <FaFormItem name="workdayEnabled" label="启用工作日模式" description="按所选工作日、时段和设备存在状态运行；默认关闭。">
                  <FaSwitch v-model="model.workdayEnabled" />
                </FaFormItem>
                <div>
                  <div class="text-sm font-medium mb-3">
                    工作日
                  </div>
                  <div class="gap-4 grid grid-cols-2 lg:grid-cols-7 sm:grid-cols-4">
                    <FaFormItem name="workdayMonday" label="周一">
                      <FaSwitch v-model="model.workdayMonday" />
                    </FaFormItem>
                    <FaFormItem name="workdayTuesday" label="周二">
                      <FaSwitch v-model="model.workdayTuesday" />
                    </FaFormItem>
                    <FaFormItem name="workdayWednesday" label="周三">
                      <FaSwitch v-model="model.workdayWednesday" />
                    </FaFormItem>
                    <FaFormItem name="workdayThursday" label="周四">
                      <FaSwitch v-model="model.workdayThursday" />
                    </FaFormItem>
                    <FaFormItem name="workdayFriday" label="周五">
                      <FaSwitch v-model="model.workdayFriday" />
                    </FaFormItem>
                    <FaFormItem name="workdaySaturday" label="周六">
                      <FaSwitch v-model="model.workdaySaturday" />
                    </FaFormItem>
                    <FaFormItem name="workdaySunday" label="周日">
                      <FaSwitch v-model="model.workdaySunday" />
                    </FaFormItem>
                  </div>
                </div>
                <div class="gap-x-8 gap-y-6 grid grid-cols-1 lg:grid-cols-4 md:grid-cols-2">
                  <FaFormItem name="workStart" label="工作开始" required>
                    <FaInput v-model="model.workStart" type="time" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="workEnd" label="工作结束" required>
                    <FaInput v-model="model.workEnd" type="time" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="focusMinutes" label="专注分钟" required description="15–180 分钟。">
                    <FaNumberField v-model="model.focusMinutes" :min="15" :max="180" :step="5" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="restMinutes" label="休息分钟" required description="5–60 分钟。">
                    <FaNumberField v-model="model.restMinutes" :min="5" :max="60" :step="5" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="absenceSuspendMinutes" label="离开后暂停（分钟）" required description="连续离开达到阈值才暂停节奏。">
                    <FaNumberField v-model="model.absenceSuspendMinutes" :min="1" :max="60" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="rearrivalMinutes" label="返场判定（分钟）" required description="超过此时长后回来才算一次返场。">
                    <FaNumberField v-model="model.rearrivalMinutes" :min="5" :max="240" :step="5" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="workdayZoneId" label="工作日时区" required description="使用 IANA 时区处理跨日。">
                    <FaInput v-model="model.workdayZoneId" class="w-full" />
                  </FaFormItem>
                </div>
              </div>
            </FaCard>
          </template>

          <template #connections>
            <FaCard title="日历与天气" description="外部数据只用于当天简报与打扰抑制，正文不会进入观察聚合。">
              <div class="gap-6 grid">
                <div class="pt-6 border-t gap-x-8 gap-y-6 grid grid-cols-1 md:grid-cols-3">
                  <FaFormItem name="locationName" label="天气位置名称" description="仅在管理页面配置，例如“上海办公室”。">
                    <FaInput v-model="model.locationName" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="latitude" label="纬度" description="与经度同时填写；留空则不启用天气。">
                    <FaInput v-model="model.latitude" type="text" inputmode="decimal" placeholder="例如 31.2304" class="w-full" />
                  </FaFormItem>
                  <FaFormItem name="longitude" label="经度" description="数据源固定为 Open-Meteo。">
                    <FaInput v-model="model.longitude" type="text" inputmode="decimal" placeholder="例如 121.4737" class="w-full" />
                  </FaFormItem>
                </div>
                <div class="pt-6 border-t gap-5 grid">
                  <FaAlert
                    title="Open-Meteo 固定位置天气"
                    description="使用当前填写的位置可先做无保存测试；保存设置后再手动同步。服务端每小时刷新一次，只缓存当前及今明两天的必要字段。"
                  />
                  <div class="flex flex-wrap gap-3 items-center justify-between">
                    <div class="flex gap-3 items-start">
                      <div class="text-primary rounded-lg bg-primary/10 flex shrink-0 size-11 items-center justify-center">
                        <FaIcon :name="weatherIcon(workdayWeather?.current?.weatherCode)" class="text-2xl" />
                      </div>
                      <div>
                        <div class="font-medium flex flex-wrap gap-1.5 items-center">
                          <FaIcon :name="weatherStatusIcon()" :class="workdayWeather?.status === 'ERROR' ? 'text-destructive' : 'text-emerald-600'" />
                          {{ weatherStatusText() }}
                          <span v-if="workdayWeather?.locationName" class="text-muted-foreground font-normal"> · {{ workdayWeather.locationName }}</span>
                        </div>
                        <div class="text-sm text-muted-foreground mt-1">
                          <span v-if="weatherFailureText()">{{ weatherFailureText() }} · </span>
                          <span v-if="workdayWeather?.lastSyncedAt">上次同步 {{ new Date(workdayWeather.lastSyncedAt).toLocaleString() }}</span>
                          <span v-else>尚无天气缓存</span>
                          <span v-if="workdayWeather?.cacheExpiresAt"> · 有效至 {{ new Date(workdayWeather.cacheExpiresAt).toLocaleString() }}</span>
                        </div>
                        <p v-if="workdayWeather?.summary" class="text-sm mt-2">
                          {{ workdayWeather.summary }}
                        </p>
                        <div v-if="workdayWeather?.current" class="text-xs mt-3 flex flex-wrap gap-2">
                          <span class="px-2.5 py-1 rounded-full bg-muted flex gap-1 items-center"><FaIcon name="i-ri:temp-hot-line" />{{ workdayWeather.current.temperature }}℃</span>
                          <span class="px-2.5 py-1 rounded-full bg-muted flex gap-1 items-center"><FaIcon name="i-ri:body-scan-line" />体感 {{ workdayWeather.current.apparentTemperature }}℃</span>
                          <span class="px-2.5 py-1 rounded-full bg-muted flex gap-1 items-center"><FaIcon name="i-ri:drizzle-line" />降水 {{ workdayWeather.current.precipitation }} mm</span>
                        </div>
                      </div>
                    </div>
                    <a href="https://open-meteo.com/" target="_blank" rel="noreferrer" class="text-sm text-primary underline-offset-4 hover:underline">
                      天气数据：Open-Meteo
                    </a>
                  </div>
                  <div class="flex flex-wrap gap-3">
                    <FaButton type="button" variant="outline" :loading="weatherAction === 'test'" @click="testWeather">
                      测试当前填写位置
                    </FaButton>
                    <FaButton
                      type="button"
                      :disabled="!workdayWeather?.configured"
                      :loading="weatherAction === 'sync'"
                      @click="syncWeather"
                    >
                      同步已保存位置
                    </FaButton>
                  </div>
                </div>
                <div class="pt-6 border-t gap-5 grid">
                  <FaAlert
                    title="iCloud 日历只读接入"
                    description="仅使用 CalDAV 读取你允许的日历；请使用 Apple Account 中已验证的电子邮件地址；App 专用密码加密保存且永不回显。"
                  />
                  <div class="flex flex-wrap gap-3 items-center justify-between">
                    <div>
                      <div class="font-medium">
                        {{ calendarStatusText() }}
                        <span v-if="icloudConnection?.account" class="text-muted-foreground font-normal"> · {{ icloudConnection.account }}</span>
                      </div>
                      <div class="text-sm text-muted-foreground mt-1">
                        <span v-if="calendarFailureText()">{{ calendarFailureText() }} · </span>
                        已缓存 {{ icloudConnection?.cachedEventCount ?? 0 }} 条日程
                        <span v-if="icloudConnection?.lastSyncedAt"> · 上次同步 {{ new Date(icloudConnection.lastSyncedAt).toLocaleString() }}</span>
                      </div>
                    </div>
                    <FaButton
                      v-if="icloudConnection?.configured"
                      type="button"
                      variant="destructive"
                      :loading="calendarAction === 'disconnect'"
                      @click="disconnectCalendar"
                    >
                      断开并清除本地数据
                    </FaButton>
                  </div>
                  <div class="gap-x-8 gap-y-5 grid grid-cols-1 md:grid-cols-2">
                    <FaFormItem name="icloudAccountEmail" :auto-bind="false" label="Apple 账号邮箱" description="必须是 Apple Account“登录与安全性”中已登记并验证的电子邮件地址。">
                      <FaInput v-model="icloudAccountEmail" type="email" autocomplete="username" class="w-full" />
                    </FaFormItem>
                    <FaFormItem name="icloudAppSpecificPassword" :auto-bind="false" label="App 专用密码" description="不是 Apple 账号登录密码；已连接时留空表示不更换。">
                      <FaInput
                        v-model="icloudAppSpecificPassword"
                        type="password"
                        autocomplete="new-password"
                        class="w-full"
                      />
                    </FaFormItem>
                  </div>
                  <div class="flex flex-wrap gap-3">
                    <FaButton type="button" variant="outline" :loading="calendarAction === 'test'" @click="testCalendar">
                      测试连接
                    </FaButton>
                    <FaButton type="button" :loading="calendarAction === 'connect'" @click="connectCalendar">
                      {{ icloudConnection?.configured ? '重新发现日历' : '连接并发现日历' }}
                    </FaButton>
                    <FaButton
                      type="button"
                      variant="outline"
                      :disabled="!icloudConnection?.configured"
                      :loading="calendarAction === 'sync'"
                      @click="syncCalendar"
                    >
                      同步未来 7 天
                    </FaButton>
                  </div>
                  <div v-if="icloudConnection?.calendars.length" class="gap-3 grid">
                    <div class="text-sm font-medium">
                      允许机器人读取的日历
                    </div>
                    <div class="gap-3 grid grid-cols-1 md:grid-cols-2">
                      <div
                        v-for="calendar in icloudConnection.calendars"
                        :key="calendar.id"
                        class="p-3 border rounded-md flex items-center justify-between"
                      >
                        <span class="text-sm">{{ calendar.displayName }}</span>
                        <FaSwitch v-model="calendar.allowed" />
                      </div>
                    </div>
                    <div>
                      <FaButton type="button" variant="outline" :loading="calendarAction === 'save'" @click="saveAllowedCalendars">
                        保存日历白名单
                      </FaButton>
                    </div>
                  </div>
                  <div v-if="icloudConnection?.configured" class="pt-5 border-t gap-3 grid">
                    <div class="flex flex-wrap gap-3 items-center justify-between">
                      <div>
                        <div class="font-medium flex gap-2 items-center">
                          <FaIcon name="i-ri:calendar-event-line" class="text-primary" />
                          未来 7 天日程
                        </div>
                        <div class="text-sm text-muted-foreground mt-1">
                          仅展示服务端已缓存的只读日程，不会在此处修改 iCloud。
                        </div>
                      </div>
                      <FaButton type="button" size="sm" variant="outline" :loading="calendarEventsLoading" @click="loadCalendarEvents()">
                        <FaIcon name="i-ri:refresh-line" />刷新列表
                      </FaButton>
                    </div>
                    <AppLoading :loading="calendarEventsLoading">
                      <FaAlert v-if="calendarEventsError" title="日程未获取" :description="calendarEventsError" />
                      <AppEmpty v-else-if="!calendarEvents.length" description="未来 7 天暂无已缓存日程" />
                      <div v-else class="gap-2 grid">
                        <div
                          v-for="event in calendarEvents"
                          :key="`${event.startsAt}-${event.endsAt}-${event.title}`"
                          class="p-3 border rounded-lg gap-3 grid sm:grid-cols-[150px_minmax(0,1fr)]"
                        >
                          <div class="text-sm">
                            <div class="font-medium">
                              {{ calendarEventDate(event) }}
                            </div>
                            <div class="text-muted-foreground mt-1 flex gap-1 items-center">
                              <FaIcon name="i-ri:time-line" />{{ calendarEventTime(event) }}
                            </div>
                          </div>
                          <div class="min-w-0">
                            <div class="font-medium flex flex-wrap gap-2 items-center">
                              <span class="truncate">{{ event.title || (event.privateEvent ? '私人日程' : '未命名日程') }}</span>
                              <FaTag v-if="event.busy" variant="secondary">
                                忙碌
                              </FaTag>
                            </div>
                            <div v-if="event.location" class="text-sm text-muted-foreground mt-1 flex gap-1 items-center">
                              <FaIcon name="i-ri:map-pin-line" />{{ event.location }}
                            </div>
                          </div>
                        </div>
                      </div>
                    </AppLoading>
                  </div>
                </div>
              </div>
            </FaCard>
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
