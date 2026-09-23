<script setup lang="ts">
import eventBus from '@/utils/eventBus'
import DetailForm from './components/DetailForm/index.vue'

defineOptions({ name: 'NotificationIntegrationDetail' })
const route = useRoute()
const router = useRouter()
const formRef = useTemplateRef<InstanceType<typeof DetailForm>>('formRef')
function onSubmit() {
  formRef.value?.submit().then((success) => {
    if (!success) {
      return
    }
    eventBus.emit('get-notification-integrations')
    onCancel()
  })
}
function onCancel() {
  router.back({ name: 'notificationIntegrationList' })
}
</script>

<template>
  <AppPageShell :title="route.params.id ? '编辑通知集成' : '新增通知集成'" width="form">
    <template #actions>
      <FaButton variant="outline" size="sm" class="rounded-full" @click="onCancel">
        <FaIcon name="i-ep:arrow-left" />
        返回
      </FaButton>
    </template>
    <div class="w-full">
      <DetailForm :id="(route.params.id as string) || ''" ref="formRef" />
    </div>

    <FaFixedBar position="bottom" class="flex-center gap-4">
      <FaButton @click="onSubmit">
        提交
      </FaButton>
      <FaButton variant="outline" @click="onCancel">
        取消
      </FaButton>
    </FaFixedBar>
  </AppPageShell>
</template>
