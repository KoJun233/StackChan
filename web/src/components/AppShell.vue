<script setup lang="ts">
import { BotMessageSquare, LayoutDashboard, Link2 } from 'lucide-vue-next'
import { ref } from 'vue'

const activeSection = ref<'overview' | 'llm-settings' | 'pairing'>('overview')
</script>

<template>
  <div class="app-shell">
    <aside class="side-nav" aria-label="主导航">
      <a class="product-name" href="#overview" @click="activeSection = 'overview'">StackChan</a>
      <nav class="nav-list">
        <a
          href="#overview"
          class="nav-link"
          :class="{ active: activeSection === 'overview' }"
          :aria-current="activeSection === 'overview' ? 'page' : undefined"
          @click="activeSection = 'overview'"
        >
          <LayoutDashboard :size="17" aria-hidden="true" />
          <span>设备概览</span>
        </a>
        <a
          href="#llm-settings"
          class="nav-link"
          :class="{ active: activeSection === 'llm-settings' }"
          :aria-current="activeSection === 'llm-settings' ? 'page' : undefined"
          @click="activeSection = 'llm-settings'"
        >
          <BotMessageSquare :size="17" aria-hidden="true" />
          <span>AI 配置</span>
        </a>
        <a
          href="#pairing"
          class="nav-link"
          :class="{ active: activeSection === 'pairing' }"
          :aria-current="activeSection === 'pairing' ? 'page' : undefined"
          @click="activeSection = 'pairing'"
        >
          <Link2 :size="17" aria-hidden="true" />
          <span>设备与配对</span>
        </a>
      </nav>
    </aside>

    <div class="workspace">
      <header class="mobile-header">
        <a class="product-name" href="#overview" @click="activeSection = 'overview'">StackChan</a>
        <nav class="mobile-nav" aria-label="主导航">
          <a href="#overview" :class="{ active: activeSection === 'overview' }" @click="activeSection = 'overview'">概览</a>
          <a href="#llm-settings" :class="{ active: activeSection === 'llm-settings' }" @click="activeSection = 'llm-settings'">AI 配置</a>
          <a href="#pairing" :class="{ active: activeSection === 'pairing' }" @click="activeSection = 'pairing'">配对</a>
        </nav>
      </header>
      <slot />
    </div>
  </div>
</template>
