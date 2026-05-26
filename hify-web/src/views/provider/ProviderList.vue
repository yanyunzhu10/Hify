<template>
  <div>
    <p :style="{ color: connected ? 'green' : 'red' }">
      {{ connected ? `后端已连接：${healthMsg}` : '后端未连接' }}
    </p>
    <p>模型提供商管理</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api/health'

const connected = ref(false)
const healthMsg = ref('')

onMounted(async () => {
  try {
    healthMsg.value = await getHealth()
    connected.value = true
  } catch {
    connected.value = false
  }
})
</script>
