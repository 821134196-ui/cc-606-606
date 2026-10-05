// 统一 API 封装：错误时抛出后端中文 error 文案
async function request(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(data?.error || `请求失败（${res.status}）`);
  }
  return data;
}

export const api = {
  get: (u) => request('GET', u),
  post: (u, b) => request('POST', u, b ?? {}),
  categories: () => request('GET', '/api/staff/categories'),
  staff: () => request('GET', '/api/staff/staff'),
  appointments: () => request('GET', '/api/staff/appointments'),
  appointment: (id) => request('GET', `/api/staff/appointments/${id}`),
  createAppointment: (b) => request('POST', '/api/staff/appointments', b),
  check: (id, receivedQty) => request('POST', `/api/staff/appointments/${id}/check`, { receivedQty }),
  confirmDifference: (id, queryCode, note) =>
    request('POST', `/api/staff/appointments/${id}/confirm-difference`, { queryCode, note }),
  addDrug: (id, b) => request('POST', `/api/staff/appointments/${id}/drugs`, b),
  complete: (id) => request('POST', `/api/staff/appointments/${id}/complete`),
  seal: (id, staff, b) =>
    request('POST', `/api/staff/appointments/${id}/seal?staff=${encodeURIComponent(staff)}`, b),
  bags: (status) => request('GET', `/api/staff/bags${status ? `?status=${status}` : ''}`),
  reopen: (bagNo, b) => request('POST', `/api/staff/bags/${bagNo}/reopen`, b),
  reseal: (bagNo, staff, location, fridgeTemp) => {
    const p = new URLSearchParams({ staff });
    if (location) p.set('location', location);
    if (fridgeTemp !== undefined && fridgeTemp !== '') p.set('fridgeTemp', fridgeTemp);
    return request('POST', `/api/staff/bags/${bagNo}/reseal?${p}`);
  },
  adjust: (bagNo, b) => request('POST', `/api/staff/bags/${bagNo}/adjust`, b),
  coldLog: (bagNo, b) => request('POST', `/api/staff/bags/${bagNo}/cold-logs`, b),
  transfer: (bagNo, b) => request('POST', `/api/staff/bags/${bagNo}/transfer`, b),
  handover: (bagNo, b) => request('POST', `/api/staff/bags/${bagNo}/handover`, b),
  sign: (bagNo, b) => request('POST', `/api/staff/bags/${bagNo}/sign`, b),
  trace: (bagNo) => request('GET', `/api/staff/bags/${bagNo}/trace`),
  staffInbox: (user) => request('GET', `/api/staff/notifications?user=${encodeURIComponent(user)}`),
  receipt: (apptNo, queryCode) =>
    request('POST', '/api/resident/receipt', { apptNo, queryCode }),
  residentInbox: (apptNo, queryCode) =>
    request('POST', '/api/resident/notifications', { apptNo, queryCode })
};
