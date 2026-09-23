<script setup lang="ts">
import eventBus from '@/utils/eventBus'
import DetailForm from './components/DetailForm/index.vue'

defineOptions({ name: 'ReminderDetail' })

const route = useRoute()
const router = useRouter()
const formRef = useTemplateRef<InstanceType<typeof DetailForm>>('formRef')

function onSubmit() {
  formRef.value?.submit().then((success) => {
    if (!success) {
      return
    }
    eventBus.emit('get-data-list')
    onCancel()
  })
}

function onCancel() {
  router.back({ name: 'reminderList' })
}
</script>

<template>
  <AppPageShell :title="route.params.id ? '编辑提醒' : '新增提醒'" width="form">
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
