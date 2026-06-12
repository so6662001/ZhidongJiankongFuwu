<script setup>
import { ref } from 'vue';
const apiKey = ref(localStorage.getItem('apiKey') || '');
const dark = ref(localStorage.getItem('theme') === 'dark');
applyTheme();
function saveKey() { localStorage.setItem('apiKey', apiKey.value.trim()); location.reload(); }
function toggle() { dark.value = !dark.value; localStorage.setItem('theme', dark.value ? 'dark' : 'light'); applyTheme(); }
function applyTheme() { document.body.classList.toggle('dark', dark.value); }
const tabs = [
  ['/overview', '概览'], ['/alerts', '告警'], ['/sla', 'SLA'], ['/monitors', '监控项'],
  ['/owners', '服务负责人'], ['/maintenance', '维护窗口'], ['/oncall', '值班排班'],
  ['/provision', '导入纳管'], ['/cmdb', 'CMDB'], ['/notify', '通知测试'],
];
</script>

<template>
  <header class="topbar">
    <h1>🛰️ API 监控大盘</h1>
    <span class="spacer"></span>
    <button @click="toggle">🌓</button>
    <input v-model="apiKey" placeholder="X-Api-Key" style="width:200px" @keyup.enter="saveKey" />
    <button @click="saveKey">保存</button>
  </header>
  <nav class="tabs">
    <router-link v-for="t in tabs" :key="t[0]" :to="t[0]">{{ t[1] }}</router-link>
  </nav>
  <main><router-view /></main>
</template>
