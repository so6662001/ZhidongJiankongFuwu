<script setup>
import { ref, onMounted } from 'vue';
import http from '../api';
const kw = ref(''); const page = ref(1); const data = ref({ records: [] }); const err = ref('');
async function load() {
  err.value = '';
  try {
    const qs = new URLSearchParams({ page: page.value, size: 15 });
    if (kw.value) qs.set('keyword', kw.value);
    data.value = await http.get('/monitors?' + qs.toString());
  } catch (e) { err.value = e.message; }
}
function go(d) { page.value = Math.max(1, page.value + d); load(); }
onMounted(load);
</script>
<template>
  <div class="toolbar"><input v-model="kw" placeholder="名称/URL 关键字" @keyup.enter="page=1;load()" /><button class="act" @click="page=1;load()">搜索</button></div>
  <div v-if="err" class="err">{{ err }}</div>
  <table><thead><tr><th>ID</th><th>名称</th><th>服务</th><th>等级</th><th>方法</th><th>URL</th><th>HZB</th></tr></thead>
    <tbody>
      <tr v-for="m in data.records" :key="m.id"><td>{{ m.id }}</td><td>{{ m.name }}</td><td>{{ m.serviceName }}</td>
        <td><span class="badge" :class="'b-'+m.severity">{{ m.severity }}</span></td><td>{{ m.method }}</td><td>{{ m.url }}</td><td>{{ m.hzbMonitorId }}</td></tr>
      <tr v-if="!data.records.length"><td colspan="7" class="empty">暂无监控项</td></tr>
    </tbody>
  </table>
  <div class="toolbar" style="margin-top:12px">
    <button class="ghost" :disabled="page<=1" @click="go(-1)">上一页</button>
    <span class="muted">第 {{ data.current||page }}/{{ data.pages||1 }} 页 · 共 {{ data.total||0 }} 条</span>
    <button class="ghost" :disabled="(data.current||1)>=(data.pages||1)" @click="go(1)">下一页</button>
  </div>
</template>
