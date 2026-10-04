'use strict';

// 与用户端 Result / Controller 保持一致。
const API = {
  products: '/api/products', search: '/api/products/search', byCategory: '/api/products/by-category', login: '/api/auth/login',
  register: '/api/auth/register', sms: '/api/auth/sms', wechat: '/api/auth/wechat', refresh: '/api/auth/refresh', logout: '/api/auth/logout',
  profile: '/api/user/detail', profileUpdate: '/api/user/detail/update',
  addresses: '/api/addresses', cart: '/api/cart/items', orders: '/api/orders'
};
const pages = {
  home: ['index.html', '首页', '新鲜好味，随心挑选', '欢迎来到栗小铺，看看今天的好物。'],
  products: ['products.html', '全部商品', '发现喜欢的好味道', '浏览商品、查看详情，按分类和价格挑选。'],
  account: ['account.html', '登录注册', '欢迎回来', '登录后可以继续联调个人资料和购物流程。'],
  addresses: ['addresses.html', '收货地址', '管理收货地址', '为心仪好物填写送达地址。'],
  cart: ['cart.html', '购物车', '我的购物车', '核对商品与数量，准备结算。'],
  orders: ['orders.html', '我的订单', '我的订单', '查看订单状态与商品明细。']
};
const page = document.body.dataset.page;
const $ = selector => document.querySelector(selector);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money = value => Number.isFinite(Number(value)) && value !== null ? '¥' + Number(value).toFixed(2) : '—';
const idPath = id => '/' + encodeURIComponent(String(id));
const imageUrl = value => {
  if (!value) return '';
  try { const url = new URL(String(value), location.origin); return ['http:', 'https:'].includes(url.protocol) ? url.href : ''; }
  catch { return ''; }
};
const productArt = p => imageUrl(p.coverImage) ? `<img src="${escapeHtml(imageUrl(p.coverImage))}" alt="${escapeHtml(p.name)}" loading="lazy">` : '<span aria-hidden="true">栗</span>';
const demoProducts = [
  {id: 1, name: '原味栗仁', description: '软糯清甜 · 100克装', price: 12.9, stock: 100},
  {id: 2, name: '山野烤栗', description: '示例商品 · 轻烤原香', price: 19.9, stock: 60},
  {id: 3, name: '栗香分享装', description: '示例商品 · 分享一份甜', price: 32.8, stock: 35}
];
let preview = sessionStorage.getItem('lxp.preview') === 'true';
let baseUrl = localStorage.getItem('lxp.baseUrl') || '';
let token = sessionStorage.getItem('lxp.token') || '';
let cartItems = [];

document.body.innerHTML = `<div class="top-strip"><div class="site-width">欢迎光临栗小铺 <a href="legacy/index.html">8080 接口联调 ↗</a></div></div>
  <header class="site-header"><div class="site-width header-inner"><a class="brand" href="index.html"><span class="brand-mark">栗</span><span>栗小铺<small>LIXIAOPU MALL</small></span></a>
  <nav aria-label="主导航">${Object.entries(pages).map(([key, p]) => `<a href="${p[0]}" ${key === page ? 'class="active" aria-current="page"' : ''}>${p[1]}</a>`).join('')}</nav>
  <a class="header-cart" href="cart.html" aria-label="查看购物车">🛒 <span>购物车</span></a></div></header>
  <main class="main site-width"><div class="topbar"><span>首页 / ${pages[page][1]}</span><span id="mode" class="badge"></span></div>
  ${page === 'home' ? '' : `<header class="heading"><div class="eyebrow">LIXIAOPU MALL</div><h1>${pages[page][2]}</h1><p>${pages[page][3]}</p></header>`}
  <details class="panel settings"><summary>联调设置 · 接口地址与预览模式</summary><div class="settings-body">
  <form id="settings-form" class="row"><label class="grow">API 服务地址（留空使用当前站点）<input id="base-url" type="url" placeholder="例如 http://localhost:8081" value="${escapeHtml(baseUrl)}"></label><button class="secondary">保存地址</button></form>
  <label class="check hint"><input type="checkbox" id="preview" ${preview ? 'checked' : ''}>示例预览（只读，不发送接口请求、不修改数据库）</label>
  <p class="hint">当前项目：success=true 且 code=200 表示成功，登录后自动携带 Bearer 令牌。切换服务地址会清除令牌。跨域服务需由后端允许 CORS。</p>
  </div></details><div id="status" class="status" role="status" aria-live="polite"></div><div id="content"></div>
  <details class="debug" id="debug"><summary>请求与响应 · 调试面板（敏感字段已隐藏）</summary><pre id="trace">还没有发送请求。</pre></details>
  <footer class="footer">栗小铺 · 好物随心选 <span>联调页面位于 src/main/resources/static</span></footer></main>`;

function notice(text, type = '') { $('#status').textContent = text; $('#status').className = 'status ' + type; }
function modeLabel() {
  $('#mode').textContent = preview ? '示例预览 · 只读' : '真实接口 · ' + (token ? '已保存令牌' : '未登录');
  $('#mode').className = 'badge' + (preview ? ' demo' : '');
}
function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k,v]) => [k, /password|token|authorization|secret|code|ticket/i.test(k) ? '[已隐藏]' : redact(v)]));
  return value;
}
async function request(path, method = 'GET', body) {
  const url = baseUrl + path;
  const info = {request: {method, url, headers: {Accept: 'application/json', ...(token ? {Authorization: '[已隐藏]'} : {})}, ...(body ? {body: redact(body)} : {})}};
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  const started = performance.now();
  try {
    const response = await fetch(url, {method, headers: {Accept: 'application/json', ...(body ? {'Content-Type':'application/json'} : {}), ...(token ? {Authorization: 'Bearer ' + token} : {})}, ...(body ? {body: JSON.stringify(body)} : {}), signal: controller.signal});
    const text = await response.text();
    let payload;
    try { payload = text ? JSON.parse(text) : null; } catch { payload = {nonJsonResponse: text.slice(0, 4000)}; }
    info.response = {httpStatus: response.status, durationMs: Math.round(performance.now() - started), body: redact(payload)};
    if (!response.ok) throw new Error(response.status === 404 ? `HTTP 404：${path} 尚未实现或路径不匹配。` : `HTTP ${response.status}：${payload?.message || '请求失败，请检查后端日志。'}`);
    if (!payload || typeof payload.code !== 'number' || typeof payload.success !== 'boolean') throw new Error('响应格式不匹配：需要 {success, code, message, data}，请查看调试面板。');
    if (!payload.success || payload.code !== 200) throw new Error(`业务错误 ${payload.code}：${payload.message || '操作失败'}`);
    return payload.data;
  } catch (error) {
    info.error = error.name === 'AbortError' ? '请求超时（15秒）' : error.message;
    if (error instanceof TypeError) info.error = '网络请求失败：请检查服务是否启动、接口地址和 CORS 配置。';
    throw new Error(info.error);
  } finally { clearTimeout(timer); $('#trace').textContent = JSON.stringify(info, null, 2); $('#debug').open = true; }
}
async function run(button, action) {
  if (button) button.disabled = true;
  try { await action(); } catch (e) { notice(e.message, 'error'); }
  finally { if (button) button.disabled = false; }
}
function mutationAllowed() {
  if (preview) { notice('示例预览为只读。请关闭预览并实现对应接口后再提交。'); return false; }
  return true;
}
function list(data) {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.list)) return data.list;
  if (Array.isArray(data?.records)) return data.records;
  throw new Error('列表响应应为 data 数组、data.list 或 data.records 数组，请查看接口约定。');
}
function positiveId(value) {
  if (!/^[1-9]\d*$/.test(String(value))) throw new Error('请输入有效的正整数 ID。');
  return String(value); // ID 保留字符串，避免 BIGINT 超出 JavaScript 安全整数范围。
}
$('#settings-form').addEventListener('submit', e => {
  e.preventDefault();
  const value = $('#base-url').value.trim().replace(/\/+$/, '');
  if (value && !/^https?:\/\/[^\s]+$/i.test(value)) { notice('接口地址必须以 http:// 或 https:// 开头。', 'error'); return; }
  if (value !== baseUrl) { token = ''; for (const key of ['token','refreshToken','userId','sessionId']) sessionStorage.removeItem('lxp.'+key); }
  baseUrl = value; localStorage.setItem('lxp.baseUrl', value); modeLabel(); render(); notice('接口地址已保存。点击页面查询按钮开始联调。', 'success');
});
$('#preview').addEventListener('change', e => { preview = e.target.checked; sessionStorage.setItem('lxp.preview', String(preview)); modeLabel(); render(); });

function render() {
  notice(preview ? '当前展示的是示例数据，不代表后端接口已完成。' : '真实接口模式：商品与账号接口可联调；地址、购物车、订单接口仍需后端实现。');
  $('#trace').textContent = '还没有发送请求。'; $('#debug').open = false;
  ({home: renderHome, products: renderProducts, account: renderAccount, addresses: renderAddresses, cart: renderCart, orders: renderOrders})[page]();
}
function renderHome() {
  $('#content').innerHTML = `<section class="hero"><div class="hero-copy"><span class="eyebrow">栗小铺 · 精选好物</span><h1>新鲜好味，<br>随心挑选。</h1><p>一份香甜，一点日常的小快乐。来这里挑选喜欢的栗子零食。</p><a class="button-link" href="products.html">逛逛全部商品 <span>→</span></a></div><div class="hero-art" aria-hidden="true"><div class="hero-circle">栗</div><span>FRESH PICKS · LIXIAOPU</span></div></section>
  <div class="benefits"><span>✦ 精选好物</span><span>✦ 用心包装</span><span>✦ 轻松选购</span></div>
  <div class="section-title"><div><span class="eyebrow">HAND PICKED FOR YOU</span><h2>今日推荐</h2><p>直接读取商品接口，方便查看真实商品数据。</p></div><a href="products.html">查看全部 <span>→</span></a></div>
  <div id="featured" class="cards"><div class="empty">正在加载商品…</div></div>
  <div class="shop-banner"><div><span class="eyebrow">YOUR LITTLE SHOP</span><h2>喜欢的好物，放进购物车。</h2><p>登录后即可继续联调地址、购物车和订单流程。</p></div><a class="button-link light" href="account.html">登录或注册 →</a></div>`;
  run(null, async () => {
    let items;
    try { items = preview ? demoProducts : list(await request(API.search, 'POST', {pageNo:1, pageSize:4})); }
    catch (error) { $('#featured').innerHTML = '<div class="empty">商品加载失败，请检查上方提示和调试面板。</div>'; throw error; }
    $('#featured').innerHTML = items.length ? items.slice(0,4).map(p => `<a class="product" href="products.html?id=${encodeURIComponent(p.id)}"><div class="product-art">${productArt(p)}</div><div class="product-body"><span class="product-tag">精选商品</span><h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.description || '来自栗小铺的一份好味道')}</p><div class="between"><span class="price">${money(p.price)}</span><span class="card-arrow">→</span></div></div></a>`).join('') : '<div class="empty">暂时没有上架商品。</div>';
    notice(preview ? '正在预览示例商品，未发送请求。' : '商品接口连接成功，已加载今日推荐。', 'success');
  });
}
function renderProducts() {
  $('#content').innerHTML = `<section class="panel catalog-toolbar"><form id="search" class="row"><label class="grow">商品分类 ID<input name="categoryId" inputmode="numeric" pattern="[1-9][0-9]*" placeholder="全部分类"></label><label>排序方式<select name="sortBy"><option value="">默认排序</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option><option value="createTime-desc">最新上架</option></select></label><button>筛选商品</button></form><p class="hint">未填写分类：POST ${API.search}；填写分类：POST ${API.byCategory}。搜索接口支持关键词筛选。</p></section><div id="products" class="cards"><div class="empty">正在加载商品…</div></div><div class="pager"><button id="prev-page" class="secondary" type="button">上一页</button><span id="page-label">第 1 页</span><button id="next-page" class="secondary" type="button">下一页</button></div><div id="product-detail" class="detail"></div>`;
  let pageNo = 1;
  let pagesCount = 1;
  const paint = items => {
    $('#products').innerHTML = items.length ? items.map(p => `<article class="product"><div class="product-art">${productArt(p)}</div><div class="product-body"><span class="product-tag">栗小铺好物</span><h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.description || '来自栗小铺的一份好味道')}</p><div class="between"><span class="price">${money(p.price)}</span><span class="muted">库存 ${escapeHtml(p.stock ?? '—')}</span></div><div class="product-actions"><button class="secondary" data-detail="${escapeHtml(p.id)}">查看详情</button><button data-add="${escapeHtml(p.id)}">加入购物车</button></div></div></article>`).join('') : '<div class="empty">该分类暂时没有商品。</div>';
  };
  const load = async () => {
    const form = new FormData($('#search'));
    const categoryId = String(form.get('categoryId') || '').trim();
    const sort = String(form.get('sortBy') || '');
    const [sortBy, direction] = sort.split('-');
    const body = {pageNo, pageSize:12};
    if (sortBy && !categoryId && !preview) throw new Error('当前后端仅在分类查询接口支持排序，请先填写分类 ID。');
    if (sortBy) { body.sortBy = sortBy; body.asc = direction === 'asc'; }
    if (categoryId) body.categoryId = positiveId(categoryId);
    const data = preview ? {list:categoryId ? demoProducts.filter(p => String(p.categoryId || 1) === categoryId) : demoProducts, pages:1, total:demoProducts.length} : await request(categoryId ? API.byCategory : API.search, 'POST', body);
    let items = list(data);
    if (preview && sortBy === 'price') items = [...items].sort((a,b) => direction === 'asc' ? a.price-b.price : b.price-a.price);
    paint(items);
    pagesCount = Math.max(1, Number(data?.pages) || 1);
    $('#page-label').textContent = `第 ${pageNo} / ${pagesCount} 页 · 共 ${data?.total ?? items.length} 件`;
    $('#prev-page').disabled = pageNo <= 1;
    $('#next-page').disabled = pageNo >= pagesCount;
    notice(preview ? '已展示示例商品，未发送请求。' : '商品查询成功。', 'success');
  };
  $('#search').onsubmit = e => { e.preventDefault(); pageNo = 1; run(e.submitter, load); };
  $('#prev-page').onclick = e => run(e.currentTarget, async () => { pageNo--; try { await load(); } catch (error) { pageNo++; throw error; } });
  $('#next-page').onclick = e => run(e.currentTarget, async () => { pageNo++; try { await load(); } catch (error) { pageNo--; throw error; } });
  $('#products').onclick = e => {
    const button = e.target.closest('button'); if (!button) return;
    run(button, async () => {
      if (button.dataset.detail) {
        const p = preview ? demoProducts.find(p => String(p.id) === button.dataset.detail) : await request(API.products + idPath(button.dataset.detail));
        $('#product-detail').innerHTML = `<section class="panel detail-card"><div class="product-art">${productArt(p)}</div><div><span class="product-tag">商品详情 · ID ${escapeHtml(p.id)}</span><h2>${escapeHtml(p.name)}</h2><p>${escapeHtml(p.description || '暂无详情')}</p><div class="price">${money(p.price)}</div><p class="muted">当前库存：${escapeHtml(p.stock ?? '—')}</p></div></section>`;
        $('#product-detail').scrollIntoView({behavior:'smooth', block:'start'});
        notice(preview ? '已展示示例详情。' : '商品详情查询成功。', 'success');
      } else if (mutationAllowed()) {
        await request(API.cart, 'POST', {productId: positiveId(button.dataset.add), quantity: 1}); notice('已加入购物车，可前往购物车页面查询。', 'success');
      }
    });
  };
  run(null, async () => {
    await load();
    const requestedId = new URLSearchParams(location.search).get('id');
    if (requestedId && /^[1-9]\d*$/.test(requestedId)) $('#products [data-detail="' + CSS.escape(requestedId) + '"]')?.click();
  });
}
function renderAccount() {
  $('#content').innerHTML = `<div class="split"><section class="panel"><h2>账号登录</h2><div class="login-state">${token ? '当前会话已保存令牌，可继续查询个人数据。' : '当前会话尚未保存令牌。'}</div><form id="login" class="form-grid"><label class="span-all">手机号<input name="phone" autocomplete="tel" maxlength="11" required></label><label class="span-all">密码<input name="password" type="password" autocomplete="current-password" required></label><div class="row span-all"><button>登录</button><button type="button" id="refresh-token" class="secondary">刷新令牌</button><button type="button" id="logout" class="secondary">退出登录</button></div></form></section><section class="panel"><h2>注册买家账号</h2><form id="register" class="form-grid"><label class="span-all">用户名<input name="username" maxlength="64" required></label><label class="span-all">密码（至少 8 位）<input name="password" type="password" minlength="8" maxlength="72" required></label><label class="span-all">手机号<input name="phone" maxlength="11" required></label><label class="span-all">短信验证码<input name="code" maxlength="6" required></label><div class="row span-all"><button type="button" id="send-register-code" class="secondary">发送验证码</button><button>注册</button></div></form></section></div><section class="panel"><h2>微信小程序快捷登录</h2><form id="wechat-login" class="row"><label class="grow">临时 code<input name="code" required placeholder="由微信小程序 wx.login 获取"></label><button>用微信 code 登录</button></form><p class="hint">普通浏览器无法生成微信小程序 code。请在小程序端调用 wx.login，再将 code 提交给此接口；需先配置 AppID 与 Secret。</p></section>`;
  $('#content').insertAdjacentHTML('beforeend', `<section class="panel"><div class="between"><h2>个人资料</h2><button type="button" id="load-profile" class="secondary">查询资料</button></div><form id="profile-form" class="form-grid"><label>昵称<input name="nickname" maxlength="64" placeholder="昵称"></label><label>手机号<input name="phone" maxlength="11" placeholder="手机号"></label><label class="span-all">头像地址<input name="avatar" type="url" placeholder="https://..."></label><div class="span-all"><button>保存资料</button></div></form><p class="hint">登录后可查询和修改个人资料，用于验证令牌及用户接口。</p></section>`);
  const saveTokens = data => {
    if (typeof data?.token !== 'string' || !data.token.trim()) throw new Error('登录响应缺少 data.token 字符串。');
    token = data.token; sessionStorage.setItem('lxp.token', token);
    for (const key of ['refreshToken','userId','sessionId']) sessionStorage.setItem('lxp.' + key, String(data[key] || ''));
    modeLabel(); $('.login-state').textContent = '登录成功，当前会话已保存令牌。';
  };
  $('#login').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    const form = e.target; const data = await request(API.login, 'POST', Object.fromEntries(new FormData(form)));
    saveTokens(data); form.reset(); notice('登录成功，可以继续联调购物车、地址和订单。', 'success');
  }); };
  $('#register').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    await request(API.register, 'POST', Object.fromEntries(new FormData(e.target))); e.target.reset(); notice('注册成功，请在左侧登录。', 'success');
  }); };
  $('#send-register-code').onclick = e => run(e.currentTarget, async () => {
    if (!mutationAllowed()) return;
    const phone = $('#register [name="phone"]').value;
    const data = await request(API.sms, 'POST', {phone, purpose:'REGISTER'});
    notice(data?.localTestCode ? `本地测试验证码：${data.localTestCode}` : '验证码已发送。', 'success');
  });
  $('#wechat-login').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    const data = await request(API.wechat, 'POST', {code:new FormData(e.target).get('code')}); saveTokens(data); e.target.reset(); notice('微信快捷登录成功。', 'success');
  }); };
  $('#refresh-token').onclick = e => run(e.currentTarget, async () => {
    if (!mutationAllowed()) return;
    const data = await request(API.refresh, 'POST', {refreshToken:sessionStorage.getItem('lxp.refreshToken')});
    saveTokens(data); notice('令牌已轮换，旧刷新令牌不能再使用。', 'success');
  });
  $('#logout').onclick = e => run(e.currentTarget, async () => {
    if (!mutationAllowed()) return;
    if (token) await request(API.logout, 'POST');
    token = ''; for (const key of ['token','refreshToken','userId','sessionId']) sessionStorage.removeItem('lxp.'+key);
    modeLabel(); $('.login-state').textContent = '当前会话尚未保存令牌。'; notice('已退出登录。', 'success');
  });
  $('#load-profile').onclick = e => run(e.currentTarget, async () => {
    const data = preview ? {nickname:'示例用户',phone:'13800000000',avatar:''} : await request(API.profile);
    for (const key of ['nickname','phone','avatar']) $('#profile-form').elements[key].value = data?.[key] ?? '';
    notice(preview ? '已展示示例资料。' : '个人资料查询成功。', 'success');
  });
  $('#profile-form').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    await request(API.profileUpdate, 'PUT', Object.fromEntries(new FormData(e.target)));
    notice('个人资料已保存。', 'success');
  }); };
}
function renderAddresses() {
  $('#content').innerHTML = `<div class="split"><section class="panel"><div class="between"><h2>我的收货地址</h2><button id="load-addresses" class="secondary">查询地址</button></div><div id="addresses"><div class="empty">查询后可复制地址 ID 用于下单。</div></div></section><section class="panel"><h2>新增地址</h2><form id="address-form" class="form-grid">${[['receiverName','收件人',64],['receiverPhone','联系电话',20],['province','省份',64],['city','城市',64],['district','区县',64],['detailAddress','详细地址',255]].map(([name,label,max]) => `<label>${label}<input name="${name}" maxlength="${max}" required></label>`).join('')}<label class="check span-all"><input type="checkbox" name="isDefault">设为默认地址</label><div class="span-all"><button>保存地址</button></div></form></section></div>`;
  const paint = items => { $('#addresses').innerHTML = items.length ? items.map(a => `<article class="address-card"><div class="between"><b>${escapeHtml(a.receiverName)}</b><span class="badge">地址 ID ${escapeHtml(a.id)}</span></div><p>${escapeHtml(a.receiverPhone)}<br>${escapeHtml([a.province,a.city,a.district,a.detailAddress].join(' '))}</p>${Number(a.isDefault) === 1 ? '<span class="hint">默认地址</span>' : ''}</article>`).join('') : '<div class="empty">还没有地址，请在右侧新增。</div>'; };
  const load = async () => { paint(preview ? [{id:'1',receiverName:'示例收件人',receiverPhone:'138****0000',province:'浙江省',city:'杭州市',district:'西湖区',detailAddress:'示例路 1 号（仅预览）',isDefault:1}] : list(await request(API.addresses))); };
  if (preview) load();
  $('#load-addresses').onclick = e => run(e.currentTarget, async () => { await load(); notice(preview ? '已展示示例地址。' : '地址查询成功。', 'success'); });
  $('#address-form').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => { const form = e.target; const body = Object.fromEntries(new FormData(form)); body.isDefault = form.elements.isDefault.checked ? 1 : 0; await request(API.addresses, 'POST', body); form.reset(); notice('地址保存成功，点击「查询地址」获取最新列表。', 'success'); }); };
}
function renderCart() {
  $('#content').innerHTML = `<section class="panel"><div class="between"><h2>待结算商品</h2><button id="load-cart" class="secondary">查询购物车</button></div><div id="cart-list"><div class="empty">点击查询，读取当前用户的购物车。</div></div></section><section class="panel"><h2>创建订单</h2><form id="checkout" class="row"><label class="grow">收货地址 ID<input name="addressId" inputmode="numeric" pattern="[1-9][0-9]*" required placeholder="先在收货地址页查询 ID"></label><label class="grow">订单备注<input name="remark" maxlength="255" placeholder="选填"></label><button>提交勾选商品</button></form><p class="hint">只提交购物车条目 ID 与地址 ID。实际成交金额由后端计算，请先刷新并核对购物车。</p></section>`;
  cartItems = [];
  const paint = () => { $('#cart-list').innerHTML = cartItems.length ? `<div class="table-wrap"><table><thead><tr><th>结算</th><th>商品</th><th>单价</th><th>数量</th><th>小计</th><th>操作</th></tr></thead><tbody>${cartItems.map(i => `<tr><td><input type="checkbox" data-select="${escapeHtml(i.id)}" aria-label="选择${escapeHtml(i.productName)}" ${Number(i.selected) === 1 ? 'checked' : ''}></td><td>${escapeHtml(i.productName)}<br><span class="hint">条目 ID ${escapeHtml(i.id)}</span></td><td>${money(i.price)}</td><td><input class="qty" type="number" min="1" max="9999" step="1" value="${escapeHtml(i.quantity)}" aria-label="${escapeHtml(i.productName)}数量" data-qty="${escapeHtml(i.id)}"></td><td>${money(Number(i.price)*Number(i.quantity))}</td><td><button class="secondary" data-update="${escapeHtml(i.id)}">保存</button> <button class="secondary" data-delete="${escapeHtml(i.id)}">移除</button></td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">购物车是空的，先去商品橱窗添加商品吧。</div>'; };
  const load = async () => { cartItems = preview ? [{id:'1',productId:'1',productName:'原味栗仁',price:12.9,quantity:2,selected:1}] : list(await request(API.cart)); paint(); };
  if (preview) load();
  $('#load-cart').onclick = e => run(e.currentTarget, async () => { await load(); notice(preview ? '已展示示例购物车。' : '购物车查询成功。', 'success'); });
  $('#cart-list').onclick = e => {
    const button = e.target.closest('button'); if (!button || !mutationAllowed()) return;
    run(button, async () => {
      const id = button.dataset.update || button.dataset.delete;
      if (button.dataset.update) {
        const input = button.closest('tr').querySelector('[data-qty]'); if (!input.reportValidity() || !input.value) throw new Error('数量必须是 1 到 9999 的整数。');
        await request(API.cart + idPath(id), 'PATCH', {quantity:Number(input.value)});
        const item = cartItems.find(i => String(i.id) === id); item.quantity = Number(input.value);
      } else { await request(API.cart + idPath(id), 'DELETE'); cartItems = cartItems.filter(i => String(i.id) !== id); }
      paint(); notice('购物车已更新。', 'success');
    });
  };
  $('#checkout').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    const cartItemIds = [...document.querySelectorAll('[data-select]:checked')].map(i => positiveId(i.dataset.select));
    if (!cartItemIds.length) throw new Error('请先查询购物车并勾选要购买的商品。');
    for (const input of document.querySelectorAll('[data-qty]')) { const item = cartItems.find(i => String(i.id) === input.dataset.qty); if (Number(input.value) !== Number(item.quantity)) throw new Error('数量已修改，请先点击对应行的「保存」再下单。'); }
    const form = new FormData(e.target);
    const order = await request(API.orders, 'POST', {addressId:positiveId(form.get('addressId')),cartItemIds,remark:form.get('remark')});
    cartItems = cartItems.filter(i => !cartItemIds.includes(String(i.id))); paint(); notice(`订单创建成功，订单号：${order?.orderNo || order?.id || '请到订单页查询'}。`, 'success');
  }); };
}
function renderOrders() {
  $('#content').innerHTML = `<section class="panel"><div class="between"><h2>订单列表</h2><button id="load-orders" class="secondary">查询订单</button></div><div id="orders"><div class="empty">查询当前用户的订单。</div></div></section><section class="panel"><h2>订单详情</h2><form id="order-query" class="row"><label class="grow">订单 ID（不是订单号）<input name="id" inputmode="numeric" pattern="[1-9][0-9]*" required placeholder="输入订单 ID"></label><button>查询详情</button></form><div id="order-detail" class="detail"></div></section>`;
  const sample = {id:'1',orderNo:'DEMO202609220001',payableAmount:25.8,status:0,createTime:'2026-09-22 10:00:00',receiverName:'示例收件人',receiverAddress:'示例地址（仅预览）',items:[{productName:'原味栗仁',price:12.9,quantity:2,subtotal:25.8}]};
  const statuses = ['待支付','待发货','待收货','已完成','已取消'];
  const paint = items => { $('#orders').innerHTML = items.length ? `<div class="table-wrap"><table><thead><tr><th>ID / 订单号</th><th>应付金额</th><th>状态</th><th>创建时间</th></tr></thead><tbody>${items.map(o => `<tr><td>${escapeHtml(o.id)} / ${escapeHtml(o.orderNo)}</td><td>${money(o.payableAmount)}</td><td><span class="badge">${escapeHtml(statuses[o.status] || '未知状态')}</span></td><td>${escapeHtml(o.createTime)}</td></tr>`).join('')}</tbody></table></div>` : '<div class="empty">暂无订单，先从购物车提交一笔订单。</div>'; };
  if (preview) paint([sample]);
  $('#load-orders').onclick = e => run(e.currentTarget, async () => { paint(preview ? [sample] : list(await request(API.orders))); notice(preview ? '已展示示例订单。' : '订单查询成功。', 'success'); });
  $('#order-query').onsubmit = e => { e.preventDefault(); run(e.submitter, async () => {
    const id = positiveId(new FormData(e.target).get('id'));
    if (preview && id !== '1') throw new Error('示例预览仅有 ID 为 1 的订单。');
    const o = preview ? sample : await request(API.orders + idPath(id));
    $('#order-detail').innerHTML = `<p><b>${escapeHtml(o.orderNo)}</b> · ${escapeHtml(statuses[o.status] || '未知状态')}</p><p class="muted">${escapeHtml(o.receiverName)} / ${escapeHtml(o.receiverAddress)}</p><p class="price">${money(o.payableAmount)}</p><div class="table-wrap"><table><thead><tr><th>商品</th><th>成交单价</th><th>数量</th><th>小计</th></tr></thead><tbody>${(o.items || []).map(i => `<tr><td>${escapeHtml(i.productName)}</td><td>${money(i.price)}</td><td>${escapeHtml(i.quantity)}</td><td>${money(i.subtotal)}</td></tr>`).join('')}</tbody></table></div>`;
    notice(preview ? '已展示示例订单详情。' : '订单详情查询成功。', 'success');
  }); };
}
modeLabel(); render();
