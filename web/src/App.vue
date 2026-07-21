<script setup lang="ts">
import AppShell from './components/AppShell.vue'
import DeviceOverview from './components/DeviceOverview.vue'
import LlmSettingsPanel from './components/LlmSettingsPanel.vue'
import PairingPanel from './components/PairingPanel.vue'
</script>

<template>
  <AppShell>
    <main class="console-main">
      <DeviceOverview />
      <LlmSettingsPanel />
      <PairingPanel />
    </main>
  </AppShell>
</template>
