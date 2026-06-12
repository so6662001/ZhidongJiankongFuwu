<script setup>
import { reactive, ref } from 'vue';
import http from '../api';
const form = reactive({ serviceName: '', wecomUserids: '', emails: '' });
const result = ref(''); const err = ref('');
async function send() {
  err.value=''; result.value='发送中…';
  const body = {};
  if (form.serviceName) body.serviceName = form.serviceName;
  if (form.wecomUserids) body.wecomUserids = form.wecomUserids;
  if (form.emails) body.emails = form.emails;
  try { const d = await http.post('/notify/test', body); result.value = JSON.stringify(d, null, 2); }
  catch (e) { err.value = e.message; result.value=''; }
}
</script>
<template>
  <div class="form">
    <p class="muted">验证企业微信/邮件投递与兜底切换。按服务负责人路由，或直接指定接收人。</p>
    <div class="row"><label>按服务负责人路由<input v-model="form.serviceName" placeholder="order-service" /></label></div>
    <div class="row">
      <label>或 企业微信 userid(逗号)<input v-model="form.wecomUserids" /></label>
      <label>或 邮件(逗号)<input v-model="form.emails" /></label>
    </div>
    <button class="act" @click="send">发送测试通知</button>
    <div v-if="err" class="err">{{ err }}</div>
    <div class="result" v-if="result">{{ result }}</div>
  </div>
</template>
