<template>
  <div class="home">
    <h1>Hify</h1>
    <p>Simplified internal AI Agent platform</p>
    <el-button type="primary" @click="checkHealth">Health Check</el-button>
    <p v-if="healthResult">{{ healthResult }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { get } from '@/utils/request'

const healthResult = ref('')

const checkHealth = async () => {
  try {
    const res = await get<string>('/health')
    healthResult.value = res
  } catch (e) {
    healthResult.value = 'Error: ' + e
  }
}
</script>

<style scoped>
.home {
  text-align: center;
  padding: 2rem;
}
</style>
