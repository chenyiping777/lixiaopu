'use strict';

// ChestnutShopWX (8080) uses Authorization: <accessToken> without a Bearer prefix.
const sections = {
  home: {name:'联调首页', subtitle:'以商城流程串起 8080 项目的接口', description:'从登录、挑选商品到订单与评价，逐页验证请求和响应。'},
  account: {name:'账户与资料', subtitle:'先登录，再完成个人设置', description:'账户登录、注册、令牌刷新、资料和密码相关接口。'},
  products: {name:'商品与收藏', subtitle:'发现好物，核对商品数据', description:'热门、搜索、分类、详情、规格、收藏与关键词接口。'},
  reviews: {name:'商品评价', subtitle:'看看大家怎么说', description:'一级评论、回复、追评、计数与点赞接口。'},
  cart: {name:'地址与购物车', subtitle:'装好喜欢的商品', description:'收货地址和购物车的查询、新增、修改与删除。'},
  orders: {name:'订单与支付', subtitle:'从结算走到收货', description:'订单创建、查询、运费、物流、状态流转与预支付。'},
  content: {name:'首页内容', subtitle:'上新、公告与品牌介绍', description:'轮播图、最新通知和关于我们的只读接口。'},
  service: {name:'客服与反馈', subtitle:'把问题告诉我们', description:'会话历史、清未读、用户反馈与图片上传。'},
  admin: {name:'运营管理', subtitle:'维护商城基础内容', description:'分类、轮播、通知与优惠券等管理接口。'}
};
const group = document.body.dataset.group;
const catalog = window.LEGACY_API_CATALOG || [];
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const $ = selector => document.querySelector(selector);
let baseUrl = localStorage.getItem('lxp.legacyBase') ?? (location.port === '8080' ? '' : 'http://localhost:8080');
let accessToken = sessionStorage.getItem('lxp.legacyAccessToken') || '';
let refreshToken = sessionStorage.getItem('lxp.legacyRefreshToken') || '';
let allowWrites = sessionStorage.getItem('lxp.legacyWrites') === 'true';
const safeRequest = operation => operation.method === 'GET' || [1, 2, 8].includes(operation.id);

function countFor(name) { return catalog.filter(operation => operation.group === name).length; }
function methodBadge(method) { return `<span class="method ${method.toLowerCase()}">${esc(method)}</span>`; }

document.body.innerHTML = `<div class="top-note"><div class="width">栗小铺 · 接口联调 <span>ChestnutShopWX 请求示例 / 8080</span></div></div>
<header class="header"><div class="width header-row"><a class="brand" href="index.html"><span class="brand-icon">栗</span><span>栗小铺<small>API SHOWROOM</small></span></a><a class="back-link" href="../index.html">返回 8081 商城页 ↗</a></div>
<nav class="width" aria-label="联调分区">${Object.entries(sections).map(([key, section]) => `<a href="${key === 'home' ? 'index' : key}.html" ${key === group ? 'class="active" aria-current="page"' : ''}>${section.name}</a>`).join('')}</nav></header>
<main class="width main"><div class="breadcrumb">联调首页 / ${sections[group].name}</div>
${group === 'home' ? `<section class="hero"><div><span class="eyebrow">LIXIAOPU API SHOWROOM</span><h1>${sections.home.subtitle}</h1><p>${sections.home.description}</p><a class="button" href="products.html">开始浏览接口 →</a></div><div class="hero-art" aria-hidden="true">栗</div></section>` : `<section class="intro"><span class="eyebrow">LIXIAOPU · API SHOWROOM</span><h1>${sections[group].subtitle}</h1><p>${sections[group].description}</p></section>`}
<section class="settings"><details><summary>联调设置 · 服务地址与访问令牌</summary><div class="settings-grid"><label>API 服务地址<input id="base-url" type="url" value="${esc(baseUrl)}" placeholder="http://localhost:8080"></label><label>访问令牌<input id="token" type="password" value="${esc(accessToken)}" placeholder="登录后自动填入，也可手动粘贴"></label><button id="save-settings" type="button">保存设置</button></div><label class="check"><input id="allow-writes" type="checkbox" ${allowWrites ? 'checked' : ''}>允许写入请求（创建、更新和删除）</label><p>空地址使用当前站点；默认指向文档中的 8080。跨域访问需要服务端允许当前页面来源。请求不会自动重试，令牌只保存在当前标签页。</p></details></section>
<div id="global-status" class="status" role="status" aria-live="polite">${group === 'home' ? '选择一个分区开始。' : `本页收录 ${countFor(group)} 个请求，参数可直接修改。`}</div>
<div id="content"></div><footer>栗小铺 · 8080 接口联调 <span>请求结构依据《接口请求示例.md》</span></footer></main>`;

function status(message, kind = '') { const node = $('#global-status'); node.textContent = message; node.className = 'status ' + kind; }
function updateLock() {
  document.querySelectorAll('.operation').forEach(card => {
    const operation = catalog.find(item => item.id === Number(card.dataset.id));
    const button = card.querySelector('.send');
    button.disabled = operation.browserUnsupported || (!safeRequest(operation) && !allowWrites);
    button.title = operation.browserUnsupported ? '浏览器 fetch 不能发送带请求体的 GET' : (!safeRequest(operation) && !allowWrites ? '请先在联调设置中允许写入请求' : '');
  });
}
$('#save-settings').onclick = () => {
  const input = $('#base-url').value.trim().replace(/\/+$/, '');
  if (input && !/^https?:\/\/[^\s]+$/i.test(input)) { status('服务地址必须以 http:// 或 https:// 开头。', 'error'); return; }
  if (baseUrl !== input) { accessToken = ''; refreshToken = ''; sessionStorage.removeItem('lxp.legacyAccessToken'); sessionStorage.removeItem('lxp.legacyRefreshToken'); $('#token').value = ''; }
  baseUrl = input;
  accessToken = $('#token').value.trim();
  localStorage.setItem('lxp.legacyBase', baseUrl);
  sessionStorage.setItem('lxp.legacyAccessToken', accessToken);
  status('设置已保存。', 'success');
};
$('#allow-writes').onchange = event => {
  allowWrites = event.target.checked;
  sessionStorage.setItem('lxp.legacyWrites', String(allowWrites));
  updateLock();
  status(allowWrites ? '写入请求已解锁。提交前请核对目标环境和参数。' : '写入请求已锁定。');
};

function operationCard(operation) {
  const params = operation.params.map(([name, value]) => `<label>${esc(name)}<input name="param:${esc(name)}" value="${esc(name === 'refreshToken' && refreshToken ? refreshToken : value)}"></label>`).join('');
  const notes = operation.notes.length ? `<div class="notes">${operation.notes.map(note => `<p>${esc(note)}</p>`).join('')}</div>` : '';
  const requestBody = operation.upload ? `<label class="field-wide">图片文件<input name="upload" type="file" accept="image/*"></label>` : operation.body ? `<label class="field-wide">JSON 请求体<textarea name="body" spellcheck="false" rows="${Math.min(13, Math.max(5, operation.body.split('\n').length + 1))}">${esc(operation.body)}</textarea></label>` : '';
  return `<details class="operation" data-id="${operation.id}"><summary><span class="number">${String(operation.id).padStart(2,'0')}</span><span class="operation-name">${esc(operation.title)}</span>${methodBadge(operation.method)}<code>${esc(operation.path)}</code></summary><form class="operation-form"><div class="form-grid"><label class="field-wide">请求路径<input name="path" value="${esc(operation.path)}" required></label>${params}<label class="field-wide">附加查询参数（可选，如 sortId=1&sortValue=...）<input name="extra" placeholder="key=value&another=value"></label>${operation.id === 16 ? '<label class="field-wide">可选 X-User-Id 请求头<input name="userId" placeholder="仅用于旧接口收藏展示，服务端不会据此验证身份"></label>' : ''}${requestBody}</div>${notes}${operation.browserUnsupported ? '<p class="warning">该 GET 接口要求 JSON 请求体，浏览器 fetch 不支持；请使用文档中的 HTTP 客户端示例联调。</p>' : ''}<div class="action-row"><button type="submit" class="send">发送请求 →</button><span class="auth-note">${operation.auth ? '需要访问令牌' : operation.optionalAuth ? '可选访问令牌' : '匿名可访问'}</span></div><div class="response-status" role="status" aria-live="polite"></div><pre class="response" hidden></pre></form></details>`;
}

if (group === 'home') {
  $('#content').innerHTML = `<div class="section-title"><div><span class="eyebrow">SHOPPING JOURNEY</span><h2>选择联调页面</h2><p>共 ${catalog.length} 个示例请求，按商城使用流程分区。</p></div></div><div class="section-grid">${Object.entries(sections).filter(([key]) => key !== 'home').map(([key, section], index) => `<a class="section-card" href="${key}.html"><span class="section-index">0${index+1} / ${countFor(key)} 个请求</span><h3>${section.name}</h3><p>${section.description}</p><span class="arrow">进入页面 →</span></a>`).join('')}</div><div class="notice-card"><b>联调提示</b><p>示例参数来自另一个项目的文档，账户、商品、订单 ID 和图片地址都需要改为本地测试数据。当前项目原有的 8081 页面仍可从顶部链接进入。</p></div>`;
} else {
  const operations = catalog.filter(operation => operation.group === group);
  $('#content').innerHTML = `<div class="section-title"><div><span class="eyebrow">REQUEST COLLECTION</span><h2>${sections[group].name}</h2><p>点开一项即可修改路径、查询参数与请求体。</p></div><span class="count">${operations.length} 个请求</span></div><div class="operation-list">${operations.map(operationCard).join('')}</div>`;
  updateLock();
  document.querySelectorAll('.operation-form').forEach(form => form.addEventListener('submit', submitRequest));
}

function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, /password|token|authorization|secret|ticket/i.test(key) ? '[已隐藏]' : redact(item)]));
  return value;
}
function redactUrl(url) {
  const copy = new URL(url);
  for (const key of copy.searchParams.keys()) if (/password|token|authorization|secret|ticket/i.test(key)) copy.searchParams.set(key, '[已隐藏]');
  return copy.href;
}

async function submitRequest(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const card = form.closest('.operation');
  const operation = catalog.find(item => item.id === Number(card.dataset.id));
  const button = form.querySelector('.send');
  const output = form.querySelector('.response');
  const responseStatus = form.querySelector('.response-status');
  if (operation.browserUnsupported || (!safeRequest(operation) && !allowWrites)) return;
  if (operation.auth && !accessToken) { responseStatus.textContent = '请先登录，或在联调设置中填入访问令牌。'; return; }
  const path = form.elements.path.value.trim();
  if (!path.startsWith('/api/')) { responseStatus.textContent = '请求路径应以 /api/ 开头。'; return; }
  let url;
  try {
    const origin = new URL(baseUrl || location.origin);
    url = new URL(path, origin);
    if (url.origin !== origin.origin || !url.pathname.startsWith('/api/')) throw new Error('请求目标必须在配置的 API 服务地址内。');
    for (const [name] of operation.params) {
      const value = form.elements['param:' + name].value.trim();
      if (value) url.searchParams.set(name, value); else url.searchParams.delete(name);
    }
    const extra = form.elements.extra.value.trim();
    if (extra) for (const [name, value] of new URLSearchParams(extra)) url.searchParams.set(name, value);
  } catch (error) { responseStatus.textContent = error.message || '请求地址无效。'; return; }
  const headers = {Accept:'application/json'};
  if ((operation.auth || operation.optionalAuth) && accessToken) headers.Authorization = accessToken;
  if (operation.id === 16 && form.elements.userId.value.trim()) headers['X-User-Id'] = form.elements.userId.value.trim();
  let body;
  let displayedBody;
  try {
    if (operation.upload) {
      const file = form.elements.upload.files[0];
      if (!file) throw new Error('请先选择图片文件。');
      if (!file.type.startsWith('image/')) throw new Error('只能上传图片。');
      body = new FormData(); body.append('file', file); displayedBody = {file:{name:file.name, size:file.size, type:file.type}};
    } else if (form.elements.body) {
      const value = form.elements.body.value.trim();
      if (value) { displayedBody = JSON.parse(value); body = JSON.stringify(displayedBody); headers['Content-Type'] = 'application/json'; }
    }
  } catch (error) { responseStatus.textContent = '请求体无效：' + error.message; return; }
  const trace = {request:{method:operation.method,url:redactUrl(url.href),headers:redact(headers),...(displayedBody !== undefined ? {body:redact(displayedBody)} : {})}};
  button.disabled = true; responseStatus.textContent = '请求中…'; output.hidden = false; output.textContent = JSON.stringify(trace,null,2);
  const abort = new AbortController();
  const timer = setTimeout(() => abort.abort(), 20000);
  const started = performance.now();
  try {
    const response = await fetch(url.href, {method:operation.method,headers,...(body !== undefined ? {body} : {}),signal:abort.signal});
    const raw = await response.text();
    let payload;
    try { payload = raw ? JSON.parse(raw) : null; } catch { payload = {nonJsonResponse:raw.slice(0,4000)}; }
    trace.response = {httpStatus:response.status,durationMs:Math.round(performance.now()-started),body:redact(payload)};
    if (response.ok && payload?.success === true) {
      responseStatus.textContent = `成功 · HTTP ${response.status} · ${payload.message || '操作成功'}`;
      responseStatus.className = 'response-status ok';
      if ([1,2,8].includes(operation.id) && payload.data?.accessToken) {
        accessToken = String(payload.data.accessToken);
        refreshToken = String(payload.data.refreshToken || '');
        sessionStorage.setItem('lxp.legacyAccessToken',accessToken);
        sessionStorage.setItem('lxp.legacyRefreshToken',refreshToken);
        $('#token').value = accessToken;
        const refreshInput = document.querySelector('.operation[data-id="8"] [name="param:refreshToken"]');
        if (refreshInput) refreshInput.value = refreshToken;
        status('令牌已保存到当前标签页，后续需登录请求会自动携带 Authorization。','success');
      }
      if (operation.id === 4) { accessToken = ''; sessionStorage.removeItem('lxp.legacyAccessToken'); $('#token').value = ''; }
    } else {
      responseStatus.textContent = `请求失败 · HTTP ${response.status} · ${payload?.message || '请查看响应'}`;
      responseStatus.className = 'response-status failed';
    }
  } catch (error) {
    trace.error = error.name === 'AbortError' ? '请求超时（20 秒）' : error instanceof TypeError ? '网络请求失败：请检查 8080 服务、地址与 CORS。' : error.message;
    responseStatus.textContent = trace.error;
    responseStatus.className = 'response-status failed';
  } finally {
    clearTimeout(timer);
    output.textContent = JSON.stringify(trace,null,2);
    button.disabled = operation.browserUnsupported || (!safeRequest(operation) && !allowWrites);
  }
}
