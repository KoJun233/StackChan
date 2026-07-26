<script setup lang="ts">
import eventBus from '@/utils/eventBus'
import DetailForm from './components/DetailForm/index.vue'

defineOptions({ name: 'CompanionMemoryDetail' })

const route = useRoute()
const router = useRouter()
const formRef = useTemplateRef<InstanceType<typeof DetailForm>>('formRef')

function onSubmit() {
  formRef.value?.submit().then((success) => {
    if (!success) {
      return
    }
    eventBus.emit('get-memory-list')
    onCancel()
  })
}

function onCancel() {
  router.back({ name: 'companionMemoryList' })
}
</script>

<template>
  <div>
    <FaFixedBar position="top" class="p-0">
      <FaPageHeader :title="route.params.id ? '编辑长期记忆' : '新增长期记忆'" class="mb-0 border-b-none">
        <FaButton variant="outline" size="sm" class="rounded-full" @click="onCancel">
          <FaIcon name="i-ep:arrow-left" />
          返回
        </FaButton>
      </FaPageHeader>
    </FaFixedBar>
    <FaPageMain>
      <div class="max-w-4xl w-full">
        <DetailForm :id="(route.params.id as string) || ''" ref="formRef" />
      </div>
    </FaPageMain>
    <FaFixedBar position="bottom" class="flex-center gap-4">
      <FaButton @click="onSubmit">
        提交
      </FaButton>
      <FaButton variant="outline" @click="onCancel">
        取消
      </FaButton>
    </FaFixedBar>
  </div>
</template>
