<script setup lang="ts">
import DetailForm from './components/DetailForm/index.vue'

defineOptions({ name: 'CompanionRoleDetail' })
const route = useRoute()
const router = useRouter()
const formRef = useTemplateRef<InstanceType<typeof DetailForm>>('formRef')
async function submit() { if (await formRef.value?.submit()) router.back({ name: 'companionPersona' }) }
</script>

<template>
  <div>
    <FaFixedBar position="top" class="p-0"><FaPageHeader :title="route.params.id ? '编辑角色' : '新增角色'" class="mb-0 border-b-none"><FaButton variant="outline" size="sm" class="rounded-full" @click="router.back({ name: 'companionPersona' })"><FaIcon name="i-ep:arrow-left" />返回</FaButton></FaPageHeader></FaFixedBar>
    <FaPageMain><div class="max-w-4xl w-full"><DetailForm ref="formRef" :id="String(route.params.id || '')" /></div></FaPageMain>
    <FaFixedBar position="bottom" class="flex-center gap-4"><FaButton @click="submit">提交</FaButton><FaButton variant="outline" @click="router.back({ name: 'companionPersona' })">取消</FaButton></FaFixedBar>
  </div>
</template>
