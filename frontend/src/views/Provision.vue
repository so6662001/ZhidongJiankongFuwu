<script setup>
import { reactive, ref } from 'vue';
import http from '../api';
const form = reactive({ serviceName: '', severity: 'P2', collector: '', openapiUrl: '', openapiContent: '' });
const result = ref(''); const err = ref('');
async function run(dryRun) {
  err.value=''; result.value = '提交中…';
  const body = { dryRun, severity: form.severity };
  if (form.serviceName) body.serviceName = form.serviceName;
  if (form.collector) body.collector = form.collector;
  if (form.openapiUrl) body.openapiUrl = form.openapiUrl;
  else if (form.openapiContent) body.openapiContent = form.openapiContent;
  else { err.value = '请填写 OpenAPI URL 或内容'; result.value=''; return; }
  try {
    const d = await http.post('/provision/import-openapi', body);
    result.value = `created=${d.created} updated=${d.updated} failed=${d.failed} (dryRun=${d.dryRun})\n` +
      (d.details || []).map((i) => `[${i.action}] ${i.name}  ${i.url}${i.needsReview ? '  ⚠needsReview' : ''}${i.message ? '  ' + i.message : ''}`).join('\n');
  } catch (e) { err.value = e.message; result.value=''; }
}
</script>
<template>
  <div class="form">
    <div class="row">
      <label>服务名(可选)<input v-model="form.serviceName" /></label>
      <label>告警等级<select v-model="form.severity"><option>P2</option><option>P0</option><option>P1</option><option>P3</option></select></label>
      <label>采集器(多探测点,可选)<input v-model="form.collector" placeholder="collector-beijing" /></label>
    </div>
    <div class="row"><label>OpenAPI 文档 URL<input v-model="form.openapiUrl" placeholder="https://svc/v3/api-docs" /></label></div>
    <div class="row"><label>或 OpenAPI 内容<textarea v-model="form.openapiContent" placeholder='{"openapi":"3.0.1",...}'></textarea></label></div>
    <button class="ghost" @click="run(true)">预览(dryRun)</button>
    <button class="act" style="margin-left:8px" @click="run(false)">正式导入</button>
    <div v-if="err" class="err">{{ err }}</div>
    <div class="result" v-if="result">{{ result }}</div>
  </div>
</template>
