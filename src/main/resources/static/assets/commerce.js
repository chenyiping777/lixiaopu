'use strict';

// 增量补齐商城交互，基础下单、退款与游标分页仍由 app.js 负责。
function enhanceCommerce() {
  if (window.commerceObserver) { window.commerceObserver.disconnect(); window.commerceObserver = null; }
  const content = $('#content');
  const settings = document.querySelector('.settings');
  $('#debug').before(settings);
  const h = escapeHtml;
  const query = (path, values) => path + '?' + new URLSearchParams(values);
  const fields = entries => entries.map(([name, label, type = 'text', value = '']) => `<label>${label}<input name="${name}" type="${type}" value="${h(value)}" ${name === 'code' ? 'minlength="5" maxlength="5" pattern="(?=.*[A-Z])(?=.*[0-9])[A-Z0-9]{5}" title="请输入 5 位大写字母与数字混合验证码，至少包含一个字母和一个数字" autocomplete="one-time-code" autocapitalize="characters"' : ''} required></label>`).join('');
  const actionForm = (id, title, entries, submit) => `<section class="panel"><h2>${title}</h2><form id="${id}" class="form-grid">${fields(entries)}<div class="span-all"><button>${submit}</button></div></form></section>`;
  const bind = (id, action, mutation = true) => {
    const form = document.getElementById(id);
    form.onsubmit = e => { e.preventDefault(); if (mutation && !mutationAllowed()) return;
      if (preview) { notice('示例预览不提交或查询个人数据。'); return; }
      run(e.submitter, () => action(Object.fromEntries(new FormData(form)), form));
    };
  };
  const show = data => notice(typeof data === 'string' ? data : JSON.stringify(data ?? '操作成功'), 'success');

  if (page === 'home' || page === 'products') {
    const root = page === 'home' ? $('#home-categories') : document.createElement('section');
    if (page === 'products') { root.className = 'category-shortcuts'; content.prepend(root); }
    run(null, async () => {
      const categories = preview ? [{id:1,name:'栗子零食',children:[]}] : list(await request('/api/category/tree'));
      root.innerHTML = categories.map(c => `<a href="products.html?categoryId=${encodeURIComponent(c.id)}&first=true"><span>${h(c.name?.slice(0,1) || '栗')}</span>${h(c.name)}</a>`).join('');
      if (page === 'products') {
        const select = document.createElement('select'); select.setAttribute('aria-label', '选择商品分类');
        select.innerHTML = '<option value="">全部分类</option>' + categories.flatMap(c => [
          `<option value="${h(c.id)}" data-first="true">${h(c.name)}</option>`,
          ...(c.children || []).map(s => `<option value="${h(s.id)}">　${h(s.name)}</option>`)]).join('');
        const input = $('#search [name="categoryId"]'); input.replaceWith(select); select.name = 'categoryId';
        // 当前服务分别提供分类查询和关键词查询，两个条件在界面中互斥，避免静默忽略关键词。
        select.onchange = () => { $('#search [name="keyword"]').value = ''; };
        $('#search [name="keyword"]').oninput = () => { select.value = ''; };
        const params = new URLSearchParams(location.search);
        if (params.get('categoryId')) select.value = params.get('categoryId');
        if (params.get('keyword')) $('#search [name="keyword"]').value = params.get('keyword');
        if (params.has('categoryId') || params.has('keyword')) $('#search').requestSubmit();
      }
    });
  }
  if (page === 'account') {
    content.insertAdjacentHTML('afterbegin', `<section class="member-banner"><span class="member-avatar">栗</span><div><small>我的栗小铺</small><h2>${token ? '欢迎回来，老朋友' : '登录，遇见更多好物'}</h2><p><a href="orders.html">我的订单 ↗</a>　<a href="vouchers.html">我的券包 ↗</a>　<a href="addresses.html">收货地址 ↗</a></p></div></section>`);
    content.insertAdjacentHTML('beforeend', actionForm('sms-login', '短信快捷登录', [['phone','手机号','tel'],['code','验证码（5 位大写字母与数字混合）']], '登录')
      + '<button id="send-login-sms" type="button" class="secondary">发送登录验证码</button>'
      + actionForm('reset-verify', '找回密码', [['phone','手机号','tel'],['code','验证码（5 位大写字母与数字混合）']], '验证身份')
      + '<button id="send-reset-sms" type="button" class="secondary">发送找回密码验证码</button>'
      + actionForm('reset-password', '设置新密码', [['ticket','验证凭证'],['password','新密码（8～72 位）','password']], '重置密码')
      + actionForm('change-password', '修改密码', [['oldPassword','旧密码','password'],['newPassword','新密码（8～72 位）','password']], '修改密码')
      + `<section class="panel"><h2>上传头像</h2><form id="avatar-upload"><label>选择图片<input name="file" type="file" accept="image/*" required></label><button>上传并填入资料</button></form></section>`
      + `<section class="panel"><div class="between"><h2>我的收藏</h2><button id="collections-load">刷新收藏</button></div><div id="collections" class="cards"></div><button id="collections-more" class="secondary" hidden>加载更多收藏</button></section>`);
    const save = data => {
      if (!data?.accessToken) throw Error('登录响应缺少访问令牌');
      token = data.accessToken; sessionStorage.setItem('lxp.token', token);
      for (const key of ['refreshToken','userId','sessionId']) sessionStorage.setItem('lxp.' + key, data[key] || '');
      modeLabel(); notice('登录成功。', 'success');
    };
    bind('sms-login', async body => save(await request(API.login, 'POST', body)));
    for (const [id, formId, purpose] of [['send-login-sms','sms-login','LOGIN'],['send-reset-sms','reset-verify','RESET_PASSWORD']]) {
      document.getElementById(id).onclick = e => run(e.currentTarget, async () => {
        if (!mutationAllowed()) return;
        const data = await request(API.sms, 'POST', {phone:document.getElementById(formId).elements.phone.value, purpose});
        notice(data?.localTestCode ? `本地测试验证码：${data.localTestCode}` : '验证码已发送。', 'success');
      });
    }
    bind('reset-verify', async body => { const data = await request('/api/auth/reset/verify','POST',body); $('#reset-password [name="ticket"]').value = data.ticket; notice('验证成功，请输入新密码。'); });
    bind('reset-password', async body => { await request('/api/auth/reset/password','POST',body); $('#reset-password').reset(); notice('密码已重置，请重新登录。'); });
    bind('change-password', async body => { await request('/api/auth/change-password','POST',body); $('#change-password').reset(); notice('密码已修改，请重新登录。'); });
    bind('avatar-upload', async (_, form) => {
      const url = await uploadImage('/api/upload/image', new FormData(form));
      $('#profile-form [name="avatar"]').value = url; notice('图片已上传，保存个人资料后生效。');
    });
    let number = 1;
    const loadCollections = async reset => {
      if (preview) return;
      const next = reset ? 1 : number + 1;
      const data = await request(query('/api/user/collection/list', {pageNo:next,pageSize:12}));
      if (reset) $('#collections').innerHTML = '';
      const batch = list(data);
      $('#collections').insertAdjacentHTML('beforeend', batch.map(p => `<article class="product"><a href="products.html?id=${encodeURIComponent(p.productId)}"><div class="product-art">${productArt({image:p.productImage,name:p.productName})}</div><div class="product-body"><h3>${h(p.productName || '商品已下架')}</h3><p class="price">${money(p.productPrice)}</p></div></a><button class="secondary" data-uncollect="${h(p.productId)}">取消收藏</button></article>`).join('') || (reset ? '<div class="empty">还没有收藏，去挑选喜欢的商品吧。</div>' : ''));
      number = next; $('#collections-more').hidden = number >= Number(data.pages || 1);
    };
    $('#collections-load').onclick = e => run(e.currentTarget, () => loadCollections(true));
    $('#collections-more').onclick = e => run(e.currentTarget, () => loadCollections(false));
    $('#collections').onclick = e => { const button = e.target.closest('[data-uncollect]'); if (!button || !mutationAllowed()) return;
      run(button, async () => { await request('/api/user/collection/delete','DELETE',button.dataset.uncollect); await loadCollections(true); }); };
  }
  if (page === 'addresses') {
    const form = $('#address-form');
    form.insertAdjacentHTML('afterbegin','<input name="id" type="hidden">');
    form.insertAdjacentHTML('beforeend','<button type="button" id="address-new" class="secondary">新建地址</button>');
    let addresses = [];
    const load = async () => {
      if (preview) return;
      addresses = list(await request('/api/address/list'));
      $('#addresses').innerHTML = addresses.map(a => `<article class="address-card"><b>${h(a.receiverName || a.receiver)}</b>　${h(a.receiverPhone || a.phone)} ${a.isDefault ? '<span class="product-tag">默认</span>' : ''}<p>${h([a.province,a.city,a.district,a.detailAddress].filter(Boolean).join(' '))}</p><button class="secondary" data-edit-address="${h(a.id)}">编辑</button> <button class="secondary" data-delete-address="${h(a.id)}">删除</button></article>`).join('') || '<div class="empty">暂无收货地址。</div>';
    };
    $('#load-addresses').onclick = e => run(e.currentTarget, load);
    $('#address-new').onclick = () => { form.reset(); form.elements.id.value = ''; };
    $('#addresses').onclick = e => {
      const button = e.target.closest('button'); if (!button || !mutationAllowed()) return;
      if (button.dataset.editAddress) {
        const a = addresses.find(a => String(a.id) === button.dataset.editAddress);
        for (const name of ['id','receiverName','receiverPhone','province','city','district','detailAddress']) form.elements[name].value = a[name] ?? (name === 'receiverName' ? a.receiver : name === 'receiverPhone' ? a.phone : '') ?? '';
        form.elements.isDefault.checked = Boolean(a.isDefault); form.scrollIntoView({behavior:'smooth'});
      } else if (window.confirm('确认删除这个收货地址？')) run(button, async () => { await request(query('/api/address/delete',{id:button.dataset.deleteAddress}), 'DELETE'); await load(); });
    };
    bind('address-form', async (body, form) => {
      body.isDefault = form.elements.isDefault.checked;
      const update = Boolean(body.id); if (!update) delete body.id;
      await request('/api/address/' + (update ? 'update' : 'add'), update ? 'PUT' : 'POST', body);
      form.reset(); form.elements.id.value = ''; await load(); notice('收货地址已保存。','success');
    });
    if (token && !preview) run(null, load);
  }
  if (page === 'cart') {
    const form = $('#checkout');
    const originalAddress = form.elements.addressId;
    const select = document.createElement('select'); select.name = 'addressId'; select.required = true;
    select.innerHTML = '<option value="">请选择收货地址</option>'; originalAddress.replaceWith(select);
    form.insertAdjacentHTML('beforeend','<a class="inline-link" href="addresses.html">管理地址</a><button id="freight" type="button" class="secondary">估算运费</button>');
    $('#cart-list').parentElement.insertAdjacentHTML('beforeend','<div class="row cart-tools"><label class="check"><input id="cart-select-all" type="checkbox">全选</label><strong id="cart-total">已选商品 ¥0.00</strong><button id="cart-remove-selected" class="secondary">删除勾选</button><button id="cart-clear" class="secondary">清空购物车</button><button id="cart-save-all" class="secondary">保存所有数量</button></div>');
    const selected = () => [...document.querySelectorAll('[data-select]:checked')].map(el => cartItems.find(i => cartEntryKey(i) === el.dataset.select)).filter(Boolean);
    const updateTotal = () => {
      const items = selected(), boxes = [...document.querySelectorAll('[data-select]')];
      $('#cart-total').textContent = `已选 ${items.length} 件 · 商品 ${money(items.reduce((total,i)=>total+Number(i.price)*Number(i.quantity),0))}`;
      $('#cart-select-all').checked = boxes.length > 0 && boxes.every(box => box.checked);
    };
    window.commerceObserver = new MutationObserver(updateTotal);
    window.commerceObserver.observe($('#cart-list'), {childList:true,subtree:true});
    $('#cart-list').addEventListener('change', updateTotal);
    $('#cart-select-all').onchange = e => { for (const box of document.querySelectorAll('[data-select]')) { box.checked = e.target.checked; box.dispatchEvent(new Event('change',{bubbles:true})); } updateTotal(); };
    $('#freight').onclick = e => run(e.currentTarget, async () => {
      if (preview) return;
      if (!selected().length || !select.value) throw Error('请勾选商品并选择收货地址。');
      show(await request(query('/api/order/freight',{productIds:[...new Set(selected().map(i=>i.productId))].join(','),addressId:select.value})));
    });
    const refreshCart = () => $('#load-cart').click();
    $('#cart-remove-selected').onclick = e => run(e.currentTarget, async () => {
      if (!mutationAllowed()) return; const items = selected(); if (!items.length) throw Error('请先勾选商品。');
      await request(query('/api/cart/products',{productIds:items.map(i=>i.productId).join(','),specIds:items.map(i=>i.specId).join(',')}),'DELETE'); refreshCart();
    });
    $('#cart-clear').onclick = e => { if (mutationAllowed() && window.confirm('确认清空购物车？')) run(e.currentTarget, async () => { await request('/api/cart/clear','DELETE'); refreshCart(); }); };
    $('#cart-save-all').onclick = e => run(e.currentTarget, async () => {
      if (!mutationAllowed()) return;
      const items = [...document.querySelectorAll('[data-qty]')].map(input => {
        if (!input.reportValidity() || !input.value) throw Error('请检查商品数量。');
        const item = cartItems.find(i=>cartEntryKey(i)===input.dataset.qty);
        return {productId:item.productId,specId:item.specId,quantity:Number(input.value)};
      });
      await request('/api/cart/update','PUT',{cartItems:items}); refreshCart();
    });
    if (token && !preview) run(null, async () => {
      const addresses = list(await request('/api/address/list'));
      select.innerHTML += addresses.map(a => `<option value="${h(a.id)}" ${a.isDefault ? 'selected' : ''}>${h(a.receiverName || a.receiver)} · ${h([a.province,a.city,a.district,a.detailAddress].filter(Boolean).join(' '))}</option>`).join('');
      refreshCart();
    });
  }
  if (page === 'orders') {
    content.insertAdjacentHTML('afterbegin', '<section class="panel"><form id="order-search" class="row"><label class="grow">搜索订单<input name="searchCondition" placeholder="订单号或商品名称" required></label><button>搜索</button></form><div id="order-search-results"></div></section>');
    bind('order-search', async body => { const data = list(await request(query('/api/order/search',body))); $('#order-search-results').innerHTML = data.map(o=>`<p><a class="inline-link" href="orders.html?orderNo=${encodeURIComponent(o.orderNo)}">${h(o.orderNo)}</a> · ${money(o.totalAmount)}</p>`).join('') || '<p>没有找到订单。</p>'; }, false);
    $('#order-detail').insertAdjacentHTML('afterend', `<div class="row order-actions">${[['pay','微信支付'],['cancel','取消订单'],['receipt','确认收货'],['logistics','查看物流'],['delete','删除订单']].map(([action,label])=>`<button type="button" data-order-action="${action}" class="secondary">${label}</button>`).join('')}</div><div id="order-action-result"></div>`);
    const routes = {cancel:['/api/order/cancel','PUT'],receipt:['/api/order/confirmReceipt','PUT'],logistics:['/api/order/logistics','GET'],delete:['/api/order/delete','DELETE']};
    document.querySelector('.order-actions').onclick = e => {
      const button = e.target.closest('[data-order-action]'); if (!button || !mutationAllowed()) return;
      run(button, async () => {
        const orderNo = $('#order-query').elements.orderNo.value.trim(); if (!orderNo) throw Error('请先输入并查询订单号。');
        const action = button.dataset.orderAction;
        if (action === 'pay') { if (!window.WeixinJSBridge) throw Error('请在微信内打开页面支付。'); await invokeWechatPayment(await request('/api/pay/wxpay','POST',{orderNo})); notice('支付已提交，请刷新订单确认结果。'); }
        else {
          if (['cancel','delete','receipt'].includes(action) && !window.confirm(`确认${button.textContent}？`)) return;
          const [path,method] = routes[action]; const body = {orderNo}; if (action === 'cancel') body.cancelReason = '用户主动取消';
          const data = await request(query(path,body),method);
          $('#order-action-result').textContent = action === 'logistics' ? JSON.stringify(data ?? '暂无物流信息') : '操作成功，请刷新订单查看最新状态。';
        }
      });
    };
    $('#orders').addEventListener('click', e => { const button = e.target.closest('[data-order-detail]'); if (button) $('#order-query').elements.orderNo.value = button.dataset.orderDetail; });
    const orderNo = new URLSearchParams(location.search).get('orderNo');
    if (orderNo) { $('#order-query').elements.orderNo.value = orderNo; $('#order-query').requestSubmit(); }
    if (token && !preview) $('#load-orders').click();
  }
  if (page === 'products') enhanceProductInteractions();
  if (page === 'home') enhanceHomeDiscovery();
  if (page === 'vouchers') {
    content.insertAdjacentHTML('beforeend', actionForm('voucher-detail-query','优惠券使用说明',[['id','优惠券编号']], '查看详情') + '<div class="panel" id="voucher-detail-result" hidden></div>');
    bind('voucher-detail-query', async body => { const v = await request('/api/vouchers/' + positiveId(body.id)); $('#voucher-detail-result').hidden = false; $('#voucher-detail-result').textContent = `${v.title} · ${v.subTitle || ''} · ${v.rules || ''} · 抵扣 ${money(Number(v.actualValue)/100)}`; }, false);
    if (!preview) { $('#load-normal-vouchers').click(); $('#load-activities').click(); if (token) $('#load-wallet').click(); }
  }
}

async function uploadImage(path, form) {
  const response = await fetch(baseUrl + path,{method:'POST',headers:token ? {Authorization:'Bearer '+token} : {},body:form,signal:AbortSignal.timeout(25000)});
  const data = await response.json(); if (!response.ok || data.code !== 200 || !data.success) throw Error(data.message || '上传失败');
  return data.data;
}

function enhanceProductInteractions() {
  const h = escapeHtml;
  const root = $('#product-detail');
  let productId = null, commentCursor = null, comments = [], loading = false;
  const path = '/api/products/user/product/comment/';
  const query = (route, body) => path + route + '?' + new URLSearchParams(body);
  const observer = new MutationObserver(() => {
    const add = root.querySelector('[data-add-spec]');
    // 无规格商品也可收藏和查看评价，通过详情容器上的 data-product-id 取得编号。
    const id = add?.dataset.addSpec || root.querySelector('[data-product-id]')?.dataset.productId;
    if (!id || root.querySelector('#product-social')) return;
    productId = id; comments = []; commentCursor = null;
    root.insertAdjacentHTML('beforeend', `<section id="product-social" class="panel"><div class="row"><button id="collect-product" class="secondary">♡ 收藏商品</button><button id="uncollect-product" class="secondary">取消收藏</button><button id="related-products" class="secondary">相似好物</button></div><div id="related-list" class="cards"></div><h2>买家评价 <small id="comment-count"></small></h2><div class="row"><select id="comment-sort" aria-label="评价筛选"><option value="default">全部评价</option><option value="isGoodReview">好评</option><option value="isAppendComment">有追评</option></select><button id="comments-load" class="secondary">查看评价</button><button id="comments-more" class="secondary" hidden>更多评价</button></div><div id="comment-list"></div><form id="comment-editor" class="form-grid"><label>操作<select name="kind"><option value="firstComment/save">发表购买评价</option><option value="secondComment/save">回复评价</option><option value="firstComment/append">追加评价</option></select></label><label>订单号<input name="orderNo"></label><label>商品规格 ID<input name="productSpecId"></label><label>规格名称<input name="productSpecText"></label><label>回复的一级评价 ID<input name="parentId"></label><label>评分<select name="rating">${[5,4,3,2,1].map(n=>`<option>${n}</option>`).join('')}</select></label><label class="span-all">评价内容<textarea name="content" maxlength="500" required></textarea></label><label>图片地址（逗号分隔）<input name="imageUrls"></label><label class="check"><input name="isAnonymous" type="checkbox">匿名评价</label><button>提交评价</button></form><div id="comment-thread"></div></section>`);
    $('#collect-product').onclick = e => run(e.currentTarget, async () => { if (mutationAllowed()) { await request('/api/user/collection/add','POST',positiveId(productId)); notice('已收藏。','success'); } });
    $('#uncollect-product').onclick = e => run(e.currentTarget, async () => { if (mutationAllowed()) { await request('/api/user/collection/delete','DELETE',productId); notice('已取消收藏。'); } });
    $('#related-products').onclick = e => run(e.currentTarget, async () => {
      if (preview) return;
      const name = root.querySelector('h2').textContent;
      const batch = list(await request('/api/products/product/related?' + new URLSearchParams({productName:name,limit:6})));
      $('#related-list').innerHTML = batch.map(p=>`<a class="product" href="products.html?id=${encodeURIComponent(p.id)}"><div class="product-art">${productArt(p)}</div><div class="product-body">${h(p.name)}<p class="price">${money(p.price)}</p></div></a>`).join('');
    });
    const load = async reset => {
      if (preview || loading) return;
      loading = true;
      try {
        const cursor = reset ? {sortType:$('#comment-sort').value,querySize:10} : commentCursor;
        const data = await request(query('firstComment/show',{productId}),'POST',cursor);
        const batch = data.list || []; comments = reset ? batch : comments.concat(batch); commentCursor = data.cursorCommonEntity;
        $('#comment-list').innerHTML = comments.map(c=>`<article class="review"><b>${h(c.userNickname || '匿名买家')}</b> <span>${h(c.rating)} / 5</span><p>${h(c.content)}</p><small>${h(c.createTime)}</small><div class="row"><button class="secondary" data-comment-like="${h(c.id)}" data-is-first="1">赞 ${h(c.likeCount || 0)}</button><button class="secondary" data-comment-unlike="${h(c.id)}" data-is-first="1">取消赞</button><button class="secondary" data-thread="${h(c.id)}">查看回复</button><button class="secondary" data-append="${h(c.id)}">查看追评</button><button class="secondary" data-reply="${h(c.id)}">回复</button></div></article>`).join('') || '<p>暂无评价。</p>';
        $('#comments-more').hidden = data.isEnd === true || !commentCursor;
        $('#comment-count').textContent = '共 ' + await request(query('count/show',{productId})) + ' 条';
      } finally { loading = false; }
    };
    $('#comments-load').onclick = e => run(e.currentTarget, () => load(true));
    $('#comments-more').onclick = e => run(e.currentTarget, () => load(false));
    let threadCursor = null, activeThread = null;
    const loadThread = async (id, more = false) => {
      const data = await request(query('secondComment/show',{firstCommentId:id}),'POST',more ? threadCursor : {sortType:'default',querySize:10});
      activeThread = id; threadCursor = data.cursorCommonEntity;
      if (!more) $('#comment-thread').innerHTML = '<h3>评价回复</h3><div id="thread-items"></div><button class="secondary" id="thread-more">加载更多回复</button>';
      $('#thread-items').insertAdjacentHTML('beforeend',(data.list || []).map(c=>`<article class="review"><b>${h(c.userNickname)}</b><p>${h(c.content)}</p><button class="secondary" data-comment-like="${h(c.id)}" data-is-first="0">点赞</button> <button class="secondary" data-comment-unlike="${h(c.id)}" data-is-first="0">取消赞</button></article>`).join(''));
      $('#thread-more').hidden = data.isEnd === true || !threadCursor;
      $('#thread-more').onclick = e => run(e.currentTarget,()=>loadThread(activeThread,true));
    };
    $('#product-social').onclick = e => {
      const button = e.target.closest('button'); if (!button || preview) return;
      if (button.dataset.reply) { $('#comment-editor').elements.kind.value = 'secondComment/save'; $('#comment-editor').elements.parentId.value = button.dataset.reply; return; }
      if (button.dataset.thread) run(button,()=>loadThread(button.dataset.thread));
      if (button.dataset.append) run(button,async()=>{ const data = await request(query('appendComment/show',{firstCommentId:button.dataset.append})); $('#comment-thread').textContent = data?.content || '暂无追评'; });
      const id = button.dataset.commentLike || button.dataset.commentUnlike;
      if (id && mutationAllowed()) run(button,async()=>{ await request(query('like',{productCommentId:id,isLike:button.dataset.commentLike ? 1 : 0,isFirstComment:button.dataset.isFirst}),'PUT'); notice('点赞状态已更新。'); });
    };
    $('#comment-editor').onsubmit = e => { e.preventDefault(); if (!mutationAllowed()) return;
      run(e.submitter,async()=>{
        const form = e.target, body = Object.fromEntries(new FormData(form)), kind = body.kind; delete body.kind;
        const profile = await request(API.profile);
        Object.assign(body,{productId,userNickname:profile.nickname || '买家',userAvatar:profile.avatar || location.origin + '/assets/chestnuts.svg',rating:Number(body.rating),isAnonymous:form.elements.isAnonymous.checked ? 1 : 0});
        if (kind === 'secondComment/save' && !body.parentId) throw Error('请先选择要回复的评价。');
        await request(path + kind,'POST',body); notice('评价已提交。','success'); await load(true);
      });
    };
  });
  observer.observe(root,{childList:true,subtree:true});
  window.commerceObserver = observer;
}

function enhanceHomeDiscovery() {
  const h = escapeHtml;
  $('#content').insertAdjacentHTML('beforeend', '<section class="panel"><div class="between"><h2>发现更多好物</h2><div class="row"><button id="hot-picks" class="secondary">热卖榜</button><button id="discover-more" class="secondary">随心逛逛</button></div></div><div id="discover-list" class="cards"></div></section>');
  let beginId = null, ended = false;
  const paint = items => items.map(p => `<a class="product" href="products.html?id=${encodeURIComponent(p.id)}"><div class="product-art">${productArt(p)}</div><div class="product-body"><h3>${h(p.name)}</h3><p class="price">${money(p.price)}</p></div></a>`).join('');
  $('#hot-picks').onclick = e => run(e.currentTarget, async () => {
    const data = preview ? demoProducts : list(await request('/api/products/product/hot?limit=8'));
    $('#discover-list').innerHTML = paint(data) || '<p>暂无热卖商品。</p>';
  });
  $('#discover-more').onclick = e => run(e.currentTarget, async () => {
    if (ended) return;
    const params = new URLSearchParams({querySize:8}); if (beginId) params.set('beginId', beginId);
    const data = preview ? {list:demoProducts,isEnd:true} : await request('/api/products/product/scroll/query/list?' + params);
    $('#discover-list').insertAdjacentHTML('beforeend', paint(list(data)));
    beginId = data.simpleCursorCommonEntity?.sortId; ended = data.isEnd === true || !beginId;
    $('#discover-more').textContent = ended ? '已经逛完了' : '继续逛逛';
  }).finally(() => { $('#discover-more').disabled = ended; });
  if (!preview) run(null, async () => {
    const words = list(await request('/api/products/product/user/keyword/list'));
    const host = document.createElement('div'); host.className = 'search-words';
    host.innerHTML = words.slice(0,8).map(word => { const text = typeof word === 'string' ? word : word.keyword; return text ? `<a href="products.html?keyword=${encodeURIComponent(text)}">${h(text)}</a>` : ''; }).join('');
    document.querySelector('.mall-search').after(host);
  });
}
