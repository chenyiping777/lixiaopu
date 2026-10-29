'use strict';
const $ = selector => document.querySelector(selector);
const h = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const money = value => value == null ? '—' : '¥' + Number(value).toFixed(2);
const safeUrl = value => { try { const url = new URL(value, location.origin); return /^https?:$/.test(url.protocol) ? url.href : ''; } catch { return ''; } };
const id = value => { if (!/^[1-9]\d*$/.test(String(value))) throw Error('请输入有效编号'); return encodeURIComponent(value); };
const q = (path, params) => path + '?' + new URLSearchParams(Object.entries(params).filter(([,v])=>v!=='' && v!=null));
let token = sessionStorage.getItem('lxp.admin.token') || '';
let section = 'home';
const sections = {home:['◈','经营概览','在这里，认真打理每一份好物。'],products:['▦','商品管理','发布、编辑商品，管理规格与库存。'],categories:['☷','分类管理','用清晰的分类，让顾客更快找到喜欢的好物。'],orders:['▤','订单与售后','查看订单、安排发货，及时处理退款申请。'],vouchers:['◇','优惠券管理','发布普通代金券和限时秒杀活动。'],content:['▧','内容与客服','管理首页轮播图，与顾客保持联系。'],system:['⚙','账号与维护','管理员账号、安全设置与搜索索引维护。']};
function notice(text, error=false) { $('#status').textContent=text; $('#status').className=error ? 'error' : ''; }
async function run(button, action) { if(button)button.disabled=true; try { await action(); } catch(error) { notice(error.message,true); } finally { if(button?.isConnected)button.disabled=false; } }
async function send(path, method='GET', body) {
  const file = body instanceof FormData;
  const response = await fetch(path,{method,headers:{Accept:'application/json',...(token?{Authorization:'Bearer '+token}:{}),...(body && !file?{'Content-Type':'application/json'}:{})},...(body?{body:file?body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(25000)});
  const data = await response.json();
  if (!response.ok || !data.success || data.code!==200) throw Error(data.message || `请求失败 (${response.status})`);
  return data.data;
}
function saveTokens(data) { if (!data?.accessToken) throw Error('登录响应缺少 accessToken'); token=data.accessToken;sessionStorage.setItem('lxp.admin.token',token);sessionStorage.setItem('lxp.admin.refreshToken',data.refreshToken || ''); }
function input(name,label,type='text',value='',required=true) { return `<label>${label}<input name="${name}" type="${type}" value="${h(value)}" ${required?'required':''} ${type==='password'?'minlength="8" maxlength="72"':''} ${name==='code'?'minlength="5" maxlength="5" pattern="(?=.*[A-Z])(?=.*[0-9])[A-Z0-9]{5}" title="请输入 5 位大写字母与数字混合验证码，至少包含一个字母和一个数字" autocomplete="one-time-code" autocapitalize="characters"':''}></label>`; }
function select(name,label,options) { return `<label>${label}<select name="${name}">${options.map(([v,text])=>`<option value="${h(v)}">${h(text)}</option>`).join('')}</select></label>`; }
function form(key,title,fields,button='保存') { return `<section class="panel"><h2>${title}</h2><form id="${key}" class="form-grid">${fields}<div class="full"><button>${button}</button></div></form></section>`; }
function bind(key, action) { const form=document.getElementById(key); form.onsubmit=e=>{e.preventDefault();run(e.submitter,()=>action(Object.fromEntries(new FormData(form)),form));}; }
function result(key,data) { const node=document.getElementById(key);node.textContent=JSON.stringify(data,null,2); }
function render(key) {
  section=key; notice(''); $('#current-section').textContent=$('#title').textContent=sections[key][1];$('#description').textContent=sections[key][2];
  $('#nav').innerHTML=Object.entries(sections).map(([k,v])=>`<button data-section="${k}" class="${k===key?'active':''}">${v[0]}　${v[1]}</button>`).join('');
  ({home:renderHome,products:renderProducts,categories:renderCategories,orders:renderOrders,vouchers:renderVouchers,content:renderContent,system:renderSystem})[key]();
}
$('#nav').onclick=e=>{ const button=e.target.closest('[data-section]');if(button)render(button.dataset.section); };
async function identity() { const user=await send('/api/admin/me'); $('#identity').textContent=user.username+' · '+(user.roles || []).join(' / ');return user; }
function renderHome() {
  $('#content').innerHTML=`<section class="welcome"><h2>欢迎来到商家中心。</h2><p>好商品、好服务，从每一次用心经营开始。</p></section><div class="quick-grid">${[['products','商品橱窗','发布商品 · 管理库存'],['orders','订单与售后','安排发货 · 处理退款'],['vouchers','优惠券','普通代金券 · 秒杀活动']].map(([key,title,text])=>`<article class="quick"><b>${title}</b><span>${text}</span><a href="#" data-open="${key}">前往管理 →</a></article>`).join('')}</div><div class="grid">${form('login','管理员登录',input('phone','手机号','tel')+input('password','密码','password'),'登录')}<section class="panel"><h2>当前身份</h2><p class="muted">管理端使用独立会话，权限以服务端校验为准。</p><div class="row"><button id="me" class="secondary">刷新身份</button><button id="refresh" class="secondary">续期会话</button><button id="logout" class="secondary">退出登录</button></div><pre id="me-result">请先登录。</pre></section></div>`;
  $('#content').onclick=e=>{ const link=e.target.closest('[data-open]');if(link){e.preventDefault();render(link.dataset.open);} };
  bind('login',async(body,form)=>{ saveTokens(await send('/api/admin/auth/login','POST',body));form.reset();result('me-result',await identity());notice('登录成功。'); });
  $('#me').onclick=e=>run(e.currentTarget,async()=>result('me-result',await identity()));
  $('#refresh').onclick=e=>run(e.currentTarget,async()=>{saveTokens(await send('/api/admin/auth/refresh','POST',{refreshToken:sessionStorage.getItem('lxp.admin.refreshToken')}));notice('会话已续期。');});
  $('#logout').onclick=e=>run(e.currentTarget,async()=>{await send('/api/admin/auth/logout','POST');token='';sessionStorage.removeItem('lxp.admin.token');sessionStorage.removeItem('lxp.admin.refreshToken');$('#identity').textContent='尚未登录';result('me-result','已退出');});
  if(token)run(null,async()=>result('me-result',await identity()));
}
function renderProducts() {
  $('#content').onclick=null;
  $('#content').innerHTML=`<section class="panel"><h2>商品列表</h2><form id="product-search" class="row">${input('keyword','商品关键词','text','',false)}${input('categoryId','分类编号','text','',false)}${select('firstCategoryId','分类层级',[['false','二级分类'],['true','一级分类']])}${input('minPrice','最低价','number','',false)}${input('maxPrice','最高价','number','',false)}<button>查询</button></form><div id="product-list"></div><div class="pager"><button id="prev" class="secondary" disabled>上一页</button><span id="page-label">第 1 页</span><button id="next" class="secondary" disabled>下一页</button></div></section>`
    +form('product-edit','发布 / 编辑商品',input('id','商品编号（留空新增）','text','',false)+input('name','商品名称')+input('categoryId','二级分类编号')+input('sellPoint','商品卖点','text','',false)+input('price','基础价格（元）','number')+input('enterprisePrice','企业价格（元，可选）','number','',false)+input('image','封面图地址','text','',false)+select('status','上架状态',[['active','上架'],['inactive','下架']])+`<label class="full">商品描述<textarea name="description"></textarea></label><div class="full"><h3>商品规格</h3><p class="muted">编辑已有规格时保留规格编号。库存变更请使用下方「库存调整」，由服务端保护库存一致性。</p><div id="specs"></div><button type="button" id="add-spec" class="secondary">＋ 添加规格</button></div><label class="full">展示图 URL（每行一张）<textarea name="imageUrls"></textarea></label>`,'保存商品')
    +`<section class="panel"><h2>商品图片上传</h2><form id="product-upload" class="row">${select('kind','图片用途',[['cover','封面（单张）'],['images','展示图（多张）']])}<label>选择图片<input name="files" type="file" accept="image/*" multiple required></label><button>上传并填入商品</button></form></section>`
    +form('stock-adjust','库存调整',input('id','商品编号')+input('specId','规格编号')+input('delta','增减数量（补货为正，扣减为负）','number'),'调整库存');
  for(const input of document.querySelectorAll('input[type=number]'))input.step='0.01'; $('#stock-adjust [name=delta]').step='1';
  let pageNo=1,total=0,rows=[];
  const addSpec=(spec={})=>{const node=document.createElement('div');node.className='spec-row';node.dataset.id=spec.id || '';node.innerHTML=input('specText','规格名称','text',spec.specText || '')+input('specPrice','单价（元）','number',spec.price ?? '')+input('specStock','初始库存','number',spec.stock ?? 0)+'<button type="button" class="secondary">移除</button>';node.querySelector('[name=specPrice]').step='0.01';node.querySelector('button').onclick=()=>node.remove();$('#specs').append(node);};addSpec();
  const fillProduct = p => {
    const editor=$('#product-edit');
    for(const key of ['id','name','categoryId','sellPoint','price','enterprisePrice','image','status','description']) editor.elements[key].value=p[key]??'';
    editor.elements.imageUrls.value=(p.imageUrls || []).join('\n');
    $('#specs').replaceChildren(); (p.specList || []).forEach(addSpec);
  };
  $('#product-edit').insertAdjacentHTML('beforeend','<div class="full row"><button type="button" id="product-read" class="secondary">读取编号对应的商品</button><button type="button" id="product-new" class="secondary">新建商品</button></div>');
  $('#product-read').onclick=e=>run(e.currentTarget,async()=>fillProduct(await send('/api/admin/products/'+id($('#product-edit').elements.id.value))));
  $('#product-new').onclick=()=>{$('#product-edit').reset();$('#product-edit').elements.id.value='';$('#specs').replaceChildren();addSpec();};
  $('#add-spec').onclick=()=>addSpec();
  const load=async()=>{const body=Object.fromEntries(new FormData($('#product-search')));Object.assign(body,{firstCategoryId:body.firstCategoryId==='true',pageNo,pageSize:10,sortType:'default'});for(const key of Object.keys(body))if(body[key]==='')delete body[key];const data=await send('/api/admin/products/search','POST',body);rows=data.list || data.records || [];total=Number(data.total || 0);$('#product-list').innerHTML=rows.length?`<div class="table-wrap"><table><thead><tr><th>商品</th><th>名称 / 编号</th><th>价格</th><th>库存</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows.map(p=>`<tr><td>${safeUrl(p.image)?`<img src="${h(safeUrl(p.image))}" alt="">`:'—'}</td><td>${h(p.name)}<br><small>${h(p.id)}</small></td><td>${money(p.price)}</td><td>${h(p.stock)}</td><td>${h(p.status)}</td><td><button class="secondary" data-edit="${h(p.id)}">编辑</button><button class="secondary" data-toggle="${h(p.id)}" data-status="${p.status==='active'?0:1}">${p.status==='active'?'下架':'上架'}</button><button class="secondary danger" data-delete="${h(p.id)}">删除</button></td></tr>`).join('')}</tbody></table></div>`:'<p class="muted">没有匹配的商品。</p>';$('#page-label').textContent=`第 ${pageNo} 页 · 共 ${total} 件`;$('#prev').disabled=pageNo<=1;$('#next').disabled=pageNo*10>=total;};
  const syncPager=()=>{$('#prev').disabled=pageNo<=1;$('#next').disabled=pageNo*10>=total;};
  bind('product-search',async()=>{pageNo=1;await load();});
  const movePage=async(delta)=>{pageNo+=delta;try{await load();}catch(error){pageNo-=delta;throw error;}};
  $('#prev').onclick=e=>run(e.currentTarget,()=>movePage(-1)).finally(syncPager);
  $('#next').onclick=e=>run(e.currentTarget,()=>movePage(1)).finally(syncPager);
  $('#product-list').onclick=e=>{const button=e.target.closest('button');if(!button)return;run(button,async()=>{const productId=button.dataset.edit || button.dataset.toggle || button.dataset.delete;if(button.dataset.edit){fillProduct(await send('/api/admin/products/'+id(productId)));$('#product-edit').scrollIntoView({behavior:'smooth'});}else if(button.dataset.toggle){await send(q('/api/admin/products/'+id(productId)+'/status',{status:button.dataset.status}),'PUT');await load();}else if(confirm('确认删除这个商品？有关联订单时建议下架。')){await send('/api/admin/products/'+id(productId),'DELETE');await load();}});};
  bind('product-edit',async(body)=>{
    const productId=body.id;delete body.id;
    body.imageUrls=body.imageUrls.split('\n').map(x=>x.trim()).filter(Boolean);
    body.specList=[...document.querySelectorAll('.spec-row')].map(node=>({...(node.dataset.id?{id:node.dataset.id}:{}),specText:node.querySelector('[name=specText]').value,price:node.querySelector('[name=specPrice]').value,stock:Number(node.querySelector('[name=specStock]').value)}));
    for(const field of ['specText','specPrice','specStock'])delete body[field];
    if(productId && !body.specList.length && !confirm('确认清空这个商品的全部规格？'))return;
    for(const key of ['enterprisePrice','image'])if(body[key]==='')delete body[key];
    const data=await send('/api/admin/products'+(productId?'/'+id(productId):''),productId?'PUT':'POST',body);
    $('#product-edit [name=id]').value=data.id;
    // 保存后重新读取数据库分配的规格编号，连续保存不会把旧规格误当作新规格。
    fillProduct(await send('/api/admin/products/'+id(data.id)));
    notice('商品保存成功，编号：'+data.id);
  });
  bind('product-upload',async(body,form)=>{const kind=body.kind;const files=form.elements.files.files;if(kind==='cover' && files.length!==1)throw Error('封面请选择一张图片');const data=new FormData();for(const file of files)data.append(kind==='cover'?'file':'files',file);const urls=await send('/api/admin/products/upload/'+kind,'POST',data);if(kind==='cover')$('#product-edit [name=image]').value=urls;else $('#product-edit [name=imageUrls]').value=urls.join('\n');notice('上传成功，保存商品后生效。');});
  bind('stock-adjust',async body=>{if(!Number.isInteger(Number(body.delta)) || Number(body.delta)===0)throw Error('库存调整量须为非零整数');await send('/api/admin/products/'+id(body.id)+'/stock','POST',{specId:body.specId,delta:Number(body.delta)});notice('库存已调整。');});
}
function renderCategories() {
  $('#content').innerHTML=`<section class="panel"><div class="row"><h2>商品分类树</h2><button id="load-tree" class="secondary">刷新分类</button></div><div id="tree"></div></section>`+form('category-edit','新增 / 编辑分类',input('id','分类编号（留空新增）','text','',false)+input('name','分类名称')+input('parentId','父分类编号（一级填 0）','text','0')+input('sort','排序值（越小越靠前）','number','0')+input('iconUrl','图标 URL','text','',false)+select('status','状态',[['1','启用'],['0','禁用']]));
  const flatten=(nodes,depth=0)=>nodes.flatMap(c=>[{...c,depth},...flatten(c.children || [],depth+1)]);let rows=[];
  const load=async()=>{rows=flatten(await send('/api/category/tree'));$('#tree').innerHTML='<div class="table-wrap"><table><thead><tr><th>分类</th><th>编号</th><th>父编号</th><th>状态</th><th>操作</th></tr></thead><tbody>'+rows.map(c=>`<tr><td>${'　'.repeat(c.depth)}${h(c.name)}</td><td>${h(c.id)}</td><td>${h(c.parentId)}</td><td>${c.status===1?'启用':'禁用'}</td><td><button class="secondary" data-edit="${h(c.id)}">编辑</button><button class="secondary" data-toggle="${h(c.id)}">${c.status===1?'禁用':'启用'}</button><button class="secondary danger" data-delete="${h(c.id)}">删除</button></td></tr>`).join('')+'</tbody></table></div>';};
  $('#load-tree').onclick=e=>run(e.currentTarget,load);
  $('#tree').onclick=e=>{const b=e.target.closest('button');if(!b)return;run(b,async()=>{const c=rows.find(c=>String(c.id)===(b.dataset.edit || b.dataset.toggle || b.dataset.delete));if(b.dataset.edit){for(const key of ['id','name','parentId','sort','iconUrl','status'])$('#category-edit').elements[key].value=c[key]??'';}else if(b.dataset.toggle){await send(q('/api/admin/category/'+id(c.id)+'/status',{status:c.status===1?0:1}),'PUT');await load();}else if(confirm('确认删除该分类？')){await send('/api/admin/category/'+id(c.id),'DELETE');await load();}});};
  bind('category-edit',async body=>{const categoryId=body.id;delete body.id;body.sort=Number(body.sort);body.status=Number(body.status);await send('/api/admin/category/'+(categoryId?id(categoryId):'add'),categoryId?'PUT':'POST',body);notice('分类已保存。');await load();});if(token)run(null,load);
}
function renderOrders() {
  $('#content').innerHTML=`<section class="panel"><h2>订单与售后</h2><form id="order-filter" class="row">${select('pageName','订单分类',[['allPage','全部订单'],['pendingPayPage','待付款'],['packagingPage','打包中'],['pendingReceiptPage','待收货'],['pendingEvalPage','待评价']])}<button>刷新订单</button></form><div id="order-list"></div><button id="order-more" class="secondary" disabled>加载更多</button></section><p class="muted">退款按原订单实付整单退还。审核拒绝须填写原因；重试仅适用于服务端允许的失败退款。</p>`;
  let cursor=null,hasMore=false;
  const labels={pendingPayment:'待付款',pendingConfirm:'待确认',pendingShipment:'待发货',pendingReceipt:'待收货',completed:'已完成',cancelled:'已取消',afterSale:'售后',evaluated:'已评价',reviewed:'已追评',refunded:'已退款'};
  const load=async reset=>{const params={pageName:$('#order-filter').elements.pageName.value,pageSize:10};if(!reset && cursor)params.beginId=cursor;const data=await send(q('/api/admin/orders/list',params));if(reset)$('#order-list').innerHTML='';$('#order-list').insertAdjacentHTML('beforeend',(data.list || []).map(o=>`<article class="order" data-order="${h(o.orderNo)}"><header><b>${h(o.orderNo)}</b><span class="tag">${h(labels[o.status] || o.status)} · ${h(o.payStatus)}</span></header>${(o.orderItems || []).map(i=>`<div class="order-item">${safeUrl(i.productImage)?`<img src="${h(safeUrl(i.productImage))}" alt="">`:''}<p>${h(i.productName)} · ${h(i.specText)} × ${h(i.quantity)}</p></div>`).join('')}<p>实付 ${money(o.totalAmount)} · ${h(o.createTime)} · 买家 ${h(o.userId)}</p>${o.refundReason?`<p>退款原因：${h(o.refundReason)}</p>`:''}<div class="row"><button class="secondary" data-status="4">标记发货</button><button class="secondary" data-status="5">完成订单</button><input aria-label="退款审核说明" placeholder="退款审核说明（拒绝必填）" maxlength="200"><button class="secondary" data-approved="true">同意退款</button><button class="secondary danger" data-approved="false">拒绝退款</button><button class="secondary" data-retry="true">重试退款</button></div></article>`).join('') || (reset?'<p class="muted">暂无订单。</p>':''));cursor=data.endId;hasMore=data.hasMore===true;$('#order-more').disabled=!hasMore;};
  bind('order-filter',()=>load(true));$('#order-more').onclick=e=>run(e.currentTarget,()=>load(false)).finally(()=>{$('#order-more').disabled=!hasMore;});
  $('#order-list').onclick=e=>{const b=e.target.closest('button');if(!b)return;run(b,async()=>{const card=b.closest('[data-order]'),path='/api/admin/orders/'+id(card.dataset.order);if(b.dataset.status){await send(q(path+'/status',{status:b.dataset.status}),'PUT');}else if(b.dataset.approved){const reason=card.querySelector('input').value.trim();if(b.dataset.approved==='false' && !reason)throw Error('请填写拒绝原因');if(!confirm('确认提交退款审核结果？'))return;await send(path+'/refund/review','PUT',{approved:b.dataset.approved==='true',reason});}else {await send(path+'/refund/retry','POST');}notice('操作成功。');await load(true);});};if(token)run(null,()=>load(true));
}
function renderVouchers() {
  const common=input('title','优惠券标题')+input('subTitle','副标题','text','',false)+input('payValue','购买价（分）','number')+input('actualValue','抵扣面额（分）','number')+input('validityDays','付款后有效天数','number','30')+'<label class="full">使用规则<textarea name="rules"></textarea></label>';
  $('#content').innerHTML=`<p class="muted">所有金额单位为分。普通券可重复购买；秒杀券限量，每位买家同活动限购一次。</p><div class="grid">${form('normal-voucher','普通代金券',common,'发布普通券')}${form('seckill-voucher','限时秒杀券',common+input('stock','活动库存','number')+input('beginTime','开始时间','datetime-local')+input('endTime','结束时间','datetime-local'),'发布秒杀券')}</div>`+form('voucher-status','普通券上下架',input('id','券模板编号')+select('status','状态',[['1','上架'],['0','下架']]))+form('voucher-cache','重建购券缓存',input('id','券模板编号'),'重建缓存');
  for(const key of ['normal-voucher','seckill-voucher'])bind(key,async body=>{if(BigInt(body.actualValue)<BigInt(body.payValue))throw Error('抵扣面额不能低于购买价');body.validityDays=Number(body.validityDays);if(body.stock)body.stock=Number(body.stock);const data=await send('/api/admin/vouchers'+(key==='normal-voucher'?'/normal':''),'POST',body);notice('发布成功，券模板编号：'+data);});
  bind('voucher-status',async body=>{await send(q('/api/admin/vouchers/'+id(body.id)+'/status',{status:body.status}),'PUT');notice('普通券状态已更新。');});
  bind('voucher-cache',async body=>{await send('/api/admin/vouchers/'+id(body.id)+'/cache/rebuild','POST');notice('缓存已重建。');});
}
function renderContent() {
  $('#content').innerHTML=`<section class="welcome"><h2>让店铺更有温度。</h2><p>首页展示与客服沟通，都是顾客认识店铺的一部分。</p></section><div class="quick-grid"><article class="quick"><b>首页轮播图</b><span>新增 · 编辑 · 排序 · 启停</span><a href="banners.html">管理轮播图 →</a></article><article class="quick"><b>在线客服</b><span>会话列表 · 消息历史 · 实时沟通</span><a href="chat.html">进入客服台 →</a></article><article class="quick"><b>退款工作台</b><span>查看退款 · 审核 · 重试</span><a href="refunds.html">进入退款台 →</a></article></div>`;
}
function renderSystem() {
  $('#content').innerHTML=`<div class="grid">${form('create-admin','创建管理员',input('username','用户名（字母、数字或下划线）')+input('phone','手机号','tel')+input('password','初始密码','password'),'创建管理员')}${form('disable-user','禁用用户',input('id','用户编号'),'禁用用户')}${form('change-password','修改登录密码',input('oldPassword','原密码','password')+input('newPassword','新密码','password'))}${form('reset-sms','找回密码：发送验证码',input('phone','手机号','tel'),'发送验证码')}${form('reset-verify','验证身份',input('phone','手机号','tel')+input('code','短信验证码（5 位大写字母与数字混合）'),'验证')}${form('reset-password','重置密码',input('ticket','验证凭证')+input('password','新密码','password'))}</div><section class="panel"><h2>搜索与缓存维护</h2><p class="muted">数据较多时导入需要等待。请确认需要重建后再执行。</p><div class="row"><button id="index-import" class="secondary">全量导入搜索索引</button><button id="bloom-rebuild" class="secondary">重建布隆过滤器</button><button id="keywords-load" class="secondary">读取搜索关键词</button></div><form id="keywords-edit"><label>搜索关键词列表（JSON，保留 ID，状态使用 active / inactive）<textarea name="keywords" required></textarea></label><button>保存关键词</button></form><pre id="system-result">等待操作。</pre></section>`;
  bind('create-admin',async body=>{await send('/api/admin/users/administrators','POST',body);$('#create-admin').reset();notice('管理员已创建。');});
  bind('disable-user',async body=>{if(confirm('确认禁用此用户？')){await send('/api/admin/users/'+id(body.id)+'/disable','POST');notice('用户已禁用。');}});
  bind('change-password',async body=>{await send('/api/admin/auth/change-password','POST',body);$('#change-password').reset();notice('密码已修改，请重新登录。');});
  bind('reset-sms',async body=>{body.purpose='RESET_PASSWORD';const data=await send('/api/admin/auth/sms','POST',body);notice(data?.localTestCode?'本地测试验证码：'+data.localTestCode:'验证码已发送。');});
  bind('reset-verify',async body=>{const data=await send('/api/admin/auth/reset/verify','POST',body);$('#reset-password [name=ticket]').value=data.ticket;notice('验证通过，请设置新密码。');});
  bind('reset-password',async body=>{await send('/api/admin/auth/reset/password','POST',body);$('#reset-password').reset();notice('密码已重置，请重新登录。');});
  $('#index-import').onclick=e=>run(e.currentTarget,async()=>{if(confirm('确认全量导入商品搜索索引？'))result('system-result',await send('/api/admin/search-index/import','POST'));});
  $('#bloom-rebuild').onclick=e=>run(e.currentTarget,async()=>{if(confirm('确认重建商品布隆过滤器？')){await send('/api/admin/products/bloom-filter/rebuild','POST');notice('布隆过滤器已重建。');}});
  $('#keywords-load').onclick=e=>run(e.currentTarget,async()=>{$('#keywords-edit [name=keywords]').value=JSON.stringify(await send('/api/admin/products/keywords'),null,2);});
  bind('keywords-edit',async body=>{const words=JSON.parse(body.keywords);if(!Array.isArray(words))throw Error('关键词须为数组');await send('/api/admin/products/keywords','PUT',words);notice('搜索关键词已保存。');});
}
render('home');
