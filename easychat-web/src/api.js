const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:5050/api'
const WS_BASE = import.meta.env.VITE_WS_URL || 'ws://localhost:5051/ws'

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = localStorage.getItem('easychat-token')
  if (token) headers.set('token', token)
  if (!(options.body instanceof FormData) && options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8')
  }
  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const body = await res.json().catch(() => ({}))
  if (!res.ok || body.status === 'error') throw new Error(body.info || `请求失败 (${res.status})`)
  return body.data
}

const form = values => new URLSearchParams(Object.entries(values).filter(([, value]) => value !== undefined && value !== null))

export const api = {
  checkCode: () => request('/account/checkcode', { method: 'POST' }),
  login: values => request('/account/login', { method: 'POST', body: form(values) }),
  register: values => request('/account/register', { method: 'POST', body: form(values) }),
  me: () => request('/user/getUserInfo', { method: 'POST' }),
  settings: () => request('/account/getSysSetting', { method: 'POST' }),
  contacts: type => request(`/contact/loadContact${type == null ? '' : `?ContactType=${type}`}`, { method: 'POST' }),
  applications: () => request('/contact/loadApply', { method: 'POST' }),
  search: id => request(`/contact/searchfriends?ContactId=${encodeURIComponent(id)}`, { method: 'POST' }),
  applyAdd: (id, type, info) => request(`/contact/applyAdd?ContactId=${encodeURIComponent(id)}&ContactType=${type}&ApplyInfo=${encodeURIComponent(info || '申请添加')}`, { method: 'POST' }),
  solveApply: (applyId, accept) => request(`/contact/solveApply?applyId=${encodeURIComponent(applyId)}&accept=${accept}`, { method: 'POST' }),
  groups: () => request('/group/loadmygroup', { method: 'POST' }),
  createGroup: values => {
    const body = new FormData()
    Object.entries(values).forEach(([key, value]) => value != null && body.append(key, value))
    return request('/group/savegroup', { method: 'POST', body })
  },
  groupInfo: id => request(`/group/getgroupinfo?groupId=${encodeURIComponent(id)}`, { method: 'POST' }),
  sessions: userId => request(`/chatSessionUser/loadDataList?userId=${encodeURIComponent(userId)}&getLastMessage=true&pageNo=1&pageSize=50`, { method: 'POST' }),
  messages: sessionId => request(`/chatMessage/loadDataList?sessionId=${encodeURIComponent(sessionId)}&pageNo=1&pageSize=50&orderBy=send_time asc`, { method: 'POST' }),
}

export function connectSocket(token, onMessage, onState) {
  // Native browser WebSocket cannot add custom headers, so the token is part of the handshake URL.
  const url = `${WS_BASE}?token=${encodeURIComponent(token)}`
  const socket = new WebSocket(url)
  socket.onopen = () => onState?.('open')
  socket.onclose = () => onState?.('closed')
  socket.onerror = () => onState?.('error')
  socket.onmessage = event => { try { onMessage?.(JSON.parse(event.data)) } catch { /* heartbeat or non-JSON frame */ } }
  return socket
}
