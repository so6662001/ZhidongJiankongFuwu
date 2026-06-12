<script setup>
import { ref, reactive, onMounted } from 'vue';
import http from '../api';
const list = ref([]); const err = ref('');
const form = reactive({ name: '', scopeType: 'global', scopeValue: '', startAt: '', endAt: '', reason: '' });
async function load() { err.value=''; try { list.value = await http.get('/maintenance-windows'); } catch (e) { err.value = e.message; } }
async function save() {
  try {
    const body = { ...form };
    body.startAt = toIso(form.startAt); body.endAt = toIso(form.endAt);
    await http.post('/maintenance-windows', body); load();
  } catch (e) { err.value = e.message; }
}
function toIso(v) { return v ? v.replace(' ', 'T') : v; }
async function del(id) { await http.delete('/maintenance-windows/' + id); load(); }
onMounted(load);
</script>
<template>
  <div class="form" style="margin-bottom:16px">
    <div class="row">
      <label>名称<input v-model="form.name" placeholder="发布维护" /></label>
      <label>范围<select v-model="form.scopeType"><option value="global">全局</option><option value="service">服务</option><option value="monitor">监控</option></select></label>
      <label>范围值(服务名/监控名)<input v-model="form.scopeValue" :disabled="form.scopeType==='global'" /></label>
    </div>
    <div class="row">
      <label>开始(YYYY-MM-DD HH:mm:ss)<input v-model="form.startAt" placeholder="2026-06-12 10:00:00" /></label>
      <label>结束<input v-model="form.endAt" placeholder="2026-06-12 12:00:00" /></label>
      <label>原因<input v-model="form.reason" /></label>
    </div>
    <button class="act" @click="save">新增维护窗口</button>
  </div>
  <div v-if="err" class="err">{{ err }}</div>
  <table><thead><tr><th>ID</th><th>名称</th><th>范围</th><th>开始</th><th>结束</th><th>原因</th><th>操作</th></tr></thead>
    <tbody>
      <tr v-for="w in list" :key="w.id"><td>{{ w.id }}</td><td>{{ w.name }}</td><td>{{ w.scopeType }} {{ w.scopeValue||'' }}</td>
        <td>{{ w.startAt }}</td><td>{{ w.endAt }}</td><td>{{ w.reason }}</td><td><button class="ghost" @click="del(w.id)">删除</button></td></tr>
      <tr v-if="!list.length"><td colspan="7" class="empty">暂无维护窗口</td></tr>
    </tbody>
  </table>
</template>
