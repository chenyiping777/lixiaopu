'use strict';

window.StorefrontMath = {
  cents(value) { return Math.round(Number(value || 0) * 100); },
  summary(items, freight = 0, voucherCents = 0) {
    const goods = items.reduce((sum, item) => sum + this.cents(item.price) * Number(item.quantity), 0);
    const discount = Math.min(goods, Math.max(0, Number(voucherCents) || 0));
    return {goods, discount, freight: this.cents(freight), total: goods - discount + this.cents(freight)};
  }
};
function buyerGate() {
  if (token) return true;
  $('#content').innerHTML = '<section class="panel login-required"><h2>登录后继续</h2><p>登录后查看你的订单、购物车和收货地址。</p><a class="button-link" href="account.html?returnTo=' + encodeURIComponent(location.pathname.split('/').pop() + location.search) + '">去登录</a> <a class="inline-link" href="products.html">先逛逛商品</a></section>';
  return false;
}
function buyerSaveSession(data) {
  if (!data?.accessToken) throw Error('登录未完成，请重试。');
  token=data.accessToken; sessionStorage.setItem('lxp.token',token);
  for(const key of ['refreshToken','userId','sessionId'])sessionStorage.setItem('lxp.'+key,data[key]||'');
}
function buyerAfterLogin() {
  const target=new URLSearchParams(location.search).get('returnTo');
  if(target&&/^(cart|orders|addresses|vouchers|products)\.html(?:\?[^#]*)?$/.test(target)){location.assign(target);return;}
  render();notice('登录成功，欢迎回来。','success');
}
function buyerField(name,label,type='text',extra='') { return `<label>${label}<input name="${name}" type="${type}" ${extra} required></label>`; }
const buyerSmsField=()=>buyerField('code','短信验证码','text','inputmode="numeric" minlength="6" maxlength="6" pattern="[0-9]{6}" autocomplete="one-time-code" placeholder="6 位数字验证码"');
function buyerForm(id,action) {const form=document.getElementById(id);form.onsubmit=e=>{e.preventDefault();run(e.submitter,()=>action(Object.fromEntries(new FormData(form)),form));};}
function buyerSms(button,formId,purpose) {
  button.onclick=()=>run(button,async()=>{
    const phone=document.getElementById(formId).elements.phone.value;
    if(!/^1[3-9]\d{9}$/.test(phone))throw Error('请填写正确的手机号。');
    await request(API.sms,'POST',{phone,purpose});notice('验证码已发送，请查看手机短信。','success');
    let remaining=60;
    const timer=setInterval(()=>{if(!button.isConnected){clearInterval(timer);return;}remaining--;button.textContent=remaining?`${remaining} 秒后重发`:'发送验证码';button.disabled=remaining>0;if(!remaining)clearInterval(timer);},1000);
    setTimeout(()=>{if(button.isConnected){button.disabled=true;button.textContent='60 秒后重发';}},0);
  });
}
function renderBuyerAccount() {
  if(!token){
    $('#content').innerHTML=`<section class="panel account-auth"><h2>欢迎来到栗小铺</h2><p>登录，收藏日常的小美好。</p><div class="customer-tabs" role="tablist" aria-label="账户操作"><button data-auth-tab="login" role="tab" aria-selected="true">登录</button><button data-auth-tab="register" role="tab" aria-selected="false">注册</button><button data-auth-tab="reset" role="tab" aria-selected="false">找回密码</button></div><div data-auth-pane="login"><form id="buyer-login" class="form-grid">${buyerField('phone','手机号','tel','maxlength="11" autocomplete="tel"')}${buyerField('password','密码','password','autocomplete="current-password"')}<label>登录方式<select id="buyer-login-mode"><option value="password">密码登录</option><option value="sms">短信登录</option></select></label><label hidden id="buyer-code-label">短信验证码<input name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" placeholder="6 位数字验证码"></label><button hidden id="buyer-login-sms" type="button" class="secondary">发送验证码</button><button>登录</button></form></div><div data-auth-pane="register" hidden><form id="buyer-register" class="form-grid">${buyerField('username','用户名','text','minlength="3" maxlength="64" pattern="[A-Za-z0-9_]{3,64}"')}${buyerField('phone','手机号','tel','maxlength="11"')}${buyerField('password','密码（8～72 位）','password','minlength="8" maxlength="72" autocomplete="new-password"')}${buyerSmsField()}<button type="button" class="secondary" id="buyer-register-sms">发送验证码</button><button>注册账号</button></form></div><div data-auth-pane="reset" hidden><form id="buyer-reset-verify" class="form-grid">${buyerField('phone','手机号','tel','maxlength="11"')}${buyerSmsField()}<button type="button" class="secondary" id="buyer-reset-sms">发送验证码</button><button>验证手机号</button></form><form id="buyer-reset-password" class="form-grid" hidden>${buyerField('password','新密码（8～72 位）','password','minlength="8" maxlength="72" autocomplete="new-password"')}<button>设置新密码</button></form></div></section>`;
    document.querySelectorAll('[data-auth-tab]').forEach(b=>b.onclick=()=>{document.querySelectorAll('[data-auth-pane]').forEach(p=>p.hidden=p.dataset.authPane!==b.dataset.authTab);document.querySelectorAll('[data-auth-tab]').forEach(t=>t.setAttribute('aria-selected',String(t===b)));});
    $('#buyer-login-mode').onchange=e=>{const sms=e.target.value==='sms',f=$('#buyer-login');f.elements.password.closest('label').hidden=sms;f.elements.password.required=!sms;$('#buyer-code-label').hidden=!sms;f.elements.code.required=sms;$('#buyer-login-sms').hidden=!sms;};
    buyerForm('buyer-login',async b=>{buyerSaveSession(await request(API.login,'POST',{phone:b.phone,...($('#buyer-login-mode').value==='sms'?{code:b.code}:{password:b.password})}));buyerAfterLogin();});
    buyerForm('buyer-register',async(b,f)=>{await request(API.register,'POST',b);f.reset();document.querySelector('[data-auth-tab="login"]').click();$('#buyer-login [name="phone"]').value=b.phone;notice('注册成功，请登录。','success');});
    buyerSms($('#buyer-login-sms'),'buyer-login','LOGIN');buyerSms($('#buyer-register-sms'),'buyer-register','REGISTER');buyerSms($('#buyer-reset-sms'),'buyer-reset-verify','RESET_PASSWORD');
    let ticket='';
    buyerForm('buyer-reset-verify',async b=>{ticket=(await request('/api/auth/reset/verify','POST',b)).ticket;$('#buyer-reset-verify').hidden=true;$('#buyer-reset-password').hidden=false;});
    buyerForm('buyer-reset-password',async b=>{await request('/api/auth/reset/password','POST',{ticket,password:b.password});renderBuyerAccount();notice('密码已更新，请重新登录。','success');});return;
  }
  $('#content').innerHTML=`<section class="member-banner"><span class="member-avatar">栗</span><div><small>我的栗小铺</small><h2 id="buyer-nickname">欢迎回来</h2><p>每一份喜欢，都值得。</p></div></section><nav class="member-links" aria-label="个人中心"><a href="orders.html">我的订单 <span>订单与售后 →</span></a><a href="addresses.html">收货地址 <span>管理配送地址 →</span></a><a href="vouchers.html?tab=wallet">我的优惠券 <span>可用券与待付款 →</span></a><a href="chat.html">在线客服 <span>联系我们 →</span></a></nav><section class="panel"><h2>个人资料</h2><form id="buyer-profile" class="form-grid"><label>昵称<input name="nickname" maxlength="64"></label><label>手机号<input name="phone" maxlength="11" type="tel"></label><label>头像<input name="avatarFile" type="file" accept="image/*"></label><button>保存资料</button></form></section><section class="panel"><h2>账号安全</h2><form id="buyer-password" class="form-grid">${buyerField('oldPassword','原密码','password')}${buyerField('newPassword','新密码','password','minlength="8" maxlength="72"')}<button>修改密码</button></form><button id="buyer-logout" class="secondary">退出登录</button></section><section class="panel"><h2>我的收藏</h2><div id="buyer-collections">正在加载…</div></section>`;
  let profile={};
  run(null,async()=>{profile=await request(API.profile);$('#buyer-nickname').textContent=profile.nickname||'欢迎回来';for(const k of ['nickname','phone'])$('#buyer-profile').elements[k].value=profile[k]||'';});
  buyerForm('buyer-profile',async(_,f)=>{let avatar=profile.avatar;if(f.elements.avatarFile.files.length){const data=new FormData();data.append('file',f.elements.avatarFile.files[0]);avatar=await uploadImage('/api/upload/image',data);}const b={nickname:f.elements.nickname.value,phone:f.elements.phone.value,...(avatar?{avatar}:{})};await request(API.profileUpdate,'PUT',b);profile={...profile,...b};$('#buyer-nickname').textContent=b.nickname||'欢迎回来';notice('资料已保存。','success');});
  const clear=()=>{token='';for(const k of ['token','refreshToken','userId','sessionId'])sessionStorage.removeItem('lxp.'+k);render();};
  buyerForm('buyer-password',async b=>{await request('/api/auth/change-password','POST',b);clear();notice('密码已修改，请重新登录。','success');});
  $('#buyer-logout').onclick=e=>run(e.currentTarget,async()=>{await request(API.logout,'POST');clear();notice('已退出登录。');});
  run(null,async()=>{const rows=list(await request('/api/user/collection/list?pageNo=1&pageSize=12'));$('#buyer-collections').innerHTML=rows.map(p=>`<a class="collection-link" href="products.html?id=${encodeURIComponent(p.productId)}">${escapeHtml(p.productName)} <span>${money(p.productPrice)} →</span></a>`).join('')||'<p class="empty">还没有收藏，去挑选喜欢的商品吧。</p>';});
}
function renderBuyerAddresses() {
  if(!buyerGate())return;
  $('#content').innerHTML='<div class="split"><section class="panel"><h2>我的收货地址</h2><div id="buyer-addresses" aria-live="polite">正在加载地址…</div></section><section class="panel"><h2 id="address-form-title">新增地址</h2><form id="buyer-address-form" class="form-grid">'+[['receiverName','收件人'],['receiverPhone','联系电话'],['province','省份'],['city','城市'],['district','区县'],['detailAddress','详细地址']].map(([k,l])=>buyerField(k,l,k==='receiverPhone'?'tel':'text',k==='receiverPhone'?'pattern="1[3-9][0-9]{9}" maxlength="11"':'maxlength="100"')).join('')+'<label class="check"><input type="checkbox" name="isDefault">设为默认地址</label><button>保存地址</button><button type="button" id="buyer-address-new" class="secondary">取消编辑</button></form></section></div>';
  let rows=[],editId='';const form=$('#buyer-address-form');
  const reset=()=>{editId='';form.reset();$('#address-form-title').textContent='新增地址';};
  const load=async()=>{rows=list(await request('/api/address/list'));$('#buyer-addresses').innerHTML=rows.map(a=>`<article class="address-card"><b>${escapeHtml(a.receiverName||a.receiver)}</b> ${escapeHtml(a.receiverPhone||a.phone)} ${a.isDefault?'<span class="product-tag">默认</span>':''}<p>${escapeHtml([a.province,a.city,a.district,a.detailAddress].join(' '))}</p><button class="secondary" data-edit-address="${escapeHtml(a.id)}">编辑</button> <button class="secondary" data-remove-address="${escapeHtml(a.id)}">删除</button></article>`).join('')||'<div class="empty">还没有收货地址，添加一个吧。</div>';};
  $('#buyer-address-new').onclick=reset;
  $('#buyer-addresses').onclick=e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.editAddress){const a=rows.find(a=>String(a.id)===b.dataset.editAddress);editId=String(a.id);for(const k of ['receiverName','receiverPhone','province','city','district','detailAddress'])form.elements[k].value=a[k]||(k==='receiverName'?a.receiver:k==='receiverPhone'?a.phone:'')||'';form.elements.isDefault.checked=!!a.isDefault;$('#address-form-title').textContent='编辑地址';form.scrollIntoView({behavior:'smooth',block:'center'});}else if(confirm('确认删除这个地址？'))run(b,async()=>{await request('/api/address/delete?id='+encodeURIComponent(b.dataset.removeAddress),'DELETE');await load();});};
  buyerForm('buyer-address-form',async b=>{b.isDefault=form.elements.isDefault.checked;if(editId)b.id=editId;await request('/api/address/'+(editId?'update':'add'),editId?'PUT':'POST',b);reset();await load();notice('地址已保存。','success');});run(null,load);
}
function renderBuyerCart() {
  if(!buyerGate())return;
  const direct=new URLSearchParams(location.search).get('direct')==='1';
  $('#content').innerHTML=`<div class="checkout-layout"><section class="panel"><div class="between"><h2>${direct?'确认购买':'我的购物车'}</h2><a href="products.html">继续挑选 →</a></div><label class="check"><input type="checkbox" id="buyer-select-all">全选</label><div id="buyer-cart-list" aria-live="polite">正在加载商品…</div></section><section class="panel checkout-summary"><h2>确认订单</h2><form id="buyer-checkout"><label>收货地址<select name="addressId" required><option value="">正在加载地址…</option></select></label><a href="addresses.html" class="inline-link">管理收货地址 →</a><label>可用优惠券<select name="voucherOrderId"><option value="">不使用优惠券</option></select></label><label>订单备注<input name="remark" maxlength="255" placeholder="选填"></label><div id="buyer-totals" aria-live="polite"></div><p class="hint">优惠与最终应付以订单确认结果为准。</p><button id="buyer-submit" disabled>提交订单</button></form><div id="buyer-order-created" hidden></div></section></div>`;
  let rows=[],selected=new Set(),vouchers=[],freight=null,saving=0,submitting=false,created=false,revision=0;
  const form=$('#buyer-checkout'),submit=$('#buyer-submit');
  const picked=()=>rows.filter(i=>selected.has(cartEntryKey(i)));
  const totals=()=>{
    const chosen=picked(),voucher=vouchers.find(v=>String(v.id)===form.elements.voucherOrderId.value);
    const amount=StorefrontMath.summary(chosen,freight??0,voucher?.actualValue||0);
    $('#buyer-totals').innerHTML=`<dl class="totals"><dt>已选商品</dt><dd>${chosen.reduce((n,i)=>n+Number(i.quantity),0)} 件</dd><dt>商品金额</dt><dd>${money(amount.goods/100)}</dd><dt>优惠抵扣</dt><dd>−${money(amount.discount/100)}</dd><dt>运费</dt><dd>${freight===null?'待确认':money(amount.freight/100)}</dd><dt>预计应付</dt><dd class="price">${money(amount.total/100)}</dd></dl>`;
    $('#buyer-select-all').checked=rows.length>0&&selected.size===rows.length;
    $('#buyer-select-all').indeterminate=selected.size>0&&selected.size<rows.length;
    submit.disabled=created||submitting||saving>0||!chosen.length||!form.elements.addressId.value||freight===null;
    submit.textContent=saving?'正在保存数量…':submitting?'正在创建订单…':'提交订单';
  };
  const estimate=async()=>{
    const version=++revision;freight=null;totals();if(!picked().length||!form.elements.addressId.value)return;
    try{const data=await request('/api/order/freight?'+new URLSearchParams({productIds:[...new Set(picked().map(i=>i.productId))].join(','),addressId:form.elements.addressId.value}));if(version===revision){freight=Number(data.freight);totals();}}catch(e){if(version===revision)notice(e.message,'error');}
  };
  const paint=()=>{
    $('#buyer-cart-list').innerHTML=rows.map(i=>{const key=cartEntryKey(i);return `<article class="basket-item"><input type="checkbox" data-pick="${escapeHtml(key)}" aria-label="选择${escapeHtml(i.productName)}" ${selected.has(key)?'checked':''}><a href="products.html?id=${encodeURIComponent(i.productId)}" class="basket-art">${productArt({name:i.productName,image:i.productImage})}</a><div class="basket-info"><a href="products.html?id=${encodeURIComponent(i.productId)}">${escapeHtml(i.productName)}</a><p>${escapeHtml(i.specText)}</p><strong>${money(i.price)}</strong><label class="quantity-label">数量<input type="number" min="1" max="9999" step="1" value="${i.quantity}" data-quantity="${escapeHtml(key)}" aria-label="${escapeHtml(i.productName)}数量"></label></div><button class="secondary" data-remove="${escapeHtml(key)}" ${direct?'hidden':''}>移除</button></article>`;}).join('')||'<div class="empty">购物车还没有商品。<a href="products.html">去挑选好物 →</a></div>';totals();
  };
  let writeQueue=Promise.resolve();
  $('#buyer-cart-list').onchange=e=>{
    if(created)return;
    if(e.target.dataset.pick){e.target.checked?selected.add(e.target.dataset.pick):selected.delete(e.target.dataset.pick);estimate();return;}
    const input=e.target,key=input.dataset.quantity;if(!key)return;
    const item=rows.find(i=>cartEntryKey(i)===key),quantity=Number(input.value);
    if(!input.reportValidity()||!Number.isInteger(quantity)||quantity<1){input.value=item.quantity;return;}
    saving++;totals();
    writeQueue=writeQueue.then(async()=>{try{if(!direct)await request('/api/cart/quantity','PUT',{productId:item.productId,specId:item.specId,quantity});item.quantity=quantity;notice('数量已更新。','success');}catch(e){input.value=item.quantity;notice(e.message,'error');}finally{saving--;totals();}});
  };
  $('#buyer-cart-list').onclick=e=>{const button=e.target.closest('[data-remove]');if(!button||created||saving)return;run(button,async()=>{const item=rows.find(i=>cartEntryKey(i)===button.dataset.remove);await request('/api/cart/products?'+new URLSearchParams({productIds:item.productId,specIds:item.specId}),'DELETE');rows=rows.filter(i=>i!==item);selected.delete(button.dataset.remove);paint();await estimate();});};
  $('#buyer-select-all').onchange=e=>{selected=e.target.checked?new Set(rows.map(cartEntryKey)):new Set();paint();estimate();};
  form.elements.addressId.onchange=()=>estimate();form.elements.voucherOrderId.onchange=totals;
  form.onsubmit=async e=>{
    e.preventDefault();if(created||submitting||saving||!picked().length||freight===null)return;
    submitting=true;totals();
    try{
      const chosen=picked();const body={source:direct?'DIRECT':'CART',addressId:form.elements.addressId.value,freight:String(freight),remark:form.elements.remark.value,orderItems:chosen.map(i=>({productId:i.productId,specId:i.specId,quantity:Number(i.quantity)}))};
      if(form.elements.voucherOrderId.value)body.voucherOrderId=form.elements.voucherOrderId.value;
      const order=await request('/api/order/create','POST',body);created=true;
      if(direct)sessionStorage.removeItem('lxp.directCheckout');
      form.hidden=true;$('#buyer-select-all').disabled=true;$('#buyer-cart-list').querySelectorAll('input,button').forEach(el=>el.disabled=true);
      const host=$('#buyer-order-created');host.hidden=false;host.innerHTML=`<h3>订单已创建</h3><p>优惠 ${money(order.voucherDiscountAmount||0)}，应付 <strong class="price">${money(order.payAmount)}</strong></p>${order.cartCleared===false?'<p>订单已创建，购物车暂未清理，请勿重复下单。</p>':''}<a class="button-link" href="orders.html?orderNo=${encodeURIComponent(order.orderNo)}">${order.paymentRequired===false?'查看订单':'去支付 / 查看订单'}</a>`;
      host.scrollIntoView({behavior:'smooth',block:'center'});
    }catch(error){notice(error.message+' 如提交超时，请先在“我的订单”确认是否已生成订单，再决定是否重试。','error');}
    finally{submitting=false;totals();}
  };
  run(null,async()=>{if(direct){try{const item=JSON.parse(sessionStorage.getItem('lxp.directCheckout')||'null');rows=item?[item]:[];}catch{rows=[];}}else rows=list(await request('/api/cart/list'));selected=new Set(rows.map(cartEntryKey));paint();await estimate();});
  run(null,async()=>{const addresses=list(await request('/api/address/list'));form.elements.addressId.innerHTML=addresses.length?addresses.map(a=>`<option value="${escapeHtml(a.id)}" ${a.isDefault?'selected':''}>${escapeHtml(a.receiverName||a.receiver)} · ${escapeHtml([a.province,a.city,a.district,a.detailAddress].join(' '))}</option>`).join(''):'<option value="">请先添加收货地址</option>';await estimate();});
  run(null,async()=>{let cursor='0';while(true){const batch=list(await request('/api/voucher-order/mine?'+new URLSearchParams({beforeId:cursor,limit:100})));vouchers.push(...batch.filter(v=>v.state==='AVAILABLE'));const next=batch[batch.length-1]?.id;if(batch.length<100||!next||String(next)===String(cursor))break;cursor=String(next);}form.elements.voucherOrderId.innerHTML='<option value="">不使用优惠券</option>'+vouchers.map(v=>`<option value="${escapeHtml(v.id)}">抵扣 ${money(Number(v.actualValue)/100)} · ${escapeHtml(v.validUntil||'')}</option>`).join('');totals();});
}

const buyerOrderStatuses={pendingPayment:'待付款',pendingConfirm:'待确认',pendingShipment:'待发货',pendingReceipt:'待收货',completed:'已完成',cancelled:'已取消',afterSale:'售后中',evaluated:'已评价',reviewed:'已追评',refunded:'已退款'};
const buyerPayStatuses={unpaid:'未支付',paid:'已支付',refundRequested:'退款待审核',refunding:'退款中',refundSuccess:'退款成功',refundRejected:'退款申请未通过'};
function buyerOrderItems(order) {return (order.orderItems||[]).map(i=>`<div class="order-goods"><div class="basket-art">${productArt({name:i.productName,image:i.productImage})}</div><div><b>${escapeHtml(i.productName)}</b><p>${escapeHtml(i.specText)} × ${escapeHtml(i.quantity)}</p><span>${money(i.price)}</span></div></div>`).join('');}
function renderBuyerOrders() {
  if(!buyerGate())return;
  const no=new URLSearchParams(location.search).get('orderNo');
  if(no){renderBuyerOrderDetail(no);return;}
  $('#content').innerHTML='<section class="panel"><div class="between"><h2>我的订单</h2><button id="buyer-orders-refresh" class="secondary">刷新</button></div><div class="customer-tabs" id="buyer-order-tabs">'+[['allPage','全部'],['pendingPayPage','待付款'],['packagingPage','打包中'],['pendingReceiptPage','待收货'],['pendingEvalPage','待评价']].map(([key,label])=>`<button data-tab="${key}" aria-pressed="${key==='allPage'}">${label}</button>`).join('')+'</div><div id="buyer-orders" aria-live="polite">正在加载订单…</div><button id="buyer-orders-more" class="secondary" hidden>加载更多</button></section>';
  let tab='allPage',cursor=null,more=true,loading=false;
  const load=async reset=>{if(loading||(!reset&&!more))return;loading=true;$('#buyer-order-tabs').querySelectorAll('button').forEach(b=>b.disabled=true);$('#buyer-orders-more').disabled=true;
    try{const data=await request('/api/order/list?'+new URLSearchParams({pageName:tab,pageSize:10,...(!reset&&cursor?{beginId:cursor}:{})}));const markup=(data.list||[]).map(o=>`<article class="order-card"><div class="between"><span class="hint">${escapeHtml(o.createTime)}</span><strong>${escapeHtml(buyerOrderStatuses[o.status]||o.status)}</strong></div>${buyerOrderItems(o)}<div class="between"><span>合计 <strong class="price">${money(o.totalAmount)}</strong></span><a class="button-link" href="orders.html?orderNo=${encodeURIComponent(o.orderNo)}">${o.status==='pendingPayment'?'去支付':'查看订单'}</a></div></article>`).join('');if(reset)$('#buyer-orders').innerHTML=markup||'<div class="empty">暂无这类订单。<a href="products.html">去逛逛 →</a></div>';else $('#buyer-orders').insertAdjacentHTML('beforeend',markup);cursor=data.endId;more=data.hasMore===true;$('#buyer-orders-more').hidden=!more;}finally{loading=false;$('#buyer-order-tabs').querySelectorAll('button').forEach(b=>b.disabled=false);$('#buyer-orders-more').disabled=false;}};
  $('#buyer-order-tabs').onclick=e=>{const b=e.target.closest('[data-tab]');if(!b||loading)return;tab=b.dataset.tab;$('#buyer-order-tabs').querySelectorAll('button').forEach(t=>t.setAttribute('aria-pressed',String(t===b)));run(null,()=>load(true));};
  $('#buyer-orders-refresh').onclick=e=>run(e.currentTarget,()=>load(true));$('#buyer-orders-more').onclick=e=>run(e.currentTarget,()=>load(false));run(null,()=>load(true));
}
function renderBuyerOrderDetail(no) {
  $('#content').innerHTML='<a class="inline-link" href="orders.html">← 返回我的订单</a><section class="panel" id="buyer-order-detail">正在加载订单…</section>';
  const host=$('#buyer-order-detail');let order;
  const load=async()=>{order=await request('/api/order/detail?orderNo='+encodeURIComponent(no));host.innerHTML=`<div class="between"><h2>${escapeHtml(buyerOrderStatuses[order.status]||order.status)}</h2><span>${escapeHtml(buyerPayStatuses[order.payStatus]||order.payStatus||'')}</span></div>${buyerOrderItems(order)}<dl class="totals"><dt>订单号</dt><dd>${escapeHtml(order.orderNo)}</dd><dt>创建时间</dt><dd>${escapeHtml(order.createTime)}</dd><dt>运费</dt><dd>${money(order.freight)}</dd><dt>优惠</dt><dd>${money(order.voucherDiscountAmount||0)}</dd><dt>应付金额</dt><dd class="price">${money(order.totalAmount)}</dd></dl>${order.address?`<div class="address-card"><b>${escapeHtml(order.address.receiver||order.address.receiverName)}</b> ${escapeHtml(order.address.phone||order.address.receiverPhone)}<p>${escapeHtml([order.address.province,order.address.city,order.address.district,order.address.detailAddress].join(' '))}</p></div>`:''}<div class="row">${order.status==='pendingPayment'?'<button data-order-action="pay">微信支付</button><button data-order-action="cancel" class="secondary">取消订单</button>':''}${order.status==='pendingReceipt'?'<button data-order-action="receipt">确认收货</button>':''}<button data-order-action="refresh" class="secondary">刷新状态</button><button data-order-action="logistics" class="secondary">查看物流</button></div><div id="buyer-logistics" aria-live="polite"></div>${order.payStatus==='paid'?'<form id="buyer-refund" class="form-grid"><h3>申请整单退款</h3><label>退款原因<input name="reason" maxlength="80" required></label><button class="secondary">提交退款申请</button></form>':''}${order.refundReason?`<p>退款原因：${escapeHtml(order.refundReason)}</p>`:''}${order.refundFailReason?`<p>${escapeHtml(order.refundFailReason)}</p>`:''}`;
    if($('#buyer-refund'))buyerForm('buyer-refund',async b=>{if(!confirm('确认申请整单退款？'))return;await request('/api/order/'+encodeURIComponent(no)+'/refund','POST',{reason:b.reason});await load();notice('退款申请已提交。','success');});
    if(order.status==='completed') {
      host.insertAdjacentHTML('beforeend',`<form id="buyer-review" class="form-grid"><h3>评价购买的商品</h3><label>选择商品<select name="item">${(order.orderItems||[]).map((i,index)=>`<option value="${index}">${escapeHtml(i.productName)} · ${escapeHtml(i.specText)}</option>`).join('')}</select></label><label>评分<select name="rating">${[5,4,3,2,1].map(n=>`<option value="${n}">${n} 星</option>`).join('')}</select></label><label class="span-all">评价内容<textarea name="content" maxlength="500" required></textarea></label><label class="check"><input name="anonymous" type="checkbox">匿名评价</label><button>提交评价</button></form>`);
      buyerForm('buyer-review',async(body,form)=>{
        const item=order.orderItems[Number(body.item)],profile=await request(API.profile);
        await request('/api/products/user/product/comment/firstComment/save','POST',{
          productId:item.productId,productSpecId:item.specId,productSpecText:item.specText,orderNo:no,
          userNickname:profile.nickname||'小铺买家',userAvatar:profile.avatar||location.origin+'/assets/chestnuts.svg',
          content:body.content,rating:Number(body.rating),isAnonymous:form.elements.anonymous.checked?1:0
        });
        form.reset();notice('评价已提交，感谢分享。','success');await load();
      });
    }
  };
  host.onclick=e=>{const b=e.target.closest('[data-order-action]');if(!b)return;run(b,async()=>{const action=b.dataset.orderAction;if(action==='pay'){await invokeWechatPayment(await request('/api/pay/wxpay','POST',{orderNo:no}));await load();notice('支付已提交，订单状态以确认结果为准。','success');}else if(action==='refresh')await load();else if(action==='logistics'){const data=await request('/api/order/logistics?orderNo='+encodeURIComponent(no));const steps=data?.orderTrackings||data?.trackingList||data?.logisticsList||data?.traces||[];$('#buyer-logistics').innerHTML=steps.length?steps.map(s=>`<p>${escapeHtml(s.time||s.createTime||'')} ${escapeHtml(s.context||s.content||s.description||'')}</p>`).join(''):'<p>暂无物流轨迹，请稍后查看。</p>';}else{if(!confirm(action==='cancel'?'确认取消此订单？':'确认已收到全部商品？'))return;await request('/api/order/'+(action==='cancel'?'cancel':'confirmReceipt')+'?'+new URLSearchParams({orderNo:no,...(action==='cancel'?{cancelReason:'用户主动取消'}:{})}),'PUT');await load();notice('操作成功。','success');}});};run(null,load);
}
function renderBuyerVouchers() {
  let tab=new URLSearchParams(location.search).get('tab')==='wallet'?'wallet':'normal',cursor='0',more=true,loading=false,revision=0;
  $('#content').innerHTML='<section class="panel"><div class="customer-tabs" id="buyer-voucher-tabs"><button data-tab="normal">日常好券</button><button data-tab="seckill">限时抢购</button><button data-tab="wallet">我的券包</button></div><p>购券付款后可抵扣商品金额，一单一券，不找零。</p><div id="buyer-voucher-list" aria-live="polite"></div><button id="buyer-vouchers-more" class="secondary" hidden>加载更多</button><div id="buyer-voucher-result" aria-live="polite"></div></section>';
  const names={UNPAID:'待付款',PAY_EXPIRED:'支付已过期',AVAILABLE:'可使用',LOCKED:'订单占用中',USED:'已使用',EXPIRED:'已过期',CANCELLED:'已取消',UNAVAILABLE:'不可用'};
  const load=async(reset=false)=>{
    if(loading||(!reset&&!more))return;
    $('#buyer-voucher-tabs').querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===tab)));
    if(tab==='wallet'&&!token){$('#buyer-voucher-list').innerHTML='<div class="empty">登录后查看券包。<a href="account.html?returnTo=vouchers.html%3Ftab%3Dwallet">去登录 →</a></div>';$('#buyer-vouchers-more').hidden=true;return;}
    const active=++revision;loading=true;if(reset){cursor='0';more=true;$('#buyer-voucher-list').innerHTML='<p>正在加载优惠券…</p>';}
    try{const wallet=tab==='wallet',batch=list(await request(wallet?'/api/voucher-order/mine?'+new URLSearchParams({beforeId:cursor,limit:20}):'/api/vouchers?'+new URLSearchParams({type:tab==='normal'?0:1,afterId:cursor,limit:20})));if(active!==revision)return;
      const markup=batch.map(v=>wallet?`<article class="voucher-card"><strong>抵扣 ${money(Number(v.actualValue)/100)}</strong><span>${escapeHtml(names[v.state]||v.state)}</span><p>${v.validUntil?'有效期至 '+escapeHtml(v.validUntil):'付款后生效'}</p>${v.state==='UNPAID'?`<button data-voucher-pay="${escapeHtml(v.id)}">继续支付</button>`:v.state==='AVAILABLE'?'<a class="button-link" href="cart.html">去使用</a>':''}</article>`:`<article class="voucher-card"><h3>${escapeHtml(v.title)}</h3><strong>${money(Number(v.payValue)/100)} 购 ${money(Number(v.actualValue)/100)} 券</strong><p>付款后 ${escapeHtml(v.validityDays)} 天内使用</p>${tab==='seckill'?`<p>${escapeHtml(v.beginTime)} 至 ${escapeHtml(v.endTime)} · 剩余 ${escapeHtml(v.stock)} 张</p>`:''}<button data-voucher-buy="${escapeHtml(v.id)}" data-kind="${tab}">${tab==='seckill'?'立即抢购':'购买优惠券'}</button></article>`).join('');
      if(reset)$('#buyer-voucher-list').innerHTML=markup||'<div class="empty">暂时没有优惠券。</div>';else $('#buyer-voucher-list').insertAdjacentHTML('beforeend',markup);
      more=batch.length===20;cursor=batch[batch.length-1]?.id||cursor;$('#buyer-vouchers-more').hidden=!more;
    }finally{loading=false;}
  };
  const paymentCard=id=>{const host=$('#buyer-voucher-result');host.innerHTML=`<div class="purchase-result"><h3>购券订单已创建</h3><p>付款后优惠券才会生效。</p><button data-voucher-pay="${escapeHtml(id)}">去支付</button> <a class="inline-link" href="vouchers.html?tab=wallet">查看我的券包 →</a></div>`;host.scrollIntoView({behavior:'smooth',block:'center'});};
  const result=async id=>{const ticket=await request('/api/voucher-order/result/'+positiveId(id));if(ticket.state==='SUCCESS'){paymentCard(id);return;}if(ticket.state==='FAILED'){notice(ticket.reason||'本次抢购未成功，请稍后再试。','error');return;}$('#buyer-voucher-result').innerHTML=`<p>抢购正在处理中，请稍后查看。</p><button data-voucher-result="${escapeHtml(id)}" class="secondary">查看处理结果</button>`;};
  $('#buyer-voucher-tabs').onclick=e=>{const b=e.target.closest('[data-tab]');if(!b||loading)return;tab=b.dataset.tab;$('#buyer-voucher-result').innerHTML='';run(b,()=>load(true));};
  $('#buyer-vouchers-more').onclick=e=>run(e.currentTarget,()=>load());
  $('#content').onclick=e=>{const b=e.target.closest('[data-voucher-buy],[data-voucher-pay],[data-voucher-result]');if(!b)return;if(!token){location.assign('account.html?returnTo='+encodeURIComponent('vouchers.html?tab='+tab));return;}run(b,async()=>{if(b.dataset.voucherPay){await invokeWechatPayment(await request('/api/voucher-order/'+positiveId(b.dataset.voucherPay)+'/pay','POST'));notice('支付已提交，请查看券包确认结果。','success');tab='wallet';await load(true);}else if(b.dataset.voucherResult)await result(b.dataset.voucherResult);else if(b.dataset.kind==='normal'){const v=await request('/api/voucher-order/buy/'+positiveId(b.dataset.voucherBuy),'POST');paymentCard(v.id);}else{const t=await request('/api/voucher-order/seckill/'+positiveId(b.dataset.voucherBuy),'POST');await result(t.orderId);}});};run(null,()=>load(true));
}
window.customerPages={account:renderBuyerAccount,addresses:renderBuyerAddresses,cart:renderBuyerCart,orders:renderBuyerOrders,vouchers:renderBuyerVouchers};
