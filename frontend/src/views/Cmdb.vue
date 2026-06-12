<script setup>
import { reactive, ref } from 'vue';
import http from '../api';
const form = reactive({ url: '', items: '' });
const result = ref(''); const err = ref('');
async function sync() {
  err.value=''; result.value = '同步中…';
  const body = {};
  if (form.url) body.url = form.url;
  else if (form.items) { try { body.items = JSON.parse(form.items); } catch (e) { err.value='items 不是合法 JSON'; result.value=''; return; } }
  else { err.value = '请填写 CMDB URL 或 items'; result.value=''; return; }
  try { const d = await http.post('/cmdb/sync', body); result.value = JSON.stringify(d, null, 2); }
  catch (e) { err.value = e.message; result.value=''; }
}
</script>
<template>
  <div class="form">
    <p class="muted">从 CMDB 同步「服务→负责人」到 service_owner。期望数组元素含 serviceName / wecomUserids / emailList。</p>
    <div class="row"><label>CMDB 拉取 URL<input v-model="form.url" placeholder="https://cmdb/api/services" /></label></div>
    <div class="row"><label>或 内联 items(JSON 数组)<textarea v-model="form.items" placeholder='[{"serviceName":"order-service","wecomUserids":"zhangsan","emailList":"ops@company.com"}]'></textarea></label></div>
    <button class="act" @click="sync">同步</button>
    <div v-if="err" class="err">{{ err }}</div>
    <div class="result" v-if="result">{{ result }}</div>
  </div>
</template>
