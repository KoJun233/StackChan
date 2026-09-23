<script setup lang="ts">
import DetailForm from './components/DetailForm/index.vue'

defineOptions({ name: 'CompanionRoleDetail' })
const route = useRoute()
const router = useRouter()
const formRef = useTemplateRef<InstanceType<typeof DetailForm>>('formRef')
async function submit() {
  if (await formRef.value?.submit()) {
    router.back({ name: 'companionPersona' })
  }
}
</script>

<template>
  <AppPageShell :title="route.params.id ? '编辑伙伴' : '新增伙伴'" width="form">
    <template #actions>
      <FaButton variant="outline" size="sm" class="rounded-full" @click="router.back({ name: 'companionPersona' })">
        <FaIcon name="i-ep:arrow-left" />返回
      </FaButton>
    </template><div class="w-full">
      <DetailForm :id="String(route.params.id || '')" ref="formRef" />
    </div>
    <FaFixedBar position="bottom" class="flex-center gap-4">
      <FaButton @click="submit">
        提交
      </FaButton><FaButton variant="outline" @click="router.back({ name: 'companionPersona' })">
        取消
      </FaButton>
    </FaFixedBar>
  </AppPageShell>
</template>
