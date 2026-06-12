<script setup>
import { ref, reactive, onMounted } from 'vue';
import http from '../api';
const list = ref([]); const err = ref(''); const current = ref(null);
const form = reactive({ name: '', serviceName: '', wecomUserids: '', emailList: '', startAt: '', endAt: '' });
async function load() { err.value=''; try { list.value = await http.get('/oncall/schedules'); } catch (e) { err.value = e.message; } }
async function save() {
  try {
    const body = { ...form }; body.startAt = form.startAt.replace(' ', 'T'); body.endAt = form.endAt.replace(' ', 'T');
    if (!body.serviceName) delete body.serviceName;
    await http.post('/oncall/schedules', body); load();
  } catch (e) { err.value = e.message; }
}
async function del(id) { await http.delete('/oncall/schedules/' + id); load(); }
async function showCurrent() { current.value = await http.get('/oncall/current' + (form.serviceName ? '?serviceName=' + encodeURIComponent(form.serviceName) : '')); }
onMounted(load);
</script>
<template>
  <div class="form" style="margin-bottom:16px">
    <div class="row">
      <label>排班名<input v-model="form.name" placeholder="本周值班" /></label>
      <label>服务名(空=全局)<input v-model="form.serviceName" /></label>
    </div>
    <div class="row">
      <label>企业微信 userid(逗号)<input v-model="form.wecomUserids" /></label>
      <label>邮件(逗号)<input v-model="form.emailList" /></label>
    </div>
    <div class="row">
      <label>开始<input v-model="form.startAt" placeholder="2026-06-12 09:00:00" /></label>
      <label>结束<input v-model="form.endAt" placeholder="2026-06-13 09:00:00" /></label>
    </div>
    <button class="act" @click="save">新增排班</button>
    <button class="ghost" style="margin-left:8px" @click="showCurrent">查当前当班</button>
    <div v-if="current" class="muted" style="margin-top:8px">当前当班 — 企业微信: {{ current.wecomUserids.join(', ')||'无' }} ｜ 邮件: {{ current.emails.join(', ')||'无' }}</div>
  </div>
  <div v-if="err" class="err">{{ err }}</div>
  <table><thead><tr><th>ID</th><th>名称</th><th>服务</th><th>企业微信</th><th>邮件</th><th>开始</th><th>结束</th><th>操作</th></tr></thead>
    <tbody>
      <tr v-for="s in list" :key="s.id"><td>{{ s.id }}</td><td>{{ s.name }}</td><td>{{ s.serviceName||'全局' }}</td>
        <td>{{ s.wecomUserids }}</td><td>{{ s.emailList }}</td><td>{{ s.startAt }}</td><td>{{ s.endAt }}</td>
        <td><button class="ghost" @click="del(s.id)">删除</button></td></tr>
      <tr v-if="!list.length"><td colspan="8" class="empty">暂无排班</td></tr>
    </tbody>
  </table>
</template>
