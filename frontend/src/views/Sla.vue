<script setup>
import { ref, onMounted } from 'vue';
import http from '../api';
import LineChart from '../components/LineChart.vue';
const svc = ref(''); const hours = ref(24); const sla = ref({ monitors: [] }); const trend = ref([]); const err = ref('');
async function load() {
  err.value = '';
  try {
    const q = (svc.value ? '&serviceName=' + encodeURIComponent(svc.value) : '');
    sla.value = await http.get('/stats/sla?hours=' + hours.value + q);
    const t = await http.get('/stats/sla-trend?hours=' + hours.value + '&buckets=24' + q);
    trend.value = (t.points || []).map((p) => ({ label: p.time, value: +p.availability }));
  } catch (e) { err.value = e.message; }
}
function avClass(v) { return v >= 99.9 ? 'av-ok' : (v >= 99 ? 'av-warn' : 'av-crit'); }
onMounted(load);
</script>
<template>
  <div class="toolbar">
    <input v-model="svc" placeholder="服务名(可选)" />
    <label class="muted">窗口(h) <input v-model.number="hours" type="number" style="width:64px" /></label>
    <button class="act" @click="load">计算 SLA</button>
  </div>
  <div v-if="err" class="err">{{ err }}</div>
  <div class="cards">
    <div class="card"><div class="num">{{ sla.worstAvailability ?? '-' }}%</div><div class="label">最差可用率</div></div>
    <div class="card"><div class="num">{{ sla.monitorCountWithIncident ?? 0 }}</div><div class="label">有故障监控数</div></div>
  </div>
  <h2>整体可用率趋势</h2>
  <LineChart :points="trend" :min="Math.floor(Math.min(95, ...(trend.map(p=>p.value).concat([100]))))" :max="100" suffix="%" :dp="1" />
  <h2>各监控可用率</h2>
  <table><thead><tr><th>监控</th><th>可用率</th><th>百分比</th><th>故障时长</th></tr></thead>
    <tbody>
      <tr v-for="m in sla.monitors" :key="m.monitorName">
        <td>{{ m.monitorName }}</td>
        <td style="width:220px"><div class="bar"><span :class="avClass(m.availability)" :style="{width:m.availability+'%'}"></span></div></td>
        <td>{{ m.availability }}%</td><td>{{ m.downtimeSec }}s</td>
      </tr>
      <tr v-if="!(sla.monitors||[]).length"><td colspan="4" class="empty" style="color:#16a34a">窗口内无故障，可用率 100%</td></tr>
    </tbody>
  </table>
</template>
