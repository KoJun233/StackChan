<script setup lang="ts">
import { Copy } from 'lucide-vue-next'
import { ref } from 'vue'
import { createPairingCode } from '../lib/api'

const createdBy = ref('')
const pairingCode = ref('')
const errorMessage = ref('')
const isCreating = ref(false)
const copyState = ref<'idle' | 'copied' | 'failed'>('idle')
const pairingCodeInput = ref<HTMLInputElement | null>(null)

async function createCode() {
  pairingCode.value = ''
  errorMessage.value = ''
  copyState.value = 'idle'
  isCreating.value = true
  try {
    pairingCode.value = await createPairingCode(createdBy.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法创建配对码。'
  } finally {
    isCreating.value = false
  }
}

async function copyCode() {
  if (!pairingCode.value) {
    return
  }

  let copied = false
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(pairingCode.value)
      copied = true
    }
  } catch {
    copied = false
  }

  if (!copied) {
    copied = copyWithSelection()
  }
  copyState.value = copied ? 'copied' : 'failed'
}

function copyWithSelection(): boolean {
  const input = pairingCodeInput.value
  if (!input || typeof document.execCommand !== 'function') {
    return false
  }

  input.focus()
  input.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  }
}
</script>

<template>
  <section id="pairing" class="console-section pairing-section" aria-labelledby="pairing-heading">
    <div class="section-heading">
      <div>
        <p class="eyebrow">设备管理</p>
        <h2 id="pairing-heading">设备配对</h2>
      </div>
    </div>

    <form class="pairing-form" @submit.prevent="createCode">
      <label class="input-label" for="created-by">创建人</label>
      <div class="pairing-controls">
        <input id="created-by" v-model="createdBy" class="text-input" maxlength="80" autocomplete="name" />
        <button class="primary-button" type="submit" :disabled="isCreating">
          {{ isCreating ? '正在创建…' : '创建配对码' }}
        </button>
      </div>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    </form>

    <div v-if="pairingCode" class="code-result" aria-live="polite">
      <span class="code-label">新配对码</span>
      <div class="code-control">
        <input ref="pairingCodeInput" :value="pairingCode" aria-label="配对码" readonly />
        <button class="icon-button" type="button" aria-label="复制配对码" title="复制配对码" @click="copyCode">
          <Copy :size="16" aria-hidden="true" />
        </button>
      </div>
      <span v-if="copyState === 'copied'" class="copy-status">已复制</span>
      <span v-else-if="copyState === 'failed'" class="copy-status failed" role="alert">复制失败，请选中配对码后手动复制。</span>
    </div>
  </section>
</template>
