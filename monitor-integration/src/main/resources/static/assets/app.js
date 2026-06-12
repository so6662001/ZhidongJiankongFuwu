// API 监控大盘前端（纯原生，无构建依赖）
const API = '/api/v1';
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = (s) => (s == null ? '' : String(s)).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

const state = { tab: 'overview', alertPage: 1, alertFilter: {}, monitorPage: 1, autoTimer: null };

function apiKey() { return localStorage.getItem('apiKey') || ''; }
function headers(json) {
  const h = {};
  const k = apiKey(); if (k) h['X-Api-Key'] = k;
  if (json) h['Content-Type'] = 'application/json';
  return h;
}
async function api(path, opts = {}) {
  const res = await fetch(API + path, { headers: headers(opts.body != null), ...opts });
  if (res.status === 401) throw new Error('401 未授权：请在右上角填写 X-Api-Key');
  const data = await res.json().catch(() => ({}));
  if (!res.ok || (data && data.code !== 0 && data.code !== undefined)) {
    throw new Error((data && data.message) || ('HTTP ' + res.status));
  }
  return data.data;
}
function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2600);
}
function avClass(v) { return v >= 99.9 ? 'av-ok' : (v >= 99 ? 'av-warn' : 'av-crit'); }
function sevBadge(s) { return `<span class="badge b-${esc(s || 'P2')}">${esc(s || '')}</span>`; }
function statusBadge(s) { return `<span class="badge b-${esc(s)}">${esc(s)}</span>`; }

// ---------- 概览 ----------
async function loadOverview() {
  try {
    const ov = await api('/stats/overview');
    $('#ov-firing').textContent = ov.firing;
    $('#ov-today').textContent = ov.today;
    const sla = await api('/stats/sla?hours=' + windowHours());
    $('#ov-worst').textContent = (sla.worstAvailability ?? 100) + '%';
    $('#ov-incident').textContent = sla.monitorCountWithIncident ?? 0;
    // firing by service 分布
    const maxv = Math.max(1, ...(ov.firingByService || []).map(x => +x.count || 0));
    $('#ov-byservice').innerHTML = (ov.firingByService || []).length
      ? ov.firingByService.map(x => `<div class="dist-row"><div class="name">${esc(x.serviceName || '(未分组)')}</div>
         <div class="barwrap"><div class="bar"><span class="av-crit" style="width:${(+x.count / maxv * 100)}%"></span></div></div>
         <div style="width:36px;text-align:right">${esc(x.count)}</div></div>`).join('')
      : '<div class="empty">当前无 firing 告警</div>';
    await loadTrend();
    setTs();
  } catch (e) { toast(e.message); }
}

// 通用折线 SVG 渲染。pts: [{label, value}]，opts: {min,max,suffix,fmt}
function lineChartSvg(pts, opts = {}) {
  if (!pts.length) return '<div class="empty">无数据</div>';
  const W = 900, H = 160, padL = 36, padT = 10, padR = 10, padB = 22;
  const vals = pts.map(p => +p.value);
  const maxv = opts.max != null ? opts.max : Math.max(1, ...vals);
  const minv = opts.min != null ? opts.min : 0;
  const range = (maxv - minv) || 1;
  const iw = W - padL - padR, ih = H - padT - padB;
  const x = i => padL + (pts.length === 1 ? iw / 2 : i / (pts.length - 1) * iw);
  const y = v => padT + ih - ((v - minv) / range) * ih;
  const line = pts.map((p, i) => `${x(i).toFixed(1)},${y(+p.value).toFixed(1)}`).join(' ');
  const area = `${padL},${padT + ih} ${line} ${x(pts.length - 1).toFixed(1)},${padT + ih}`;
  const dots = pts.map((p, i) => `<circle class="dot" cx="${x(i).toFixed(1)}" cy="${y(+p.value).toFixed(1)}" r="2"><title>${esc(p.label)}: ${esc(p.value)}${esc(opts.suffix || '')}</title></circle>`).join('');
  const xlabels = pts.map((p, i) => (i % 4 === 0 || i === pts.length - 1)
    ? `<text class="lbl" x="${x(i).toFixed(1)}" y="${H - 6}" text-anchor="middle">${esc(String(p.label).slice(-5))}</text>` : '').join('');
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">
    <line class="axis" x1="${padL}" y1="${padT + ih}" x2="${W - padR}" y2="${padT + ih}"></line>
    <text class="lbl" x="2" y="${padT + 8}">${(+maxv).toFixed(opts.dp ?? 0)}${esc(opts.suffix || '')}</text>
    <text class="lbl" x="2" y="${padT + ih}">${(+minv).toFixed(opts.dp ?? 0)}</text>
    <polyline class="area" points="${area}"></polyline>
    <polyline class="line" points="${line}"></polyline>${dots}${xlabels}</svg>`;
}

// 告警新增趋势
async function loadTrend() {
  try {
    const d = await api('/stats/trend?hours=' + windowHours() + '&buckets=24');
    const pts = (d.points || []).map(p => ({ label: p.time, value: +p.count }));
    $('#ov-trend').innerHTML = lineChartSvg(pts, { suffix: '' }) +
      `<div class="muted">窗口 ${d.windowHours}h · 共新增 ${d.total} 条告警</div>`;
  } catch (e) { $('#ov-trend').innerHTML = '<div class="empty">' + esc(e.message) + '</div>'; }
}

// ---------- 告警 ----------
async function loadAlerts() {
  try {
    const f = state.alertFilter;
    const qs = new URLSearchParams({ page: state.alertPage, size: 15 });
    ['status', 'severity', 'serviceName'].forEach(k => { if (f[k]) qs.set(k, f[k]); });
    const d = await api('/alerts?' + qs.toString());
    const marks = a => [a.escalated == 1 ? '<span class="badge b-P1">已升级</span>' : '',
      a.ackedBy ? '<span class="badge b-P2">已认领</span>' : ''].filter(Boolean).join(' ') || '<span class="muted">—</span>';
    const rows = (d.records || []).map(a => `<tr class="clickable" onclick="showAlert(${a.id})">
      <td>${a.id}</td><td>${statusBadge(a.status)}</td><td>${sevBadge(a.severity)}</td>
      <td>${esc(a.serviceName)}</td><td>${esc(a.monitorName)}</td>
      <td>${esc((a.content || '').slice(0, 50))}</td>
      <td>${marks(a)}</td></tr>`).join('');
    $('#alertBody').innerHTML = rows || '<tr><td colspan="7" class="empty">暂无告警</td></tr>';
    $('#alertPageInfo').textContent = `第 ${d.current || state.alertPage} / ${d.pages || 1} 页 · 共 ${d.total || 0} 条`;
    $('#alertPrev').disabled = (d.current || 1) <= 1;
    $('#alertNext').disabled = (d.current || 1) >= (d.pages || 1);
    setTs();
  } catch (e) { toast(e.message); }
}
async function showAlert(id) {
  try {
    const d = await api('/alerts/' + id);
    const a = d.alert;
    $('#drawerTitle').textContent = '告警 #' + a.id;
    const kv = (k, v) => `<div class="kv"><div class="k">${k}</div><div>${v}</div></div>`;
    const logs = (d.notifyLogs || []).map(l => `<div class="kv"><div class="k">${esc(l.channelType)}</div>
      <div>${l.success == 1 ? '✅成功' : '❌失败'} → ${esc(l.receiver)} ${l.errorMsg ? '<span class="muted">(' + esc(l.errorMsg) + ')</span>' : ''}
      <span class="muted">${esc(l.sentAt)}</span></div></div>`).join('') || '<div class="empty">无通知回执</div>';
    $('#drawerBody').innerHTML =
      kv('状态', statusBadge(a.status)) + kv('等级', sevBadge(a.severity)) +
      kv('服务', esc(a.serviceName)) + kv('监控', esc(a.monitorName)) +
      kv('接口', esc(a.url)) + kv('内容', esc(a.content)) +
      kv('首次发现', esc(a.firstSeen)) + kv('最近', esc(a.lastSeen)) +
      kv('恢复时间', esc(a.recoveredAt || '-')) + kv('持续(秒)', esc(a.durationSec ?? '-')) +
      kv('通知次数', esc(a.notifyCount)) + kv('升级', a.escalated == 1 ? '是 @ ' + esc(a.escalatedAt) : '否') +
      kv('认领', a.ackedBy ? esc(a.ackedBy) + ' @ ' + esc(a.ackedAt) : '未认领') +
      kv('备注', esc(a.remark || '-')) +
      `<div style="margin:12px 0"><button id="ackBtn" data-id="${a.id}" style="border:0;background:var(--primary);color:#fff;padding:7px 14px;border-radius:6px;cursor:pointer">认领/添加备注</button></div>` +
      '<h2>响应时间(近6h)</h2><div class="chart" id="rtChart"><div class="muted">—</div></div>' +
      '<h2>通知回执</h2>' + logs;
    $('#ackBtn').addEventListener('click', () => ackAlert(a.id));
    $('#drawer').classList.add('open');
    if (a.hzbMonitorId) loadResponseTime(a.hzbMonitorId);
  } catch (e) { toast(e.message); }
}

// 响应时间历史（来自 HertzBeat 时序库）
async function loadResponseTime(hzbId) {
  const box = $('#rtChart'); if (!box) return;
  box.innerHTML = '<div class="muted">加载响应时间…</div>';
  try {
    const d = await api('/hertzbeat/monitors/' + hzbId + '/response-time?history=6h');
    const values = (d && d.values) || {};
    const series = Object.values(values)[0] || [];
    const pts = series.map((it, i) => {
      const v = it.origin ?? it.value ?? it.mean ?? it.avg;
      return { label: it.time || it.timestamp || (i + 1), value: +v };
    }).filter(p => !isNaN(p.value));
    box.innerHTML = pts.length
      ? lineChartSvg(pts, { suffix: 'ms' })
      : '<div class="empty">暂无响应时间数据（监控不可达或无历史）</div>';
  } catch (e) { box.innerHTML = '<div class="empty">' + esc(e.message) + '</div>'; }
}
async function ackAlert(id) {
  const acker = prompt('认领人（你的姓名/工号）:', localStorage.getItem('acker') || '');
  if (acker === null) return;
  localStorage.setItem('acker', acker);
  const remark = prompt('处理备注（可选）:', '') || '';
  try {
    await api('/alerts/' + id + '/ack', { method: 'POST', body: JSON.stringify({ ackedBy: acker, remark }) });
    toast('已认领'); showAlert(id); loadAlerts();
  } catch (e) { toast(e.message); }
}

// ---------- SLA ----------
async function loadSla() {
  try {
    const sla = await api('/stats/sla?hours=' + windowHours() + (slaService() ? '&serviceName=' + encodeURIComponent(slaService()) : ''));
    $('#slaSummary').innerHTML =
      `<div class="card"><div class="num">${sla.worstAvailability}%</div><div class="label">最差可用率 (${sla.windowHours}h)</div></div>
       <div class="card"><div class="num">${sla.monitorCountWithIncident}</div><div class="label">有故障的监控数</div></div>`;
    $('#slaBody').innerHTML = (sla.monitors || []).length
      ? sla.monitors.map(m => `<tr><td>${esc(m.monitorName)}</td>
         <td style="width:220px"><div class="bar"><span class="${avClass(m.availability)}" style="width:${m.availability}%"></span></div></td>
         <td>${m.availability}%</td><td>${m.downtimeSec}s</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty" style="color:#16a34a">窗口内无故障，可用率 100%</td></tr>';
    // SLA 可用率趋势
    try {
      const t = await api('/stats/sla-trend?hours=' + windowHours() + '&buckets=24' + (slaService() ? '&serviceName=' + encodeURIComponent(slaService()) : ''));
      const pts = (t.points || []).map(p => ({ label: p.time, value: +p.availability }));
      const lo = Math.min(95, ...(pts.map(p => p.value)));
      $('#slaTrend').innerHTML = lineChartSvg(pts, { min: Math.floor(lo), max: 100, suffix: '%', dp: 1 }) +
        `<div class="muted">监控总数 ${t.totalMonitors} · 最低桶可用率 ${t.worstBucketAvailability}%</div>`;
    } catch (e) { $('#slaTrend').innerHTML = '<div class="empty">' + esc(e.message) + '</div>'; }
    setTs();
  } catch (e) { toast(e.message); }
}

// ---------- 监控项 ----------
async function loadMonitors() {
  try {
    const kw = $('#monKeyword').value.trim();
    const qs = new URLSearchParams({ page: state.monitorPage, size: 15 });
    if (kw) qs.set('keyword', kw);
    const d = await api('/monitors?' + qs.toString());
    $('#monBody').innerHTML = (d.records || []).length
      ? d.records.map(m => `<tr><td>${m.id}</td><td>${esc(m.name)}</td><td>${esc(m.serviceName)}</td>
         <td>${sevBadge(m.severity)}</td><td>${esc(m.method)}</td><td>${esc(m.url)}</td>
         <td>${m.hzbMonitorId ?? ''}</td></tr>`).join('')
      : '<tr><td colspan="7" class="empty">暂无监控项，请到「导入」纳管</td></tr>';
    $('#monPageInfo').textContent = `第 ${d.current || 1} / ${d.pages || 1} 页 · 共 ${d.total || 0} 条`;
    $('#monPrev').disabled = (d.current || 1) <= 1;
    $('#monNext').disabled = (d.current || 1) >= (d.pages || 1);
  } catch (e) { toast(e.message); }
}

// ---------- 服务负责人 ----------
async function loadOwners() {
  try {
    const list = await api('/service-owners');
    $('#ownerBody').innerHTML = (list || []).length
      ? list.map(o => `<tr><td>${o.id}</td><td>${esc(o.serviceName)}</td><td>${esc(o.wecomUserids)}</td>
         <td>${esc(o.emailList)}</td><td>${o.enabled == 1 ? '启用' : '停用'}</td>
         <td><button class="btn-ghost" onclick="delOwner(${o.id})">删除</button></td></tr>`).join('')
      : '<tr><td colspan="6" class="empty">暂无配置</td></tr>';
  } catch (e) { toast(e.message); }
}
async function saveOwner() {
  try {
    await api('/service-owners', { method: 'POST', body: JSON.stringify({
      serviceName: $('#ownSvc').value.trim(), wecomUserids: $('#ownWecom').value.trim(), emailList: $('#ownEmail').value.trim()
    }) });
    toast('已保存'); $('#ownSvc').value = $('#ownWecom').value = $('#ownEmail').value = ''; loadOwners();
  } catch (e) { toast(e.message); }
}
async function delOwner(id) { try { await api('/service-owners/' + id, { method: 'DELETE' }); loadOwners(); } catch (e) { toast(e.message); } }

// ---------- 导入 ----------
async function doImport(dryRun) {
  const body = { dryRun, severity: $('#impSev').value, serviceName: $('#impSvc').value.trim() || undefined,
    collector: $('#impCollector').value.trim() || undefined };
  const url = $('#impUrl').value.trim(); const content = $('#impContent').value.trim();
  if (url) body.openapiUrl = url; else if (content) body.openapiContent = content;
  else { toast('请填写 OpenAPI URL 或内容'); return; }
  try {
    const d = await api('/provision/import-openapi', { method: 'POST', body: JSON.stringify(body) });
    $('#impResult').textContent = `created=${d.created} updated=${d.updated} failed=${d.failed} (dryRun=${d.dryRun})\n` +
      (d.details || []).map(i => `[${i.action}] ${i.name}  ${i.url}${i.needsReview ? '  ⚠needsReview' : ''}${i.message ? '  ' + i.message : ''}`).join('\n');
    if (!dryRun) toast('导入完成');
  } catch (e) { $('#impResult').textContent = '错误: ' + e.message; }
}

// ---------- 通知测试 ----------
async function doNotifyTest() {
  const body = {};
  const svc = $('#ntSvc').value.trim(), w = $('#ntWecom').value.trim(), e = $('#ntEmail').value.trim();
  if (svc) body.serviceName = svc; if (w) body.wecomUserids = w; if (e) body.emails = e;
  try {
    const d = await api('/notify/test', { method: 'POST', body: JSON.stringify(body) });
    $('#ntResult').textContent = '已触发: ' + JSON.stringify(d, null, 2);
    toast('测试通知已发送');
  } catch (err) { $('#ntResult').textContent = '错误: ' + err.message; }
}

// ---------- 通用 ----------
function windowHours() { return $('#hours').value || 24; }
function slaService() { return $('#slaSvc').value.trim(); }
function setTs() { $('#ts').textContent = '更新于 ' + new Date().toLocaleTimeString(); }
function refreshCurrent() {
  ({ overview: loadOverview, alerts: loadAlerts, sla: loadSla, monitors: loadMonitors, owners: loadOwners }[state.tab] || (() => {}))();
}
function switchTab(name) {
  state.tab = name;
  $$('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
  $$('.view').forEach(v => v.classList.toggle('active', v.id === 'view-' + name));
  refreshCurrent();
}
function setupAuto() {
  if (state.autoTimer) clearInterval(state.autoTimer);
  const sec = +$('#auto').value;
  if (sec > 0) state.autoTimer = setInterval(refreshCurrent, sec * 1000);
}

function applyTheme(dark) {
  document.body.classList.toggle('dark', dark);
  localStorage.setItem('theme', dark ? 'dark' : 'light');
}

window.addEventListener('DOMContentLoaded', () => {
  applyTheme(localStorage.getItem('theme') === 'dark');
  $('#btnTheme').addEventListener('click', () => applyTheme(!document.body.classList.contains('dark')));
  $('#apiKey').value = apiKey();
  $('#apiKey').addEventListener('change', e => { localStorage.setItem('apiKey', e.target.value.trim()); toast('已保存 API Key'); refreshCurrent(); });
  $$('.tabs button').forEach(b => b.addEventListener('click', () => switchTab(b.dataset.tab)));
  $('#btnRefresh').addEventListener('click', refreshCurrent);
  $('#auto').addEventListener('change', setupAuto);
  $('#hours').addEventListener('change', refreshCurrent);
  // alerts filters
  ['fStatus', 'fSeverity', 'fService'].forEach(id => $('#' + id).addEventListener('change', () => {
    state.alertFilter = { status: $('#fStatus').value, severity: $('#fSeverity').value, serviceName: $('#fService').value.trim() };
    state.alertPage = 1; loadAlerts();
  }));
  $('#alertPrev').addEventListener('click', () => { if (state.alertPage > 1) { state.alertPage--; loadAlerts(); } });
  $('#alertNext').addEventListener('click', () => { state.alertPage++; loadAlerts(); });
  $('#monPrev').addEventListener('click', () => { if (state.monitorPage > 1) { state.monitorPage--; loadMonitors(); } });
  $('#monNext').addEventListener('click', () => { state.monitorPage++; loadMonitors(); });
  $('#monSearch').addEventListener('click', () => { state.monitorPage = 1; loadMonitors(); });
  $('#drawerClose').addEventListener('click', () => $('#drawer').classList.remove('open'));
  $('#slaRefresh').addEventListener('click', loadSla);
  $('#ownerSave').addEventListener('click', saveOwner);
  $('#impDry').addEventListener('click', () => doImport(true));
  $('#impRun').addEventListener('click', () => doImport(false));
  $('#ntSend').addEventListener('click', doNotifyTest);
  switchTab('overview');
  setupAuto();
});
window.showAlert = showAlert; window.delOwner = delOwner;
