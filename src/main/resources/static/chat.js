'use strict';
// 同一份页面用于两端；管理端通过 body 属性选择受保护的管理接口。
const admin = document.body.dataset.admin === 'true';
const prefix = admin ? '/api/admin/chat' : '/api/chat';
const tokenKey = admin ? 'lxp.admin.token' : 'lxp.token';
const $ = selector => document.querySelector(selector);
let socket, heartbeat, ackTimer, config, contact = '', page = 1, generation = 0, connectionGeneration = 0, pending = false;
const messages = new Map();
function status(text, error = false) { $('#status').textContent = text; $('#status').className = error ? 'error' : ''; }
function syncSend() {
  $('#send-button').disabled = pending || !contact || socket?.readyState !== WebSocket.OPEN;
  $('#content').disabled = pending;
}
async function api(path, method = 'GET') {
  const token = sessionStorage.getItem(tokenKey);
  if (!token) throw Error('请先返回首页登录。');
  const response = await fetch(prefix + path, {method, headers:{Authorization:'Bearer ' + token}, signal:AbortSignal.timeout(15000)});
  const body = await response.json();
  if (!response.ok || body.code !== 200) throw Error(body.message || '请求失败');
  return body.data;
}
async function run(action) { try { await action(); } catch (error) { status(error.message, true); } }
async function sessions() {
  const rows = await api('/sessions');
  $('#sessions').replaceChildren();
  if (!rows.length) $('#sessions').textContent = '暂无会话，输入联系人 ID 开始聊天。';
  for (const row of rows) {
    const button = document.createElement('button');
    button.textContent = `${row.contactNickname}（${row.contactId}） · 未读 ${row.unreadCount}\n${row.lastMsgContent || ''}`;
    button.onclick = () => run(() => openContact(String(row.contactId)));
    $('#sessions').append(button);
  }
}
function paint(scrollToBottom) {
  const list = $('#messages');
  const previousHeight = list.scrollHeight;
  const previousTop = list.scrollTop;
  list.replaceChildren();
  const rows = [...messages.values()].sort((a,b) => BigInt(a.id) < BigInt(b.id) ? -1 : 1);
  for (const row of rows) {
    const item = document.createElement('li');
    if (String(row.fromUserId) === config?.userId) item.className = 'mine';
    const meta = document.createElement('small');
    meta.textContent = `${row.fromUserId} · ${row.createTime || ''} · ${['文本','图片','商品卡片','语音'][row.msgType] || '消息'}`;
    item.append(meta, document.createTextNode(row.content + (row.productId ? `\n商品 ID：${row.productId}` : '')));
    list.append(item);
  }
  list.scrollTop = scrollToBottom ? list.scrollHeight : previousTop + list.scrollHeight - previousHeight;
}
async function history(reset = false) {
  if (!contact) return;
  const version = generation, target = contact, next = reset ? 1 : page + 1;
  const data = await api(`/history/${encodeURIComponent(target)}?page=${next}&size=30`);
  if (version !== generation || target !== contact) return;
  if (reset) messages.clear();
  for (const row of data.list) messages.set(String(row.id), row);
  page = next;
  $('#older').disabled = page >= data.totalPages;
  paint(reset);
}
async function openContact(id) {
  if (!/^[1-9]\d*$/.test(id) || BigInt(id) > 9223372036854775807n) throw Error('请输入有效联系人 ID');
  if (id === config?.userId) throw Error('不能与自己聊天');
  contact = id; generation++; page = 1; messages.clear(); $('#messages').replaceChildren();
  $('#contact').value = id; $('#conversation').textContent = '正在与 ' + id + ' 对话'; $('#read').disabled = false;
  syncSend(); await history(true);
}
async function connect() {
  const attempt = ++connectionGeneration;
  const response = await api('/config');
  if (attempt !== connectionGeneration) return;
  config = response;
  if (!config.enabled) throw Error('客服 WebSocket 已关闭');
  if (socket) { socket.onopen = socket.onmessage = socket.onerror = socket.onclose = null; socket.close(); }
  clearInterval(heartbeat); clearTimeout(ackTimer); pending = false;
  const url = new URL(config.publicUrl || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.hostname}:${config.port}`);
  if (!['ws:','wss:'].includes(url.protocol)) throw Error('客服服务地址须为 ws 或 wss');
  url.pathname = config.websocketPath;
  url.searchParams.delete('device'); url.searchParams.delete('clientType');
  url.searchParams.set('token', sessionStorage.getItem(tokenKey));
  $('#identity').textContent = '当前账号：' + config.userId;
  socket = new WebSocket(url.href); syncSend(); status('正在连接…');
  socket.onopen = () => {
    status('已连接'); syncSend();
    heartbeat = setInterval(() => { if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({action:'HEARTBEAT'})); }, 20000);
  };
  socket.onclose = () => {
    clearInterval(heartbeat); clearTimeout(ackTimer); pending = false; syncSend();
    status('连接已断开；若消息未收到确认，请先查询历史，再决定是否重发。', true);
  };
  socket.onerror = () => status('连接失败，请检查登录状态、客服服务和来源配置。', true);
  socket.onmessage = event => run(async () => {
    const payload = JSON.parse(event.data);
    if (payload.action === 'PUSH_MSG') {
      const row = payload.data;
      if (String(row.fromUserId) === contact) { messages.set(String(row.id), row); paint(true); }
      await sessions();
    } else if (payload.action === 'MSG_ACK') {
      clearTimeout(ackTimer); pending = false; syncSend(); $('#content').value = '';
      status('消息已保存（ID：' + payload.data.msgId + '），离线联系人可查看历史记录。');
      await history(true); await sessions();
    } else if (payload.action === 'ERROR') {
      clearTimeout(ackTimer); pending = false; syncSend(); status(payload.data.message, true);
    }
  });
  await sessions();
}
$('#connect').onclick = () => run(connect);
$('#refresh').onclick = () => run(sessions);
$('#open').onsubmit = event => { event.preventDefault(); run(() => openContact($('#contact').value.trim())); };
$('#older').onclick = () => run(() => history());
$('#read').onclick = () => run(async () => {
  await api('/clearUnread/' + encodeURIComponent(contact), 'POST'); await sessions(); status('本会话未读数已清除。');
});
$('#send').onsubmit = event => { event.preventDefault(); run(async () => {
  if (pending || !contact || socket?.readyState !== WebSocket.OPEN) throw Error('请先连接并打开会话');
  const content = $('#content').value;
  if (!content.trim()) throw Error('消息不能为空');
  const productId = $('#product').value.trim();
  if (productId && (!/^[1-9]\d*$/.test(productId) || BigInt(productId) > 9223372036854775807n)) throw Error('商品 ID 无效');
  socket.send(JSON.stringify({action:'SEND_MSG', data:{toUserId:contact, content, msgType:Number($('#type').value), ...(productId ? {productId} : {})}}));
  pending = true; syncSend(); status('等待保存确认…');
  ackTimer = setTimeout(() => {
    // 原协议没有客户端幂等号；不自动重发，避免将已保存消息重复写入。
    socket.close(); status('确认超时，请重连并核对历史记录。', true);
  }, 15000);
}); };
window.addEventListener('pagehide', () => { clearInterval(heartbeat); clearTimeout(ackTimer); socket?.close(); });
run(sessions);
