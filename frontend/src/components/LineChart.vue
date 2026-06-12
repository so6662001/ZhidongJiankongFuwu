<script setup>
import { computed } from 'vue';
const props = defineProps({
  points: { type: Array, default: () => [] }, // [{label, value}]
  min: { type: Number, default: null },
  max: { type: Number, default: null },
  suffix: { type: String, default: '' },
  dp: { type: Number, default: 0 },
});
const W = 900, H = 160, padL = 36, padT = 10, padR = 10, padB = 22;
const view = computed(() => {
  const pts = props.points;
  if (!pts.length) return null;
  const vals = pts.map((p) => +p.value);
  const maxv = props.max != null ? props.max : Math.max(1, ...vals);
  const minv = props.min != null ? props.min : 0;
  const range = (maxv - minv) || 1;
  const iw = W - padL - padR, ih = H - padT - padB;
  const x = (i) => padL + (pts.length === 1 ? iw / 2 : (i / (pts.length - 1)) * iw);
  const y = (v) => padT + ih - ((v - minv) / range) * ih;
  const line = pts.map((p, i) => `${x(i).toFixed(1)},${y(+p.value).toFixed(1)}`).join(' ');
  const area = `${padL},${padT + ih} ${line} ${x(pts.length - 1).toFixed(1)},${padT + ih}`;
  const dots = pts.map((p, i) => ({ cx: x(i).toFixed(1), cy: y(+p.value).toFixed(1), t: `${p.label}: ${p.value}${props.suffix}` }));
  const labels = pts.map((p, i) => (i % 4 === 0 || i === pts.length - 1) ? { x: x(i).toFixed(1), text: String(p.label).slice(-5) } : null).filter(Boolean);
  return { line, area, dots, labels, maxv, minv, baseY: padT + ih };
});
</script>

<template>
  <div class="chart">
    <svg v-if="view" :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" style="width:100%;height:160px;display:block">
      <line :x1="padL" :y1="view.baseY" :x2="W - padR" :y2="view.baseY" stroke="var(--line)" />
      <text x="2" :y="padT + 8" fill="var(--muted)" font-size="10">{{ view.maxv.toFixed(dp) }}{{ suffix }}</text>
      <text x="2" :y="view.baseY" fill="var(--muted)" font-size="10">{{ view.minv.toFixed(dp) }}</text>
      <polyline :points="view.area" fill="var(--primary)" opacity="0.12" />
      <polyline :points="view.line" fill="none" stroke="var(--primary)" stroke-width="2" />
      <circle v-for="(d, i) in view.dots" :key="i" :cx="d.cx" :cy="d.cy" r="2" fill="var(--primary)"><title>{{ d.t }}</title></circle>
      <text v-for="(l, i) in view.labels" :key="'l' + i" :x="l.x" :y="H - 6" text-anchor="middle" fill="var(--muted)" font-size="10">{{ l.text }}</text>
    </svg>
    <div v-else class="empty">无数据</div>
  </div>
</template>
