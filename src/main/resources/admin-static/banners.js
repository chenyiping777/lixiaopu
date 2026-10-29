'use strict';
const $ = s => document.querySelector(s);
const escapeHtml = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const safeUrl = value => {
  if (!value) return '';
  try { const url = new URL(value, location.origin); return ['http:', 'https:'].includes(url.protocol) ? url.href : ''; }
  catch { return ''; }
};
let pageNum = 1, pages = 0, items = [], loading = false;
async function send(path, method = 'GET', body) {
  const token = sessionStorage.getItem('lxp.admin.token');
  if (!token) throw Error('请先在管理端首页登录。');
  const response = await fetch('/api/admin/banner' + path, {method, headers: {
    Accept: 'application/json', Authorization: 'Bearer ' + token,
    ...(body ? {'Content-Type':'application/json'} : {})
  }, ...(body ? {body: JSON.stringify(body)} : {})});
  const result = await response.json();
  if (!response.ok || !result.success || result.code !== 200) throw Error(result.message || '请求失败');
  return result.data;
}
async function run(button, action) {
  if (button) button.disabled = true;
  try { await action(); } catch (error) { $('#status').textContent = error.message; }
  finally { if (button) button.disabled = false; syncPager(); }
}
function syncPager() {
  $('#prev').disabled = loading || pageNum <= 1;
  $('#next').disabled = loading || pageNum >= pages;
}
async function load() {
  loading = true; syncPager();
  try {
    let data = await send('/list?pageNum=' + pageNum + '&pageSize=10');
    // 删除末页最后一条时回到新的最后一页。
    if (pageNum > Math.max(1, data.pages)) {
      pageNum = Math.max(1, data.pages);
      data = await send('/list?pageNum=' + pageNum + '&pageSize=10');
    }
    pages = data.pages; items = data.list;
    $('#rows').innerHTML = items.length ? items.map(b => '<tr><td>' +
      (safeUrl(b.imageUrl) ? '<img src="' + escapeHtml(safeUrl(b.imageUrl)) + '" alt="' + escapeHtml(b.title) + '">' : '无图片') +
      '</td><td>' + escapeHtml(b.id) + '<br>' + escapeHtml(b.title) +
      '</td><td><input aria-label="排序值" data-sort="' + escapeHtml(b.id) + '" type="number" min="0" max="2147483647" step="1" value="' + escapeHtml(b.sort) + '"><button data-action="sort" data-id="' + escapeHtml(b.id) + '">保存排序</button></td><td>' +
      (b.status === 1 ? '启用' : '禁用') + '</td><td><div class="actions">' +
      ['edit','status','delete'].map((a,i) => '<button data-action="' + a + '" data-id="' + escapeHtml(b.id) + '">' +
      ['编辑', b.status === 1 ? '禁用' : '启用', '删除'][i] + '</button>').join('') + '</div></td></tr>').join('') :
      '<tr><td colspan="5">暂无轮播图，请先新增。</td></tr>';
    $('#page-info').textContent = '第 ' + pageNum + ' 页 / 共 ' + pages + ' 页 · ' + data.total + ' 条';
  } finally { loading = false; syncPager(); }
}
$('#banner-form').onsubmit = e => {
  e.preventDefault();
  run(e.submitter, async () => {
    const body = Object.fromEntries(new FormData(e.target));
    body.sort = Number(body.sort); body.status = Number(body.status);
    if (!safeUrl(body.imageUrl) || (body.linkUrl && !safeUrl(body.linkUrl))) throw Error('图片和跳转地址须为 http(s) 地址或站内路径。');
    const update = Boolean(body.id);
    if (!update) delete body.id;
    await send(update ? '/update' : '/add', update ? 'PUT' : 'POST', body);
    e.target.reset(); $('#form-title').textContent = '新增轮播图';
    $('#status').textContent = '保存成功。'; await load();
  });
};
$('#reset-form').onclick = () => { $('#banner-form').reset(); $('#form-title').textContent = '新增轮播图'; };
$('#reload').onclick = e => run(e.currentTarget, load);
$('#prev').onclick = () => { if (loading || pageNum <= 1) return; pageNum--; run(null, load); };
$('#next').onclick = () => { if (loading || pageNum >= pages) return; pageNum++; run(null, load); };
$('#rows').onclick = e => {
  const button = e.target.closest('button[data-action]');
  if (!button) return;
  const item = items.find(b => String(b.id) === button.dataset.id);
  if (!item) return;
  const action = button.dataset.action;
  if (action === 'edit') {
    for (const key of ['id','title','imageUrl','linkUrl','linkType','sort','status'])
      $('#banner-form').elements[key].value = item[key] ?? '';
    $('#form-title').textContent = '修改轮播图'; $('#banner-form').scrollIntoView({block:'start'});
    return;
  }
  run(button, async () => {
    if (action === 'delete') {
      if (!confirm('确定删除轮播图“' + item.title + '”？')) return;
      await send('/delete?id=' + encodeURIComponent(item.id), 'DELETE');
    } else if (action === 'status') {
      await send('/updateStatus', 'PUT', {id:item.id, status:item.status === 1 ? 0 : 1});
    } else if (action === 'sort') {
      const input = button.closest('td').querySelector('input');
      if (!input.reportValidity() || input.value === '') throw Error('排序值须为非负整数。');
      await send('/updateSort', 'PUT', {id:item.id, sort:Number(input.value)});
    }
    $('#status').textContent = '操作成功。'; await load();
  });
};
run(null, load);
