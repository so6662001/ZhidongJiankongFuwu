import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', redirect: '/overview' },
  { path: '/overview', component: () => import('./views/Overview.vue'), meta: { title: '概览' } },
  { path: '/alerts', component: () => import('./views/Alerts.vue'), meta: { title: '告警' } },
  { path: '/sla', component: () => import('./views/Sla.vue'), meta: { title: 'SLA' } },
  { path: '/monitors', component: () => import('./views/Monitors.vue'), meta: { title: '监控项' } },
  { path: '/owners', component: () => import('./views/Owners.vue'), meta: { title: '服务负责人' } },
  { path: '/maintenance', component: () => import('./views/Maintenance.vue'), meta: { title: '维护窗口' } },
  { path: '/oncall', component: () => import('./views/Oncall.vue'), meta: { title: '值班排班' } },
  { path: '/provision', component: () => import('./views/Provision.vue'), meta: { title: '导入纳管' } },
  { path: '/cmdb', component: () => import('./views/Cmdb.vue'), meta: { title: 'CMDB 同步' } },
  { path: '/notify', component: () => import('./views/Notify.vue'), meta: { title: '通知测试' } },
];

export default createRouter({ history: createWebHistory('/ui/'), routes });
