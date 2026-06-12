<script setup>
import { ref, reactive, onMounted } from 'vue';
import http from '../api';
const list = ref([]); const err = ref(''); const form = reactive({ serviceName: '', wecomUserids: '', emailList: '' });
async function load() { err.value=''; try { list.value = await http.get('/service-owners'); } catch (e) { err.value = e.message; } }
async function save() { try { await http.post('/service-owners', { ...form }); form.serviceName=form.wecomUserids=form.emailList=''; load(); } catch (e) { err.value = e.message; } }
async function del(id) { await http.delete('/service-owners/' + id); load(); }
onMounted(load);
</script>
<template>
  <div class="form" style="margin-bottom:16px">
    <div class="row">
      <label>服务名<input v-model="form.serviceName" placeholder="order-service" /></label>
      <label>企业微信 userid(逗号)<input v-model="form.wecomUserids" placeholder="zhangsan,lisi" /></label>
      <label>邮件(逗号)<input v-model="form.emailList" placeholder="ops@company.com" /></label>
    </div>
    <button class="act" @click="save">保存/更新</button>
  </div>
  <div v-if="err" class="err">{{ err }}</div>
  <table><thead><tr><th>ID</th><th>服务</th><th>企业微信</th><th>邮件</th><th>状态</th><th>操作</th></tr></thead>
    <tbody>
      <tr v-for="o in list" :key="o.id"><td>{{ o.id }}</td><td>{{ o.serviceName }}</td><td>{{ o.wecomUserids }}</td><td>{{ o.emailList }}</td>
        <td>{{ o.enabled==1?'启用':'停用' }}</td><td><button class="ghost" @click="del(o.id)">删除</button></td></tr>
      <tr v-if="!list.length"><td colspan="6" class="empty">暂无配置</td></tr>
    </tbody>
  </table>
</template>
