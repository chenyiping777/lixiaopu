'use strict';

// 与用户端 Result / Controller 保持一致。
const API = {
  products: '/api/products', search: '/api/products/product/search', byCategory: '/api/products/product/category/list', login: '/api/auth/login',
  register: '/api/auth/register', sms: '/api/auth/sms', wechat: '/api/auth/wechat', refresh: '/api/auth/refresh', logout: '/api/auth/logout',
  profile: '/api/user/detail', profileUpdate: '/api/user/detail/update',
  addresses: '/api/address', cart: '/api/cart', orders: '/api/order'
};
const pages = {
  home: ['index.html', '首页', '新鲜好味，随心挑选', '欢迎来到栗小铺，看看今天的好物。'],
  products: ['products.html', '全部商品', '发现喜欢的好味道', '浏览商品、查看详情，按分类和价格排序。'],
  account: ['account.html', '个人中心', '欢迎回来', '管理个人资料、收藏与账号安全。'],
  addresses: ['addresses.html', '收货地址', '管理收货地址', '为心仪好物填写送达地址。'],
  cart: ['cart.html', '购物车', '我的购物车', '核对商品与数量，准备结算。'],
  orders: ['orders.html', '我的订单', '我的订单', '查看订单状态与商品明细。'],
  vouchers: ['vouchers.html', '优惠券', '我的优惠券', '抢购代金券，支付后可在购物车结算时使用。'],
  about: ['about.html', '关于我们', '关于栗小铺', '认识我们，了解每一份好味道背后的故事。']
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
const productArt = p => imageUrl(p.image || p.coverImage) ? `<img src="${escapeHtml(imageUrl(p.image || p.coverImage))}" alt="${escapeHtml(p.name)}" loading="lazy">` : '<span aria-hidden="true">栗</span>';
const demoProducts = [
  {id: 1, name: '原味栗仁', description: '软糯清甜 · 100克装', price: 12.9, stock: 100},
  {id: 2, name: '山野烤栗', description: '示例商品 · 轻烤原香', price: 19.9, stock: 60},
  {id: 3, name: '栗香分享装', description: '示例商品 · 分享一份甜', price: 32.8, stock: 35}
];
let preview = sessionStorage.getItem('lxp.preview') === 'true';
let baseUrl = localStorage.getItem('lxp.baseUrl') || '';
let token = sessionStorage.getItem('lxp.token') || '';
let cartItems = [];

const browseKey = () => 'lxp.browse.' + (baseUrl || location.origin);
function localBrowseHistory() {
  try {
    const saved = JSON.parse(localStorage.getItem(browseKey()) || '[]');
    return Array.isArray(saved) ? saved.filter(x => /^[1-9]\d*$/.test(String(x?.id))
      && Number.isFinite(x.time) && x.time > Date.now() - 30 * 86400000).slice(0, 100) : [];
  } catch { return []; }
}
function rememberAnonymousProduct(id) {
  if (preview || token) return;
  try {
    localStorage.setItem(browseKey(), JSON.stringify([{id: String(id), time: Date.now()},
      ...localBrowseHistory().filter(x => String(x.id) !== String(id))].slice(0, 100)));
  } catch { /* 本地存储不可用时仍可查看商品。 */ }
}

document.body.innerHTML = `<div class="top-strip"><div class="site-width">栗小铺 · 认真挑好物 <a href="about.html">关于我们 ↗</a></div></div>
  <header class="site-header"><div class="site-width header-inner"><a class="brand" href="index.html"><span class="brand-mark">栗</span><span>栗小铺<small>LIXIAOPU MALL</small></span></a>
  <nav aria-label="主导航">${Object.entries(pages).map(([key, p]) => `<a href="${p[0]}" ${key === page ? 'class="active" aria-current="page"' : ''}>${p[1]}</a>`).join('')}<a href="chat.html">在线客服</a></nav>
  <a class="header-cart" href="cart.html" aria-label="查看购物车">🛒 <span>购物车</span></a></div></header>
  <main class="main site-width"><div class="topbar"><span>首页 / ${pages[page][1]}</span><span id="mode" class="badge"></span></div>
  ${page === 'home' ? '' : `<header class="heading"><div class="eyebrow">LIXIAOPU MALL</div><h1>${pages[page][2]}</h1><p>${pages[page][3]}</p></header>`}
  <details class="panel settings"><summary>联调设置 · 接口地址与预览模式</summary><div class="settings-body">
  <form id="settings-form" class="row"><label class="grow">API 服务地址（留空使用当前站点）<input id="base-url" type="url" placeholder="例如 http://localhost:8081" value="${escapeHtml(baseUrl)}"></label><button class="secondary">保存地址</button></form>
  <label class="check hint"><input type="checkbox" id="preview" ${preview ? 'checked' : ''}>示例预览（只读，不发送接口请求、不修改数据库）</label>
  <p class="hint">当前项目：success=true 且 code=200 表示成功，登录后自动携带 Bearer 令牌。切换服务地址会清除令牌。跨域服务需由后端允许 CORS。</p>
  </div></details><div id="status" class="status" role="status" aria-live="polite"></div><div id="content"></div>
  <details class="debug" id="debug"><summary>请求与响应 · 调试面板（敏感字段已隐藏）</summary><pre id="trace">还没有发送请求。</pre></details>
  <footer class="footer">栗小铺 · 每一份喜欢，都值得 <span><a href="about.html">关于我们</a> · <a href="/admin/">商家中心</a></span></footer></main>
  <nav class="mobile-nav" aria-label="底部导航">${[['home','index.html','⌂','首页'],['products','products.html','▦','分类'],['vouchers','vouchers.html','◇','优惠券'],['cart','cart.html','▤','购物车'],['account','account.html','♙','个人中心']].map(([key,url,icon,label]) => `<a href="${url}" ${page === key ? 'aria-current="page"' : ''}><b aria-hidden="true">${icon}</b>${label}</a>`).join('')}</nav>`;

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
    const plainText = path === '/api/user/collection/delete';
    const response = await fetch(url, {method, headers: {Accept: 'application/json', ...(body ? {'Content-Type':plainText ? 'text/plain;charset=UTF-8' : 'application/json'} : {}), ...(token ? {Authorization: 'Bearer ' + token} : {})}, ...(body ? {body: plainText ? String(body) : JSON.stringify(body)} : {}), signal: controller.signal});
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
  } finally { clearTimeout(timer); $('#trace').textContent = JSON.stringify(info, null, 2); }
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
  clearInterval(carouselTimer);
  notice(preview ? '示例预览 · 商品仅作展示，不会提交交易。' : '');
  $('#trace').textContent = '还没有发送请求。'; $('#debug').open = false;
  ({home: renderHome, about: renderAbout, products: renderProducts, account: renderAccount, addresses: renderAddresses, cart: renderCart, orders: renderOrders, vouchers: renderVouchers})[page]();
  if (typeof enhanceCommerce === 'function') enhanceCommerce();
}
let carouselTimer;
function loadHomeBanners() {
  const panel = $('#home-banners');
  run(null, async () => {
    try {
      const banners = preview ? [] : list(await request('/api/banner/list'));
      if (!panel.isConnected) return;
      const visible = banners.filter(b => imageUrl(b.imageUrl));
      if (!visible.length) { panel.innerHTML = '<p class="hint">暂无首页轮播图。</p>'; return; }
      let current = 0;
      panel.innerHTML = '<div id="banner-slide"></div><div class="banner-controls"><button id="banner-prev" type="button" class="secondary" aria-label="上一张">←</button><span id="banner-count" aria-live="polite"></span><button id="banner-next" type="button" class="secondary" aria-label="下一张">→</button></div>';
      const paint = () => {
        const b = visible[current], link = imageUrl(b.linkUrl);
        const picture = '<img src="' + escapeHtml(imageUrl(b.imageUrl)) + '" alt="' + escapeHtml(b.title) + '">';
        panel.querySelector('#banner-slide').innerHTML = link ? '<a href="' + escapeHtml(link) + '">' + picture + '</a>' : picture;
        panel.querySelector('#banner-count').textContent = (current + 1) + ' / ' + visible.length;
      };
      const move = step => { current = (current + step + visible.length) % visible.length; paint(); };
      panel.querySelector('#banner-prev').onclick = () => move(-1);
      panel.querySelector('#banner-next').onclick = () => move(1);
      panel.querySelector('.banner-controls').hidden = visible.length < 2;
      paint();
      if (visible.length > 1 && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        carouselTimer = setInterval(() => {
          if (!panel.isConnected) { clearInterval(carouselTimer); return; }
          if (!document.hidden && !panel.matches(':hover') && !panel.contains(document.activeElement)) move(1);
        }, 5000);
      }
    } catch (error) {
      if (panel.isConnected) panel.textContent = '轮播图加载失败：' + error.message;
    }
  });
}
function renderAbout() {
  $('#content').innerHTML = '<section class="panel" id="about-info"><p>正在加载关于我们…</p></section>';
  const panel = $('#about-info');
  run(null, async () => {
    try {
      if (preview) { panel.innerHTML = '<p>示例预览不读取工厂信息，关闭预览后可查看关于我们。</p>'; return; }
      const info = await request('/api/about/us/introduce');
      if (!panel.isConnected) return;
      panel.innerHTML = (imageUrl(info.image) ? '<img class="about-image" src="' + escapeHtml(imageUrl(info.image)) + '" alt="' + escapeHtml(info.factoryName) + '">' : '') +
        '<h2>' + escapeHtml(info.factoryName) + '</h2><p class="about-introduction">' + escapeHtml(info.introduction) + '</p><dl>' +
        [['服务热线',info.serviceHotline],['官方微信',info.officialWechat],['地址',info.address]].filter(([,v]) => v)
          .map(([k,v]) => '<dt>' + k + '</dt><dd>' + escapeHtml(v) + '</dd>').join('') +
        '</dl><p class="hint">' + escapeHtml(info.copyrightInfo) + '</p>';
    } catch (error) {
      if (panel.isConnected) panel.textContent = '关于我们暂时无法展示：' + error.message;
    }
  });
}

function renderHome() {
  $('#content').innerHTML = `<form action="products.html" class="mall-search"><span aria-hidden="true">⌕</span><input aria-label="搜索商品" name="keyword" placeholder="搜一搜，把喜欢带回家"><button>搜索</button></form><div class="mall-tabs"><a class="active" href="index.html">为你推荐</a><a href="products.html">全部好物</a><a href="vouchers.html">优惠券专区</a><a href="orders.html">我的订单</a></div><section class="hero"><div class="hero-copy"><span class="eyebrow">LITTLE JOYS, EVERY DAY</span><h1>把日子<br>过甜一点<span>。</span></h1><p>一口软糯，一份心意。<br>发现属于你的日常小确幸。</p><a class="button-link" href="products.html">去挑点好吃的 <span>↗</span></a><small>栗小铺 / 精选栗子与零食</small></div><div class="hero-art" aria-hidden="true"><img src="assets/chestnuts.svg" alt=""><span class="hero-stamp">小小一颗<br>满满喜欢</span></div></section>
  <div class="benefits"><span>✦ 精选好物</span><span>✦ 用心包装</span><span>✦ 轻松选购</span></div><div id="home-categories" class="category-shortcuts"></div>
  <section class="promo-grid"><a href="vouchers.html"><span>好物有好价</span><h2>你的优惠券<br>等你来挑</h2><b>进入领券中心 ↗</b><i aria-hidden="true">券</i></a><a href="products.html"><span>给生活加点甜</span><h2>一人食，分享装<br>总有一款喜欢</h2><b>逛逛商品橱窗 ↗</b><i aria-hidden="true">栗</i></a></section><section id="home-banners" class="home-banners" aria-label="首页轮播图"><p class="hint">正在加载轮播图…</p></section>
  <div class="section-title"><div><span class="eyebrow">GOOD THINGS, JUST FOR YOU</span><h2>猜你喜欢 <em>每一口都是小满足</em></h2></div><a href="products.html">查看全部 <span>→</span></a></div>
  <div id="featured" class="cards"><div class="empty">正在加载商品…</div></div>
  <div class="shop-banner"><div><span class="eyebrow">YOUR LITTLE SHOP</span><h2>喜欢的好物，放进购物车。</h2><p>登录后查看订单、管理收货地址，开启轻松购物。</p></div><a class="button-link light" href="account.html">登录或注册 →</a></div>`;
  loadHomeBanners();
  run(null, async () => {
    let items;
    try { items = preview ? demoProducts : list(await request(API.search + '?sortType=default&querySize=12')); }
    catch (error) { $('#featured').innerHTML = '<div class="empty">商品加载失败，请检查上方提示和调试面板。</div>'; throw error; }
    $('#featured').innerHTML = items.length ? items.map(p => `<a class="product" href="products.html?id=${encodeURIComponent(p.id)}"><div class="product-art">${productArt(p)}</div><div class="product-body"><span class="product-tag">精选商品</span><h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.sellPoint || p.description || '来自栗小铺的一份好味道')}</p><div class="between"><span class="price">${money(p.price)}</span><span class="card-arrow">去看看 ↗</span></div></div></a>`).join('') : '<div class="empty">暂时没有上架商品。</div>';
    notice(preview ? '正在预览示例商品，未发送请求。' : '商品接口连接成功，已加载今日推荐。', 'success');
  });
}
function renderProducts() {
  $('#content').innerHTML = `<section class="panel catalog-toolbar"><form id="search" class="row"><label class="grow">商品分类<input name="categoryId" inputmode="numeric" pattern="[1-9][0-9]*" placeholder="全部分类"></label><label>排序方式<select name="sortBy"><option value="">默认排序</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option><option value="createTime-desc">最新上架</option></select></label><button>筛选商品</button></form><p class="hint">选择分类或输入关键词，找到心仪好物。</p></section><div id="products" class="cards"><div class="empty">正在加载商品…</div></div><div class="pager"><span id="page-label">第 1 页</span><button id="next-page" class="secondary" type="button">加载更多</button></div><div id="product-detail" class="detail"></div>`;
  $('#search').insertAdjacentHTML('afterbegin', '<label>商品关键词<input name="keyword" placeholder="例如：板栗"></label>');
  $('#content').insertAdjacentHTML('beforeend', '<section class="panel"><div class="between"><h2>最近浏览</h2><button type="button" id="load-history" class="secondary">刷新浏览记录</button></div><div id="browse-history" class="cards"></div></section>');
  let historyItems = [], historyCursor = null, anonymousItems = [];
  let historyLoading = false, historyEnd = true;
  $('#browse-history').insertAdjacentHTML('afterend', '<div class="pager"><button id="more-history" type="button" class="secondary" hidden>加载更多浏览记录</button></div>');
  const loadHistory = async (reset = true) => {
    if (historyLoading || (!reset && historyEnd)) return;
    historyLoading = true;
    $('#more-history').disabled = true;
    try {
      let items = [], nextCursor = null, isEnd = true;
      let localItems = anonymousItems;
      if (!preview) {
        if (token) {
          const params = new URLSearchParams({querySize: '20', sortType: 'browseTimeDesc'});
          if (!reset && historyCursor) {
            params.set('sortValue', historyCursor.sortValue);
            params.set('sortId', historyCursor.sortId);
          }
          const data = await request(API.products + '/history?' + params);
          items = list(data); nextCursor = data.cursorCommonEntity; isEnd = data.isEnd;
        } else {
          if (reset) {
            const ids = localBrowseHistory().map(x => x.id);
            localItems = ids.length ? list(await request(API.products + '/product/brief/list?productIds=' + encodeURIComponent(ids.join(','))))
              .filter(p => p && (p.status === 'active' || p.status === 1)) : [];
          }
          const offset = reset ? 0 : historyItems.length;
          items = localItems.slice(offset, offset + 20);
          isEnd = offset + items.length >= localItems.length;
        }
      }
      // 请求成功后才推进游标，失败重试仍从原位置开始。
      historyItems = reset ? items : [...historyItems, ...items];
      historyCursor = nextCursor; historyEnd = isEnd; anonymousItems = localItems;
      const uniqueItems = [...new Map(historyItems.map(p => [String(p.id), p])).values()];
      $('#browse-history').innerHTML = uniqueItems.length ? uniqueItems.map(p => `<article class="product"><div class="product-art">${productArt(p)}</div><div class="product-body"><h3>${escapeHtml(p.name)}</h3><p class="price">${money(p.price)}</p><button class="secondary" data-detail="${escapeHtml(p.id)}">查看详情</button></div></article>`).join('') : '<div class="empty">暂无浏览记录，查看商品详情后会显示在这里。</div>';
      $('#more-history').hidden = historyEnd;
    } finally {
      historyLoading = false;
      $('#more-history').disabled = false;
    }
  };
  $('#load-history').onclick = e => run(e.currentTarget, () => loadHistory(true));
  $('#more-history').onclick = e => run(e.currentTarget, () => loadHistory(false));
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting) && !historyEnd && !historyLoading)
        run(null, () => loadHistory(false));
    });
    observer.observe($('#more-history'));
  }
  let pageNo = 1;
  let cursor = null;
  let loadedItems = [];
  let productsEnded = false;
  const paint = items => {
    $('#products').innerHTML = items.length ? items.map(p => `<article class="product"><div class="product-art">${productArt(p)}</div><div class="product-body"><span class="product-tag">栗小铺好物</span><h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.description || '来自栗小铺的一份好味道')}</p><div class="between"><span class="price">${money(p.price)}</span><span class="muted">库存 ${escapeHtml(p.stock ?? '—')}</span></div><div class="product-actions"><button class="secondary" data-detail="${escapeHtml(p.id)}">查看详情</button><button data-add="${escapeHtml(p.id)}">加入购物车</button></div></div></article>`).join('') : '<div class="empty">该分类暂时没有商品。</div>';
  };
  const load = async () => {
    const form = new FormData($('#search'));
    const categoryId = String(form.get('categoryId') || '').trim();
    const sort = String(form.get('sortBy') || '');
    const [sortBy, direction] = sort.split('-');
    const keyword = String(form.get('keyword') || '').trim();
    const sortType = sortBy === 'price' ? (direction === 'asc' ? 'priceAsc' : 'priceDesc')
      : sortBy === 'createTime' ? 'newest' : 'default';
    const params = new URLSearchParams({sortType, querySize: '12'});
    if (cursor) {
      params.set('sortId', cursor.sortId);
      params.set('sortValue', cursor.sortValue);
    }
    if (categoryId) {
      params.set('categoryId', positiveId(categoryId));
      params.set('isFirstCategoryId', $('#search [name="categoryId"]').selectedOptions?.[0]?.dataset.first === 'true' ? 'true' : 'false');
    } else if (keyword) params.set('keyword', keyword);
    const data = preview ? {list:demoProducts, isEnd:true} : await request((categoryId ? API.byCategory : API.search) + '?' + params);
    const items = list(data);
    loadedItems = pageNo === 1 ? items : [...loadedItems, ...items];
    paint(loadedItems);
    cursor = data?.cursorCommonEntity || null;
    $('#page-label').textContent = `已加载 ${loadedItems.length} 件`;
    productsEnded = Boolean(data?.isEnd) || !cursor;
    $('#next-page').disabled = productsEnded;
    notice(preview ? '已展示示例商品，未发送请求。' : '商品查询成功。', 'success');
  };
  $('#search').onsubmit = e => { e.preventDefault(); pageNo = 1; cursor = null; loadedItems = []; run(e.submitter, load); };
  $('#next-page').onclick = e => run(e.currentTarget, async () => { pageNo++; try { await load(); } catch (error) { pageNo--; throw error; } }).finally(() => { $('#next-page').disabled = productsEnded; });
  const showPurchaseDetail = p => {
    const specs = p.specList || [];
    $('#product-detail').innerHTML = `<section class="panel detail-card" data-product-id="${escapeHtml(p.id)}"><div class="product-art">${productArt(p)}</div><div><span class="product-tag">栗小铺精选</span><h2>${escapeHtml(p.name)}</h2><p class="price">${money(p.price)}</p><p>${escapeHtml(p.description || '暂无详情')}</p><div class="row">${(p.imageUrls || []).filter(url => imageUrl(url)).map(url => `<img src="${escapeHtml(imageUrl(url))}" alt="商品图片" loading="lazy" style="width:100px;height:100px;object-fit:cover">`).join('')}</div>${specs.length ? `<label>选择规格<select id="purchase-spec">${specs.map(spec => `<option value="${escapeHtml(spec.id)}">${escapeHtml(spec.specText)} · ${money(spec.price)} · 库存 ${escapeHtml(spec.stock ?? '—')}</option>`).join('')}</select></label><button data-add-spec="${escapeHtml(p.id)}">加入购物车</button>` : '<p>暂无可选规格，暂不能加入购物车。</p>'}</div></section>`;
    $('#product-detail').scrollIntoView({behavior:'smooth', block:'start'});
  };
  $('#content').onclick = e => {
    const button = e.target.closest('button'); if (!button) return;
    if (!button.dataset.detail && !button.dataset.add && !button.dataset.addSpec) return;
    run(button, async () => {
      if (button.dataset.addSpec) {
        if (!mutationAllowed()) return;
        await request(API.cart + '/add', 'POST', {productId:positiveId(button.dataset.addSpec),specId:positiveId($('#purchase-spec').value),quantity:1});
        notice('已加入购物车，可前往购物车查询并结算。', 'success');
      } else if (button.dataset.detail) {
        const p = preview ? demoProducts.find(p => String(p.id) === button.dataset.detail) : await request(API.products + idPath(button.dataset.detail));
        rememberAnonymousProduct(p.id);
        showPurchaseDetail(p);
        notice(preview ? '已展示示例详情。' : '商品详情查询成功。', 'success');
        try { await loadHistory(); } catch { /* 浏览记录加载失败不影响详情。 */ }
      } else if (mutationAllowed()) {
        const p = await request(API.products + idPath(button.dataset.add));
        if ((p.specList || []).length === 1) {
          await request(API.cart + '/add', 'POST', {productId:positiveId(p.id),specId:positiveId(p.specList[0].id),quantity:1});
          notice('已加入购物车，可前往购物车查询并结算。', 'success');
        } else { showPurchaseDetail(p); notice('请选择规格后加入购物车。'); }
      }
    });
  };
  run(null, async () => {
    await load();
    await loadHistory();
    const requestedId = new URLSearchParams(location.search).get('id');
    if (requestedId && /^[1-9]\d*$/.test(requestedId)) {
      const button = document.createElement('button');
      button.hidden = true; button.dataset.detail = requestedId;
      $('#products').append(button); button.click(); button.remove();
    }
  });
}
function renderAccount() {
  $('#content').innerHTML = `<div class="split"><section class="panel"><h2>账号登录</h2><div class="login-state">${token ? '已登录，可以管理个人信息。' : '登录后管理订单、收藏和优惠券。'}</div><form id="login" class="form-grid"><label class="span-all">手机号<input name="phone" autocomplete="tel" maxlength="11" required></label><label class="span-all">密码<input name="password" type="password" autocomplete="current-password" required></label><div class="row span-all"><button>登录</button><button type="button" id="refresh-token" class="secondary">刷新令牌</button><button type="button" id="logout" class="secondary">退出登录</button></div></form></section><section class="panel"><h2>注册买家账号</h2><form id="register" class="form-grid"><label class="span-all">用户名<input name="username" maxlength="64" required></label><label class="span-all">密码（至少 8 位）<input name="password" type="password" minlength="8" maxlength="72" required></label><label class="span-all">手机号<input name="phone" maxlength="11" required></label><label class="span-all">短信验证码（5 位大写字母与数字混合）<input name="code" minlength="5" maxlength="5" pattern="(?=.*[A-Z])(?=.*[0-9])[A-Z0-9]{5}" title="请输入 5 位大写字母与数字混合验证码，至少包含一个字母和一个数字" autocomplete="one-time-code" autocapitalize="characters" required></label><div class="row span-all"><button type="button" id="send-register-code" class="secondary">发送验证码</button><button>注册</button></div></form></section></div><section class="panel"><h2>关于栗小铺</h2><p><a href="about.html">关于我们 →</a></p></section><section class="panel"><h2>微信小程序快捷登录</h2><form id="wechat-login" class="row"><label class="grow">临时 code<input name="code" required placeholder="由微信小程序 wx.login 获取"></label><button>用微信 code 登录</button></form><p class="hint">普通浏览器无法生成微信小程序 code。请在小程序端调用 wx.login，再将 code 提交给此接口；需先配置 AppID 与 Secret。</p></section>`;
  $('#content').insertAdjacentHTML('beforeend', `<section class="panel"><div class="between"><h2>个人资料</h2><button type="button" id="load-profile" class="secondary">查询资料</button></div><form id="profile-form" class="form-grid"><label>昵称<input name="nickname" maxlength="64" placeholder="昵称"></label><label>手机号<input name="phone" maxlength="11" placeholder="手机号"></label><label class="span-all">头像地址<input name="avatar" type="url" placeholder="https://..."></label><div class="span-all"><button>保存资料</button></div></form><p class="hint">完善昵称与头像，让每一次相遇更亲切。</p></section>`);
  const saveTokens = data => {
    if (typeof data?.accessToken !== 'string' || !data.accessToken.trim()) throw new Error('登录响应缺少 data.accessToken 字符串。');
    token = data.accessToken; sessionStorage.setItem('lxp.token', token);
    for (const key of ['refreshToken','userId','sessionId']) sessionStorage.setItem('lxp.' + key, String(data[key] || ''));
    modeLabel(); $('.login-state').textContent = '登录成功，欢迎回来。';
  };
  $('#login').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    const form = e.target; const data = await request(API.login, 'POST', Object.fromEntries(new FormData(form)));
    saveTokens(data); form.reset(); notice('登录成功，欢迎回来。', 'success');
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
    modeLabel(); $('.login-state').textContent = '登录后管理订单、收藏和优惠券。'; notice('已退出登录。', 'success');
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
  const paint = items => { $('#addresses').innerHTML = items.length ? items.map(a => `<article class="address-card"><div class="between"><b>${escapeHtml(a.receiver || a.receiverName)}</b><span class="badge">地址 ID ${escapeHtml(a.id)}</span></div><p>${escapeHtml(a.phone || a.receiverPhone)}<br>${escapeHtml([a.province,a.city,a.district,a.detailAddress].join(' '))}</p>${(a.isDefault === true || Number(a.isDefault) === 1) ? '<span class="hint">默认地址</span>' : ''}</article>`).join('') : '<div class="empty">还没有地址，请在右侧新增。</div>'; };
  const load = async () => { paint(preview ? [{id:'1',receiverName:'示例收件人',receiverPhone:'138****0000',province:'浙江省',city:'杭州市',district:'西湖区',detailAddress:'示例路 1 号（仅预览）',isDefault:1}] : list(await request(API.addresses + '/list'))); };
  if (preview) load();
  $('#load-addresses').onclick = e => run(e.currentTarget, async () => { await load(); notice(preview ? '已展示示例地址。' : '地址查询成功。', 'success'); });
  $('#address-form').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => { const form = e.target; const body = Object.fromEntries(new FormData(form)); body.isDefault = form.elements.isDefault.checked; await request(API.addresses + '/add', 'POST', body); form.reset(); notice('地址保存成功，点击「查询地址」获取最新列表。', 'success'); }); };
}
function cartEntryKey(item) { return positiveId(item.productId) + ':' + positiveId(item.specId); }
function cartOrderPayload(items, keys, addressId, remark) {
  const selected = new Set(keys);
  if (!selected.size) throw new Error('请勾选要购买的商品。');
  const orderItems = items.filter(item => selected.has(cartEntryKey(item))).map(item => ({
    productId: positiveId(item.productId), specId: positiveId(item.specId), quantity: Number(item.quantity),
    price: String(item.price), productName: item.productName,
    productImage: item.productImage, specText: item.specText
  }));
  if (orderItems.length !== selected.size || orderItems.some(i => !Number.isInteger(i.quantity) || i.quantity < 1 || !Number.isFinite(Number(i.price)) || Number(i.price) < 0)) throw new Error('购物车已变化，请刷新。');
  return {source:'CART', addressId:positiveId(addressId), remark:String(remark || ''), orderItems};
}
function renderCart() {
  $('#content').innerHTML = `<section class="panel"><div class="between"><h2>待结算商品</h2><button id="load-cart" class="secondary">查询购物车</button></div><div id="cart-list"><div class="empty">点击查询，读取当前用户的购物车。</div></div></section><section class="panel"><h2>结算勾选商品</h2><form id="checkout" class="row"><label class="grow">收货地址<input name="addressId" inputmode="numeric" pattern="[1-9][0-9]*" required placeholder="先在收货地址页查询 ID"></label><label class="grow">订单备注<input name="remark" maxlength="255" placeholder="选填"></label><button>提交勾选商品</button></form><p class="hint">勾选的多个商品合成一张订单；商品价格、运费和用券优惠均以服务端结果为准。修改数量后请先保存；下单成功后移除本次勾选商品，未勾选商品保留。</p></section>`;
  $('#checkout').insertAdjacentHTML('afterbegin', '<label class="grow">优惠券<select name="voucherOrderId"><option value="">不使用优惠券</option></select></label><button type="button" id="load-checkout-vouchers" class="secondary">加载优惠券</button>');
  let voucherCursor = '0';
  let voucherEnd = false;
  $('#load-checkout-vouchers').onclick = e => run(e.currentTarget, async () => {
    if (preview) { notice('示例预览不读取个人优惠券。'); return; }
    const batch = list(await request('/api/voucher-order/mine?' + new URLSearchParams({beforeId:voucherCursor,limit:100})));
    const select = $('#checkout [name="voucherOrderId"]');
    for (const voucher of batch.filter(v => v.state === 'AVAILABLE')) {
      select.insertAdjacentHTML('beforeend', `<option value="${escapeHtml(voucher.id)}">抵扣 ${money(Number(voucher.actualValue) / 100)} · ${escapeHtml(voucher.validUntil)} 到期</option>`);
    }
    if (batch.length) voucherCursor = batch[batch.length - 1].id;
    $('#load-checkout-vouchers').textContent = '继续加载优惠券';
    voucherEnd = batch.length < 100;
    notice('已加载可用券；一单一券，仅抵商品金额，不找零，提交时重新校验。', 'success');
  }).finally(() => { $('#load-checkout-vouchers').disabled = voucherEnd; });
  cartItems = [];
  let selected = new Set();
  const paint = () => { $('#cart-list').innerHTML = cartItems.length ? `<div class="table-wrap"><table><thead><tr><th>结算</th><th>商品 / 规格</th><th>单价</th><th>数量</th><th>小计</th><th>操作</th></tr></thead><tbody>${cartItems.map(i => { const key = cartEntryKey(i); return `<tr><td><input type="checkbox" data-select="${escapeHtml(key)}" aria-label="选择${escapeHtml(i.productName)}" ${selected.has(key) ? 'checked' : ''}></td><td>${escapeHtml(i.productName)}<br><span class="hint">${escapeHtml(i.specText)}</span></td><td>${money(i.price)}</td><td><input class="qty" type="number" min="1" max="9999" step="1" value="${escapeHtml(i.quantity)}" data-qty="${escapeHtml(key)}"></td><td>${money(Number(i.price)*Number(i.quantity))}</td><td><button class="secondary" data-update="${escapeHtml(key)}">保存</button> <button class="secondary" data-delete="${escapeHtml(key)}">移除</button></td></tr>`; }).join('')}</tbody></table></div>` : '<div class="empty">购物车是空的，先去商品橱窗添加商品吧。</div>'; };
  const load = async () => {
    cartItems = preview ? [{productId:'1',specId:'11',productName:'原味栗仁',specText:'500g',price:12.9,quantity:2}] : list(await request(API.cart + '/list'));
    selected = new Set([...selected].filter(key => cartItems.some(i => cartEntryKey(i) === key))); paint();
  };
  if (preview) load();
  $('#load-cart').onclick = e => run(e.currentTarget, async () => { await load(); notice('购物车查询成功。', 'success'); });
  $('#cart-list').onchange = e => { if(e.target.dataset.select) { if(e.target.checked) selected.add(e.target.dataset.select); else selected.delete(e.target.dataset.select); } };
  $('#cart-list').onclick = e => {
    const button = e.target.closest('button'); if (!button || !mutationAllowed()) return;
    run(button, async () => {
      const key = button.dataset.update || button.dataset.delete;
      const item = cartItems.find(i => cartEntryKey(i) === key);
      if (!item) throw new Error('购物车已变化，请刷新。');
      if (button.dataset.update) {
        const input = button.closest('tr').querySelector('[data-qty]');
        if (!input.reportValidity() || !input.value) throw new Error('数量必须是 1 到 9999 的整数。');
        await request(API.cart + '/quantity', 'PUT', {productId:item.productId,specId:item.specId,quantity:Number(input.value)});
      } else {
        await request(API.cart + '/products?' + new URLSearchParams({productIds:item.productId,specIds:item.specId}), 'DELETE');
        selected.delete(key);
      }
      await load(); notice('购物车已更新。', 'success');
    });
  };
  $('#checkout').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return; run(e.submitter, async () => {
    for (const input of document.querySelectorAll('[data-qty]')) {
      const item = cartItems.find(i => cartEntryKey(i) === input.dataset.qty);
      if (selected.has(input.dataset.qty) && Number(input.value) !== Number(item.quantity)) throw new Error('数量已修改，请先保存再下单。');
    }
    const form = new FormData(e.target);
    const body = cartOrderPayload(cartItems, [...selected], form.get('addressId'), form.get('remark'));
    if (form.get('voucherOrderId')) body.voucherOrderId = positiveId(form.get('voucherOrderId'));
    const order = await request(API.orders + '/create', 'POST', body);
    $('#checkout [name="voucherOrderId"]').value = '';
    if (order.cartCleared === true) {
      cartItems = cartItems.filter(item => !selected.has(cartEntryKey(item)));
      selected.clear(); paint();
      notice(`订单创建成功，订单号：${order.orderNo}。优惠 ${money(order.voucherDiscountAmount || 0)}，应付 ${money(order.payAmount)}。已移除本次结算商品，${order.paymentRequired === false ? '无需支付，已完成结算。' : '可到「我的订单」查看。'}`, 'success');
    } else {
      selected.clear(); paint();
      notice(`订单已创建，订单号：${order.orderNo}。购物车清理未完成，请刷新后手动移除已购商品，请勿重复下单。`, 'error');
    }
  }); };
}
async function invokeWechatPayment(parameters) {
  if (!window.WeixinJSBridge) throw new Error('请在微信内打开页面发起支付。');
  const payment = {appId:parameters.appId, timeStamp:parameters.timeStamp, nonceStr:parameters.nonceStr,
    package:parameters.package || parameters.packageVal, signType:parameters.signType, paySign:parameters.paySign};
  await new Promise((resolve, reject) => window.WeixinJSBridge.invoke('getBrandWCPayRequest', payment, result => {
    if (result.err_msg === 'get_brand_wcpay_request:ok') resolve();
    else reject(new Error('支付未完成，请查询订单状态后重试。'));
  }));
}
function renderVouchers() {
  $('#content').innerHTML = `<section class="panel"><h2>秒杀购券</h2><button id="load-activities">加载活动</button><div id="voucher-activities"></div><p>抢购成功后还需付款，付款成功才能使用。每人同一活动限购一次。</p></section><section class="panel"><h2>购券订单支付</h2><label>购券订单号<input id="voucher-ticket" inputmode="numeric"></label><button id="voucher-result">查询抢购结果</button><button id="pay-voucher">支付购券订单</button></section><section class="panel"><h2>我的券与购券订单</h2><button id="load-wallet">刷新我的券</button><button id="more-wallet" class="secondary" disabled>加载更多</button><div id="voucher-wallet"></div><p>一单一券，仅抵商品金额，不找零；实付为零时直接完成结算，无需微信支付。未支付取消及商品全额退款成功后返还原券，使用期限不延长。</p></section>`;
  $('#content').insertAdjacentHTML('afterbegin', `<section class="panel"><h2>普通代金券</h2><button id="load-normal-vouchers">加载普通优惠券</button><div id="normal-vouchers"></div><p>不限购买库存和活动时间，可重复购买；付款后与秒杀券一样用于商品订单抵扣。</p></section>`);
  let normalCursor = '0';
  let normalEnd = false;
  $('#load-normal-vouchers').onclick = e => run(e.currentTarget, async () => {
    if (preview) { notice('示例预览不读取普通券。'); return; }
    const batch = list(await request('/api/vouchers?' + new URLSearchParams({type:0,afterId:normalCursor,limit:20})));
    for (const v of batch) $('#normal-vouchers').insertAdjacentHTML('beforeend', `<article><b>${escapeHtml(v.title)}</b> · ${money(Number(v.payValue)/100)} 购 ${money(Number(v.actualValue)/100)} 代金券<p>付款后 ${escapeHtml(v.validityDays)} 天内使用</p><button data-buy-normal-voucher="${escapeHtml(v.id)}">购买代金券</button></article>`);
    if (batch.length) normalCursor = batch[batch.length-1].id;
    normalEnd = batch.length < 20;
    $('#load-normal-vouchers').textContent = '加载更多普通券';
  }).finally(() => { $('#load-normal-vouchers').disabled = normalEnd; });
  $('#normal-vouchers').onclick = e => {
    const button = e.target.closest('[data-buy-normal-voucher]');
    if (!button || !mutationAllowed()) return;
    run(button, async () => {
      const voucher = await request('/api/voucher-order/buy/' + positiveId(button.dataset.buyNormalVoucher), 'POST');
      $('#voucher-ticket').value = voucher.id;
      notice(`购券订单 ${voucher.id} 已创建，请在下方支付，付款后可在商品下单时抵扣。`, 'success');
    });
  };
  let activityCursor = '0';
  let activityEnd = false;
  let walletCursor = '0';
  let walletEnd = false;
  const names = {UNPAID:'待支付',PAY_EXPIRED:'支付已过期',AVAILABLE:'可使用',LOCKED:'订单占用中',USED:'已使用',EXPIRED:'已过期',CANCELLED:'已取消',UNAVAILABLE:'不可用'};
  $('#load-activities').onclick = e => run(e.currentTarget, async () => {
    if (preview) { notice('示例预览不读取活动。'); return; }
    const batch = list(await request('/api/vouchers?' + new URLSearchParams({afterId:activityCursor,limit:20})));
    for (const v of batch) $('#voucher-activities').insertAdjacentHTML('beforeend', `<article><b>${escapeHtml(v.title)}</b> · ${money(Number(v.payValue)/100)} 购 ${money(Number(v.actualValue)/100)} 券 · 库存 ${escapeHtml(v.stock)}<p>${escapeHtml(v.beginTime)} 至 ${escapeHtml(v.endTime)}，支付后 ${escapeHtml(v.validityDays)} 天内使用</p><button data-buy-voucher="${escapeHtml(v.id)}">抢购</button></article>`);
    if (batch.length) activityCursor = batch[batch.length - 1].id;
    $('#load-activities').textContent = '加载更多活动'; activityEnd = batch.length < 20;
  }).finally(() => { $('#load-activities').disabled = activityEnd; });
  $('#voucher-activities').onclick = e => {
    const button = e.target.closest('[data-buy-voucher]');
    if (!button || !mutationAllowed()) return;
    run(button, async () => {
      const ticket = await request('/api/voucher-order/seckill/' + positiveId(button.dataset.buyVoucher), 'POST');
      $('#voucher-ticket').value = ticket.orderId;
      notice('请求已受理，请查询抢购结果；成功后再支付购券订单。', 'success');
    });
  };
  $('#voucher-result').onclick = e => run(e.currentTarget, async () => {
    if (preview) return;
    const ticket = await request('/api/voucher-order/result/' + positiveId($('#voucher-ticket').value));
    notice(`抢购状态：${ticket.state}${ticket.reason ? ' · ' + ticket.reason : ''}`);
  });
  $('#pay-voucher').onclick = e => run(e.currentTarget, async () => {
    if (!mutationAllowed()) return;
    if (!window.WeixinJSBridge) throw new Error('请在微信内打开页面发起支付。');
    const parameters = await request('/api/voucher-order/' + positiveId($('#voucher-ticket').value) + '/pay', 'POST');
    await invokeWechatPayment(parameters);
    notice('支付已提交，请刷新我的券确认支付结果。', 'success');
  });
  const loadWallet = async reset => {
    if (preview) { notice('示例预览不读取个人优惠券。'); return; }
    if (reset) { walletCursor = '0'; $('#voucher-wallet').innerHTML = ''; }
    const batch = list(await request('/api/voucher-order/mine?' + new URLSearchParams({beforeId:walletCursor,limit:20})));
    for (const v of batch) $('#voucher-wallet').insertAdjacentHTML('beforeend', `<article>购券订单 ${escapeHtml(v.id)} · 抵扣面额 ${money(Number(v.actualValue)/100)} · ${escapeHtml(names[v.state] || v.state)}<p>${v.validUntil ? '使用截止 ' + escapeHtml(v.validUntil) : '待付款后生效'}${v.state === 'UNPAID' ? '；复制订单号到上方可支付' : ''}</p></article>`);
    if (batch.length) walletCursor = batch[batch.length - 1].id;
    walletEnd = batch.length < 20;
    $('#more-wallet').disabled = walletEnd;
  };
  $('#load-wallet').onclick = e => run(e.currentTarget, () => loadWallet(true));
  $('#more-wallet').onclick = e => run(e.currentTarget, () => loadWallet(false)).finally(() => { $('#more-wallet').disabled = walletEnd; });
}
function renderOrders() {
  $('#content').innerHTML = `<section class="panel"><div class="between"><h2>订单列表</h2><button id="load-orders" class="secondary">查询订单</button></div><div class="row"><label>订单分类<select id="order-tab"><option value="allPage">全部</option><option value="pendingPayPage">待付款</option><option value="packagingPage">打包中</option><option value="pendingReceiptPage">待收货</option><option value="pendingEvalPage">评价</option></select></label></div><div id="orders"><div class="empty">查询当前用户的订单。</div></div><div class="row"><button id="orders-more" class="secondary" disabled>加载更多</button><span id="orders-progress" role="status"></span></div></section><section class="panel"><h2>订单详情</h2><form id="order-query" class="row"><label class="grow">订单号<input name="orderNo" required placeholder="输入订单号"></label><button>查询详情</button></form><div id="order-detail" class="detail"></div><div id="refund-result" role="status"></div></section>`;
  const refundNames = {unpaid:'未支付',paid:'已支付',refundRequested:'申请退款，等待商家处理',refunding:'退款中',refundSuccess:'退款成功',refundRejected:'申请失败'};
  const showRefund = r => { $('#refund-result').textContent = r ? `退款单号：${r.refundNo}；金额：${money(r.totalAmount)}；状态：${refundNames[r.payStatus] || r.payStatus}；申请原因：${r.refundReason || ''}${r.refundFailReason ? '；' + (r.payStatus === 'refundRejected' ? '拒绝原因：' : '退款处理信息：') + r.refundFailReason : ''}${r.refundTime ? '；成功时间：' + r.refundTime : ''}` : '该订单暂无退款申请。'; };
  let refundableOrderNo = null;
  $('#order-detail').onsubmit = e => {
    if (e.target.id !== 'refund-apply') return;
    e.preventDefault();
    if (!mutationAllowed() || !refundableOrderNo) return;
    const orderNo = refundableOrderNo;
    run(e.submitter, async () => {
      const reason = String(new FormData(e.target).get('reason') || '').trim();
      if (!reason) throw new Error('请填写退款原因。');
      await request(API.orders + '/' + encodeURIComponent(orderNo) + '/refund', 'POST', {reason});
      await loadOrderDetail(orderNo);
      notice('整单退款申请已提交，请等待商家审核。', 'success');
    });
  };
  const sample = {orderNo:'DEMO202610200001',totalAmount:'31.80',status:1,createTime:'2026-10-20 10:00:00',address:{receiver:'示例收件人',detailAddress:'示例地址'},orderItems:[{productName:'原味栗仁',specText:'500g',price:'12.90',quantity:2}]};
  const statuses = {1:'待付款',2:'待确认',3:'待发货',4:'待收货',5:'已完成',6:'已取消',7:'售后',8:'已评价',9:'已追评',10:'已退款',pendingPayment:'待付款',pendingConfirm:'待确认',pendingShipment:'待发货',pendingReceipt:'待收货',completed:'已完成',cancelled:'已取消',afterSale:'售后',evaluated:'已评价',reviewed:'已追评',refunded:'已退款'};
  const paint = items => {
    $('#orders').innerHTML = items.length ? items.map(o => `<article class="order-card"><header class="between"><strong>订单 ${escapeHtml(o.orderNo)}</strong><span>${escapeHtml(statuses[o.status] || '未知状态')}</span></header>${(o.orderItems || []).map(i => {
      const src = i.productImage ? imageUrl(i.productImage) : '';
      return `<div class="order-card-item">${src ? `<img src="${escapeHtml(src)}" alt="${escapeHtml(i.productName)}" loading="lazy">` : '<div class="order-image-placeholder" aria-label="暂无商品图片">栗</div>'}<div class="grow"><strong>${escapeHtml(i.productName)}</strong><p>${escapeHtml(i.specText || '')}</p><span>${money(i.price)} · 数量 ${escapeHtml(i.quantity)}</span></div></div>`;
    }).join('')}<footer class="between"><span>${escapeHtml(o.createTime)} · ${escapeHtml(refundNames[o.payStatus] || '未支付')}</span><strong>应付 ${money(o.totalAmount)}</strong><button class="secondary" data-order-detail="${escapeHtml(o.orderNo)}">查看订单</button></footer></article>`).join('') : '<div class="empty">暂无订单。</div>';
  };
  let orderCursor = null, hasMoreOrders = false, activeTab = 'allPage', loadingOrders = false, visibleOrders = [], ordersLoaded = false;
  const updatePaging = () => {
    $('#orders-more').disabled = loadingOrders || !hasMoreOrders;
    $('#orders-progress').textContent = loadingOrders ? '正在加载…' : ordersLoaded
      ? `已加载 ${visibleOrders.length} 条订单${hasMoreOrders ? '' : '，已全部加载'}` : '';
  };
  const loadOrders = async (reset, pageName) => {
    if (loadingOrders || (!reset && !hasMoreOrders)) return;
    loadingOrders = true;
    $('#order-tab').disabled = true;
    $('#load-orders').disabled = true;
    updatePaging();
    try {
      const params = new URLSearchParams({pageName, pageSize: 10});
      if (!reset && orderCursor != null) params.set('beginId', orderCursor);
      const matches = pageName === 'allPage' || pageName === 'pendingPayPage';
      const result = preview ? {list: matches ? [sample] : [], endId: matches ? '1' : null, hasMore: false}
        : await request(API.orders + '/list?' + params);
      visibleOrders = reset ? (result.list || []) : visibleOrders.concat(result.list || []);
      orderCursor = result.endId;
      hasMoreOrders = result.hasMore === true;
      activeTab = pageName;
      ordersLoaded = true;
      paint(visibleOrders);
      notice('订单查询成功。', 'success');
    } finally {
      loadingOrders = false;
      $('#order-tab').disabled = false;
      $('#order-tab').value = activeTab;
      $('#load-orders').disabled = false;
      updatePaging();
    }
  };
  $('#load-orders').onclick = () => run(null, () => loadOrders(true, $('#order-tab').value));
  $('#order-tab').onchange = e => run(null, () => loadOrders(true, e.target.value));
  $('#orders-more').onclick = () => run(null, () => loadOrders(false, activeTab));
  if (preview) { visibleOrders = [sample]; ordersLoaded = true; paint(visibleOrders); updatePaging(); }
  $('#orders').onclick = e => {
    const button = e.target.closest('[data-order-detail]');
    if (button) run(button, () => loadOrderDetail(button.dataset.orderDetail));
  };
  const loadOrderDetail = async orderNo => {
    refundableOrderNo = null;
    $('#order-detail').innerHTML = '<p>正在查询订单…</p>';
    const o = preview ? sample : await request(API.orders + '/detail?' + new URLSearchParams({orderNo}));
    const a = o.address || {};
    const canRefund = o.payStatus === 'paid' || o.payStatus === 'refundRejected';
    refundableOrderNo = canRefund ? o.orderNo : null;
    showRefund(o.refundNo ? o : null);
    $('#order-detail').innerHTML = `<p><b>${escapeHtml(o.orderNo)}</b> · ${escapeHtml(statuses[o.status] || '未知状态')} · ${escapeHtml(refundNames[o.payStatus] || '未支付')}</p><p>${escapeHtml(a.receiver)} / ${escapeHtml(a.phone || '')}<br>${escapeHtml([a.province,a.city,a.district,a.detailAddress].filter(Boolean).join(' '))}</p><p>商品金额 ${money(o.totalGoodsAmount)} · 运费 ${money(o.freight)} · 优惠 ${money(o.voucherDiscountAmount || 0)}</p><p class="price">应付 ${money(o.totalAmount)}</p><div class="table-wrap"><table><thead><tr><th>商品 / 规格</th><th>成交单价</th><th>数量</th><th>小计</th></tr></thead><tbody>${(o.orderItems || []).map(i => `<tr><td>${escapeHtml(i.productName)} / ${escapeHtml(i.specText)}</td><td>${money(i.price)}</td><td>${escapeHtml(i.quantity)}</td><td>${money(Number(i.price)*Number(i.quantity))}</td></tr>`).join('')}</tbody></table></div>${canRefund ? `<form id="refund-apply" class="row"><p>申请退还整单原实付 ${money(o.totalAmount)}（包含运费、已扣优惠），不支持按商品或数量部分退款。零元订单审核通过后返还原券，不涉及现金退款。</p><label class="grow">退款原因<input name="reason" maxlength="80" required></label><button>申请整单退款</button></form>` : ''}`;
  };
  $('#order-query').onsubmit = e => { e.preventDefault(); run(e.submitter, async () => {
    await loadOrderDetail(String(new FormData(e.target).get('orderNo')).trim());
    notice('订单详情查询成功。', 'success');
  }); };
}
modeLabel(); render();
