'use strict';
const refundLabels = {refundRequested:'申请退款',refunding:'退款中',refundSuccess:'退款成功',refundRejected:'申请失败'};
let busy = false;
async function send(path, method, body) {
  const token = sessionStorage.getItem('lxp.admin.token');
  if (!token) throw Error('请先返回管理首页登录。');
  const response = await fetch(path, {method, headers:{Accept:'application/json',Authorization:'Bearer ' + token,
    ...(body ? {'Content-Type':'application/json'} : {})}, ...(body ? {body:JSON.stringify(body)} : {})});
  const json = await response.json();
  if (!response.ok || json.code !== 200) throw Error(json.message || '请求失败');
  return json.data;
}
document.querySelector('#refund-actions').onsubmit = async e => {
  e.preventDefault();
  if (busy) return;
  const button = e.submitter;
  if (!button) return;
  busy = true; button.disabled = true;
  try {
    const orderNo = document.querySelector('#refund-order-no').value.trim();
    if (!orderNo) throw Error('请填写订单号。');
    const action = button.dataset.action;
    const path = '/api/admin/orders/' + encodeURIComponent(orderNo) + '/refund';
    let result;
    if (action === 'retry') result = await send(path + '/retry', 'POST');
    else {
      const reason = document.querySelector('#refund-reason').value.trim();
      if (action === 'reject' && !reason) throw Error('拒绝退款时请填写原因。');
      result = await send(path + '/review', 'PUT', {approved:action === 'approve',reason});
    }
    document.querySelector('#refund-status').textContent = `订单 ${orderNo}：${refundLabels[result.payStatus] || result.payStatus}${result.refundFailReason ? '；' + result.refundFailReason : ''}`;
  } catch (ex) { document.querySelector('#refund-status').textContent = ex.message; }
  finally { busy = false; button.disabled = false; }
};
