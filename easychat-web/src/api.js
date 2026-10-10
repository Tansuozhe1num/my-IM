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
  if (!res.ok || body.status === 'error') {
    const error = new Error(body.info || `请求失败 (${res.status})`)
    error.status = res.status
    throw error
  }
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
  saveGroup: values => {
    const body = new FormData()
    Object.entries(values).forEach(([key, value]) => value != null && body.append(key, value))
    return request('/group/savegroup', { method: 'POST', body })
  },
  updateUserInfo: values => {
    const body = new FormData()
    Object.entries(values).forEach(([key, value]) => value != null && body.append(key, value))
    return request('/user/updateUserInfo', { method: 'POST', body })
  },
  avatarUrl: (id, version) => API_BASE + '/file/avatar/' + encodeURIComponent(id) + (version ? '?v=' + version : ''),
  createGroup: values => api.saveGroup(values),
  groupInfo: id => request(`/group/getgroupinfo?groupId=${encodeURIComponent(id)}`, { method: 'POST' }),
  sessions: userId => request(`/chatSessionUser/loadDataList?userId=${encodeURIComponent(userId)}&getLastMessage=true&pageNo=1&pageSize=50`, { method: 'POST' }),
  messages: sessionId => request(`/chatMessage/loadDataList?sessionId=${encodeURIComponent(sessionId)}&pageNo=1&pageSize=50&orderBy=send_time asc`, { method: 'POST' }),
  sendMessage: values => request('/chat/sendMessage', {
    method: 'POST',
    body: form({
      ContactId: values.contactId,
      MessageContext: values.messageContent,
      messageType: 2,
      clientMessageId: values.clientMessageId,
    }),
  }),
}

export function connectSocket(token, onMessage, onState) {
  // Native browser WebSocket cannot add custom headers, so the token is part of the handshake URL.
  const url = `${WS_BASE}?token=${encodeURIComponent(token)}`
  let socket = null
  let retryTimer = null
  let retryCount = 0
  let disposed = false

  const scheduleReconnect = () => {
    if (disposed || retryTimer !== null) return
    const delay = Math.min(30000, 500 * 2 ** Math.min(retryCount++, 6))
    retryTimer = window.setTimeout(() => {
      retryTimer = null
      connect()
    }, Math.round(delay * (0.8 + Math.random() * 0.4)))
  }

  const connect = () => {
    if (disposed) return
    try {
      socket = new WebSocket(url)
    } catch {
      onState?.('error')
      scheduleReconnect()
      return
    }
    const current = socket
    current.onopen = () => {
      if (socket !== current) return
      retryCount = 0
      onState?.('open')
    }
    current.onclose = () => {
      if (socket !== current) return
      if (!disposed) onState?.('closed')
      scheduleReconnect()
    }
    current.onerror = () => {
      if (socket === current) onState?.('error')
      current.close()
    }
    current.onmessage = event => {
      try { onMessage?.(JSON.parse(event.data)) } catch { /* heartbeat or non-JSON frame */ }
    }
  }

  const connection = {
    get readyState() { return socket?.readyState ?? WebSocket.CLOSED },
    send(data) {
      if (socket?.readyState !== WebSocket.OPEN) throw new Error('WebSocket is not connected')
      socket.send(data)
    },
    close() {
      disposed = true
      if (retryTimer !== null) window.clearTimeout(retryTimer)
      retryTimer = null
      socket?.close()
    },
  }

  connect()
  return connection
}
