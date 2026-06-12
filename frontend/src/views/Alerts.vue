<script setup>
import { ref, reactive, onMounted } from 'vue';
import http from '../api';
import LineChart from '../components/LineChart.vue';
const page = ref(1); const data = ref({ records: [] }); const err = ref('');
const f = reactive({ status: '', severity: '', serviceName: '' });
const detail = ref(null); const rt = ref([]);
async function load() {
  err.value = '';
  try {
    const qs = new URLSearchParams({ page: page.value, size: 15 });
    Object.entries(f).forEach(([k, v]) => v && qs.set(k, v));
    data.value = await http.get('/alerts?' + qs.toString());
  } catch (e) { err.value = e.message; }
}
async function open(id) {
  detail.value = await http.get('/alerts/' + id); rt.value = [];
  const hid = detail.value.alert.hzbMonitorId;
  if (hid) {
    try {
      const d = await http.get('/hertzbeat/monitors/' + hid + '/response-time?history=6h');
      const series = Object.values((d && d.values) || {})[0] || [];
      rt.value = series.map((it, i) => ({ label: it.time || i, value: +(it.origin ?? it.value ?? it.mean) })).filter((p) => !isNaN(p.value));
    } catch (e) { /* ignore */ }
  }
}
async function ack(id) {
  const by = prompt('认领人:', localStorage.getItem('acker') || ''); if (by === null) return;
  localStorage.setItem('acker', by);
  const remark = prompt('备注(可选):', '') || '';
  await http.post('/alerts/' + id + '/ack', { ackedBy: by, remark });
  await open(id); await load();
}
function go(d) { page.value = Math.max(1, page.value + d); load(); }
onMounted(load);
</script>
<template>
  <div class="toolbar">
    <select v-model="f.status" @change="page=1;load()"><option value="">全部状态</option><option>firing</option><option>recovered</option></select>
    <select v-model="f.severity" @change="page=1;load()"><option value="">全部等级</option><option>P0</option><option>P1</option><option>P2</option><option>P3</option></select>
    <input v-model="f.serviceName" placeholder="服务名" @keyup.enter="page=1;load()" />
    <button class="ghost" @click="load">刷新</button>
  </div>
  <div v-if="err" class="err">{{ err }}</div>
  <table>
    <thead><tr><th>ID</th><th>状态</th><th>等级</th><th>服务</th><th>监控</th><th>内容</th><th>标记</th></tr></thead>
    <tbody>
      <tr v-for="a in data.records" :key="a.id" style="cursor:pointer" @click="open(a.id)">
        <td>{{ a.id }}</td>
        <td><span class="badge" :class="'b-'+a.status">{{ a.status }}</span></td>
        <td><span class="badge" :class="'b-'+a.severity">{{ a.severity }}</span></td>
        <td>{{ a.serviceName }}</td><td>{{ a.monitorName }}</td>
        <td>{{ (a.content||'').slice(0,40) }}</td>
        <td>
          <span v-if="a.escalated==1" class="badge b-P1">已升级</span>
          <span v-if="a.ackedBy" class="badge b-P2">已认领</span>
        </td>
      </tr>
      <tr v-if="!data.records.length"><td colspan="7" class="empty">暂无告警</td></tr>
    </tbody>
  </table>
  <div class="toolbar" style="margin-top:12px">
    <button class="ghost" :disabled="page<=1" @click="go(-1)">上一页</button>
    <span class="muted">第 {{ data.current||page }}/{{ data.pages||1 }} 页 · 共 {{ data.total||0 }} 条</span>
    <button class="ghost" :disabled="(data.current||1)>=(data.pages||1)" @click="go(1)">下一页</button>
  </div>

  <div v-if="detail" class="form" style="margin-top:16px;max-width:100%">
    <h2>告警 #{{ detail.alert.id }} 详情 <button class="ghost" style="float:right" @click="detail=null">关闭</button></h2>
    <div class="muted">服务 {{ detail.alert.serviceName }} · 监控 {{ detail.alert.monitorName }} · {{ detail.alert.url }}</div>
    <p>状态 <b>{{ detail.alert.status }}</b> · 等级 {{ detail.alert.severity }} · 内容：{{ detail.alert.content }}</p>
    <p>首次 {{ detail.alert.firstSeen }} · 恢复 {{ detail.alert.recoveredAt || '-' }} · 持续 {{ detail.alert.durationSec ?? '-' }}s · 升级 {{ detail.alert.escalated==1?'是':'否' }}</p>
    <p>认领：{{ detail.alert.ackedBy ? detail.alert.ackedBy + ' @ ' + detail.alert.ackedAt : '未认领' }} · 备注：{{ detail.alert.remark || '-' }}
      <button class="act" style="margin-left:8px" @click="ack(detail.alert.id)">认领/备注</button></p>
    <h2>响应时间(近6h)</h2>
    <LineChart v-if="rt.length" :points="rt" suffix="ms" />
    <div v-else class="empty">暂无响应时间数据（监控不可达或无历史）</div>
    <h2>通知回执</h2>
    <table><thead><tr><th>渠道</th><th>结果</th><th>接收人</th><th>时间</th></tr></thead>
      <tbody>
        <tr v-for="l in detail.notifyLogs" :key="l.id"><td>{{ l.channelType }}</td>
          <td>{{ l.success==1?'✅':'❌' }} {{ l.errorMsg||'' }}</td><td>{{ l.receiver }}</td><td>{{ l.sentAt }}</td></tr>
        <tr v-if="!detail.notifyLogs.length"><td colspan="4" class="empty">无</td></tr>
      </tbody>
    </table>
  </div>
</template>
