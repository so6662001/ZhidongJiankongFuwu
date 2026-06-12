<script setup>
import { ref, onMounted } from 'vue';
import http from '../api';
import LineChart from '../components/LineChart.vue';
const ov = ref({}); const sla = ref({}); const trend = ref([]); const err = ref('');
async function load() {
  err.value = '';
  try {
    ov.value = await http.get('/stats/overview');
    sla.value = await http.get('/stats/sla?hours=24');
    const t = await http.get('/stats/trend?hours=24&buckets=24');
    trend.value = (t.points || []).map((p) => ({ label: p.time, value: +p.count }));
  } catch (e) { err.value = e.message; }
}
onMounted(load);
</script>
<template>
  <div v-if="err" class="err">{{ err }}</div>
  <div class="cards">
    <div class="card"><div class="num">{{ ov.firing ?? '-' }}</div><div class="label">当前告警(firing)</div></div>
    <div class="card"><div class="num">{{ ov.today ?? '-' }}</div><div class="label">今日告警数</div></div>
    <div class="card"><div class="num">{{ sla.worstAvailability ?? '-' }}%</div><div class="label">最差可用率</div></div>
    <div class="card"><div class="num">{{ sla.monitorCountWithIncident ?? '-' }}</div><div class="label">有故障监控数</div></div>
  </div>
  <h2>告警趋势（新增告警数）</h2>
  <LineChart :points="trend" />
  <h2>当前告警按服务分布</h2>
  <div v-if="(ov.firingByService||[]).length">
    <div class="dist" v-for="x in ov.firingByService" :key="x.serviceName">
      <div class="n">{{ x.serviceName || '(未分组)' }}</div>
      <div class="w"><div class="bar"><span class="av-crit" :style="{width: Math.min(100, x.count*20)+'%'}"></span></div></div>
      <div style="width:36px;text-align:right">{{ x.count }}</div>
    </div>
  </div>
  <div v-else class="empty">当前无 firing 告警</div>
</template>
