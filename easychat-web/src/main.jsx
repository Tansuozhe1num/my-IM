import React, { useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { api, connectSocket } from './api'
import { Search, Plus, MessageCircle, Users, Compass, Settings, Smile, Paperclip, Mic, Send, UserPlus, MoreHorizontal, LogOut, RefreshCw, X, Check, ShieldOff, ChevronLeft, Mail, MapPin, CalendarDays } from 'lucide-react'
import './styles.css'

const initials = value => (value || '?').trim().slice(0, 1).toUpperCase()
const timeText = value => value ? new Date(Number(value) || value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
const sessionTime = value => Number(value) || Date.parse(value) || 0

function Auth({ onLogin }) {
  const [mode, setMode] = useState('login'); const [code, setCode] = useState(null); const [form, setForm] = useState({ email: '', nickname: '', password: '', checkCode: '' }); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const refresh = async () => { try { setCode(await api.checkCode()) } catch (e) { setError(e.message) } }
  useEffect(() => { refresh() }, [])
  const submit = async e => { e.preventDefault(); if (!code?.checkCodeKey) { setError('验证码尚未加载，请刷新后重试'); await refresh(); return } setBusy(true); setError(''); try { const data = await (mode === 'login' ? api.login({ ...form, checkcodekey: code.checkCodeKey }) : api.register({ ...form, checkcodekey: code.checkCodeKey })); if (mode === 'login') { localStorage.setItem('easychat-token', data.token); onLogin(data) } else { setMode('login'); setForm(f => ({ ...f, password: '', checkCode: '' })); await refresh() } } catch (err) { setError(err.message); await refresh() } finally { setBusy(false) } }
  return <main className="auth-shell"><div className="auth-card"><div className="brand"><div className="brand-mark">E</div><div><strong>EasyChat</strong><span>连接每一刻</span></div></div><div className="auth-copy"><p className="eyebrow">WECHAT INSPIRED IM</p><h1>{mode === 'login' ? '欢迎回来' : '创建账号'}</h1><p>{mode === 'login' ? '登录 EasyChat，继续你的对话。' : '用一个昵称开始你的聊天空间。'}</p></div><form onSubmit={submit} className="auth-form">{mode === 'register' && <input required placeholder="昵称" value={form.nickname} onChange={e => setForm({ ...form, nickname: e.target.value })} />}<input required type="email" placeholder="邮箱" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /><input required type="password" minLength="6" placeholder="密码" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /><div className="code-row"><input required placeholder="验证码结果" value={form.checkCode} onChange={e => setForm({ ...form, checkCode: e.target.value })} />{code?.checkCode && <button type="button" className="captcha" onClick={refresh}><img src={code.checkCode} alt="验证码" /></button>}<button type="button" className="icon-btn subtle" title="刷新验证码" onClick={refresh}><RefreshCw size={16} /></button></div>{error && <div className="error">{error}</div>}<button className="primary-btn" disabled={busy}>{busy ? '处理中...' : mode === 'login' ? '登录' : '注册'}</button></form><button className="text-btn" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); refresh() }}>{mode === 'login' ? '没有账号？立即注册' : '已有账号？返回登录'}</button></div><div className="auth-note">API: {import.meta.env.VITE_API_BASE || 'http://localhost:5050/api'}</div></main>
}

function Avatar({ name, tone = 'green', small = false, onClick, title }) { return <div className={`avatar ${tone} ${small ? 'small' : ''} ${onClick ? 'clickable' : ''}`} onClick={onClick} title={title}>{initials(name)}</div> }
const asList = value => Array.isArray(value) ? value : []

function ContactDetails({ contact, loading, onClose, onChat }) {
  const name = contact?.nickName || contact?.contactName || contact?.name || '联系人'
  const isGroup = contact?.contactType === 1 || contact?.contactId?.startsWith('G')
  const sex = contact?.sex === 1 ? '男' : contact?.sex === 0 ? '女' : ''
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <section className="contact-details" role="dialog" aria-modal="true" aria-label={`${name}的联系人详情`} onMouseDown={e => e.stopPropagation()}>
      <button className="icon-btn details-close" onClick={onClose} title="关闭"><X size={18} /></button>
      <div className="details-hero"><Avatar name={name} tone="blue" /><div><h2>{name}</h2><span>{contact?.userId || contact?.contactId || '联系人'}</span></div></div>
      {loading ? <div className="details-loading">正在加载联系人信息...</div> : <div className="details-body">
        <p className="details-signature">{isGroup ? contact?.groupNotice || '这个群组还没有群公告' : contact?.personalSignature || '这个人还没有填写个性签名'}</p>
        <dl>
          {isGroup && <div><dt><Users size={15} />群号</dt><dd>{contact?.contactId}</dd></div>}
          {isGroup && contact?.memberCount != null && <div><dt><Users size={15} />群成员</dt><dd>{contact.memberCount}</dd></div>}
          {contact?.email && <div><dt><Mail size={15} />邮箱</dt><dd>{contact.email}</dd></div>}
          {(contact?.areaName || sex) && <div><dt><MapPin size={15} />资料</dt><dd>{[sex, contact.areaName].filter(Boolean).join(' · ')}</dd></div>}
          {contact?.createTime && <div><dt><CalendarDays size={15} />加入时间</dt><dd>{String(contact.createTime).replace('T', ' ').slice(0, 16)}</dd></div>}
        </dl>
      </div>}
      <div className="details-actions"><button className="primary-btn" onClick={onChat}><MessageCircle size={16} />发消息</button></div>
    </section>
  </div>
}

function ApplicationsPanel({ applications, processing, onAction, onClose }) {
  return <section className="applications-panel"><header className="applications-header"><div><span className="kicker">CONTACT REQUESTS</span><h2>好友与群聊申请</h2><p>处理新的联系人请求</p></div><button className="icon-btn" onClick={onClose} title="关闭"><X size={18} /></button></header><div className="applications-list">{applications.map(item => { const isGroup = item.contactType === 1; return <article className="application-card" key={item.applyId}><Avatar name={item.applyUserId} tone="blue" /><div className="application-content"><div className="application-title"><strong>{item.applyUserId}</strong><time>{item.lastApplyTime ? new Date(item.lastApplyTime).toLocaleString('zh-CN') : ''}</time></div><p>{item.applyInfo || (isGroup ? `申请加入群组 ${item.contactId}` : '请求添加你为好友')}</p><div className="application-actions"><button className="apply-accept" disabled={processing === `${item.applyId}-1`} onClick={() => onAction(item, 1)}><Check size={15} />{isGroup ? '批准入群' : '接受'}</button><button className="apply-reject" disabled={processing === `${item.applyId}-2`} onClick={() => onAction(item, 2)}><X size={15} />拒绝</button>{!isGroup && <button className="apply-block" disabled={processing === `${item.applyId}-3`} onClick={() => onAction(item, 3)}><ShieldOff size={15} />拉黑</button>}</div></div></article>})}{!applications.length && <div className="applications-empty"><div className="empty-icon">✓</div><h3>没有待处理申请</h3><p>新的好友和群聊申请会显示在这里</p></div>}</div></section>
}

function CreateGroupDialog({ onClose, onCreated }) {
  const [form, setForm] = useState({ groupName: '', groupNotice: '', joinType: '1', avatorfile: null })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async event => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api.createGroup(form)
      await onCreated()
      onClose()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <section className="create-group" role="dialog" aria-modal="true" aria-label="创建群聊" onMouseDown={e => e.stopPropagation()}>
      <header><div><span className="kicker">NEW GROUP</span><h2>创建群聊</h2></div><button className="icon-btn" onClick={onClose} title="关闭"><X size={18} /></button></header>
      <form onSubmit={submit}>
        <label>群名称<input required maxLength={40} value={form.groupName} onChange={e => setForm({ ...form, groupName: e.target.value })} placeholder="给群聊取个名字" /></label>
        <label>群公告<textarea maxLength={300} value={form.groupNotice} onChange={e => setForm({ ...form, groupNotice: e.target.value })} placeholder="介绍一下这个群" /></label>
        <fieldset><legend>加入方式</legend><label><input type="radio" name="joinType" value="1" checked={form.joinType === '1'} onChange={e => setForm({ ...form, joinType: e.target.value })} />需要群主审批</label><label><input type="radio" name="joinType" value="0" checked={form.joinType === '0'} onChange={e => setForm({ ...form, joinType: e.target.value })} />允许直接加入</label></fieldset>
        <label className="group-avatar-upload">群头像<input type="file" accept="image/*" onChange={e => setForm({ ...form, avatorfile: e.target.files?.[0] || null })} /><span>{form.avatorfile?.name || '选择图片（可选）'}</span></label>
        {error && <div className="error">{error}</div>}
        <footer><button type="button" className="cancel-btn" onClick={onClose}>取消</button><button className="primary-btn" disabled={busy}>{busy ? '创建中...' : '创建群聊'}</button></footer>
      </form>
    </section>
  </div>
}

function App() {
  const [user, setUser] = useState(null); const [loading, setLoading] = useState(!!localStorage.getItem('easychat-token')); const [section, setSection] = useState('chats'); const [contacts, setContacts] = useState([]); const [groups, setGroups] = useState([]); const [applications, setApplications] = useState([]); const [sessions, setSessions] = useState([]); const [active, setActive] = useState(null); const [showApplications, setShowApplications] = useState(false); const [showCreateGroup, setShowCreateGroup] = useState(false); const [processingApply, setProcessingApply] = useState(null); const [messages, setMessages] = useState([]); const [search, setSearch] = useState(''); const [searchResult, setSearchResult] = useState(null); const [composer, setComposer] = useState(''); const [notice, setNotice] = useState(''); const [socketState, setSocketState] = useState('offline'); const [contactDetails, setContactDetails] = useState(null); const socketRef = useRef(null); const selectionRef = useRef(''); const pendingMessagesRef = useRef(new Map()); const activeRef = useRef(active); activeRef.current = active
  const load = async current => { const me = current || await api.me(); setUser(me); const results = await Promise.allSettled([api.contacts(0), api.groups(), api.applications(), api.sessions(me.userId)]); const [friendData, groupData, applyData, sessionData] = results; setContacts(friendData.status === 'fulfilled' ? asList(friendData.value?.friendUserInfo) : []); setGroups(groupData.status === 'fulfilled' ? asList(groupData.value).map(group => ({ ...group, contactId: group.groupId, contactName: group.groupName, contactType: 1 })) : []); setApplications(applyData.status === 'fulfilled' ? asList(applyData.value) : []); const sessionResult = sessionData.status === 'fulfilled' ? sessionData.value : null; setSessions(asList(sessionResult?.list || sessionResult).map(session => session.contactId?.startsWith('G') ? { ...session, contactType: 1 } : session)); const failed = results.map((result, index) => result.status === 'rejected' ? ['联系人', '群组', '好友申请', '会话'][index] : null).filter(Boolean); if (failed.length) setNotice(`部分数据加载失败：${failed.join('、')}。请检查后端接口和浏览器控制台。`); setLoading(false) }
  useEffect(() => { if (localStorage.getItem('easychat-token')) load().catch(e => { localStorage.removeItem('easychat-token'); setLoading(false); setNotice(e.message) }) }, [])
  useEffect(() => {
    if (!user) return
    const ws = connectSocket(localStorage.getItem('easychat-token'), message => {
      const init = message?.extendData || message
      if (message?.messageType === 0 && init?.chatMessages) {
        setMessages(asList(init.chatMessages))
        if (init.chatSessionUsers) setSessions(asList(init.chatSessionUsers).map(session => session.contactId?.startsWith('G') ? { ...session, contactType: 1 } : session))
        return
      }
      if (message?.messageType === 4) {
        api.applications().then(data => setApplications(asList(data))).catch(() => {})
        setNotice('收到新的好友或群聊申请')
        return
      }
      if (message?.status === 2) {
        const failedMessage = pendingMessagesRef.current.get(message.clientMessageId)
        if (message.clientMessageId) pendingMessagesRef.current.delete(message.clientMessageId)
        if (failedMessage && activeRef.current?.sessionId === failedMessage.sessionId) setComposer(current => current || failedMessage.content)
        setNotice(message.messageContent || '消息发送失败')
        return
      }
      if (!message?.sessionId || !message?.messageContent) return
      if (message.clientMessageId) pendingMessagesRef.current.delete(message.clientMessageId)

      setMessages(old => old.some(item =>
        (message.messageId && item.messageId === message.messageId)
        || (message.clientMessageId && item.clientMessageId === message.clientMessageId)
      ) ? old : [...old, message])
      const sessionData = message.extendData?.sessionId ? {
        ...message.extendData,
        contactType: message.extendData.contactId?.startsWith('G') ? 1 : 0,
        lastMessage: message.messageContent,
        lastReceiveTime: message.sendTime || message.extendData.lastReceiveTime,
      } : null
      if (sessionData?.contactId) {
        if (sessionData.contactType === 1) {
          setGroups(old => old.some(item => item.contactId === sessionData.contactId)
            ? old.map(item => item.contactId === sessionData.contactId ? { ...item, groupName: sessionData.contactName || item.groupName, contactName: sessionData.contactName || item.contactName } : item)
            : [...old, { contactId: sessionData.contactId, groupId: sessionData.contactId, contactType: 1, groupName: sessionData.contactName, contactName: sessionData.contactName }])
        } else {
          setContacts(old => old.some(item => item.contactId === sessionData.contactId)
            ? old.map(item => item.contactId === sessionData.contactId ? { ...item, nickName: sessionData.contactName || item.nickName } : item)
            : [...old, { contactId: sessionData.contactId, contactType: 0, nickName: sessionData.contactName, status: 1 }])
        }
        setActive(old => old && !old.sessionId && old.contactId === sessionData.contactId ? { ...old, ...sessionData } : old)
      }
      setSessions(old => {
        const updated = sessionData
          ? [sessionData, ...old.filter(item => item.sessionId !== sessionData.sessionId)]
          : old.map(item => item.sessionId === message.sessionId
            ? { ...item, lastMessage: message.messageContent, lastReceiveTime: message.sendTime }
            : item)
        return updated.sort((a, b) => sessionTime(b.lastReceiveTime) - sessionTime(a.lastReceiveTime))
      })
    }, setSocketState)
    socketRef.current = ws
    return () => ws.close()
  }, [user])
  const activeTitle = active?.contactName || active?.name || '选择一个聊天'; const currentMessages = active?.sessionId ? messages.filter(m => m.sessionId === active.sessionId) : []
  const contactKey = item => `${item?.contactType ?? 0}:${item?.contactId || item?.groupId || item?.sessionId || item?.name || ''}`
  const selectChat = async item => {
    const session = item.sessionId ? item : sessions.find(candidate => candidate.contactId === item.contactId && (candidate.contactType ?? 0) === (item.contactType ?? 0))
    const selected = { ...item, ...(session || {}), name: item.name || session?.contactName, contactName: item.contactName || session?.contactName || item.name }
    const key = contactKey(selected)
    selectionRef.current = key
    setActive(selected)
    setMessages([])
    setComposer('')
    if (!selected.sessionId) return
    try {
      const data = await api.messages(selected.sessionId)
      if (selectionRef.current === key) setMessages(asList(data?.list || data))
    } catch (e) {
      if (selectionRef.current === key) setNotice(e.message)
    }
  }
  const openContactDetails = async item => {
    const contactId = item.contactId || item.userId || item.groupId
    const contact = { ...item, contactId, contactType: item.contactType ?? (contactId?.startsWith('G') ? 1 : 0), nickName: item.nickName || item.contactName || item.groupName || item.name }
    setContactDetails({ contact, loading: true })
    try {
      const result = contact.contactType === 1 ? await api.groupInfo(contact.contactId) : await api.search(contact.contactId)
      const detail = contact.contactType === 1 ? result : result?.userInfoList?.[0]
      setContactDetails({ contact: detail ? { ...contact, ...detail } : contact, loading: false })
    } catch {
      setContactDetails({ contact, loading: false })
    }
  }
  const closeContactDetails = () => setContactDetails(null)
  const chatFromDetails = () => { const item = contactDetails?.contact; closeContactDetails(); if (item) selectChat(item) }
  const send = () => { const content = composer.trim(); if (!content || !active || !active.sessionId || !socketRef.current || socketRef.current.readyState !== WebSocket.OPEN) { if (!active?.sessionId) setNotice('该联系人还没有可用会话'); else if (!socketRef.current || socketRef.current.readyState !== WebSocket.OPEN) setNotice('消息通道未连接，暂时无法发送'); return } const packet = { clientMessageId: globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`, sessionId: active.sessionId, contactId: active.contactId, messageContent: content, messageType: 2, contactType: active.contactType ?? 0 }; try { socketRef.current.send(JSON.stringify(packet)); pendingMessagesRef.current.set(packet.clientMessageId, { content, sessionId: active.sessionId }); setComposer('') } catch (e) { setNotice('消息发送失败，请重试') } }
  const doSearch = async e => { if (e.key !== 'Enter' || !search.trim()) return; try { setSearchResult(await api.search(search.trim())) } catch (err) { setNotice(err.message) } }
  const doApply = async (id, type = 0) => { try { const pending = await api.applyAdd(id, type, '你好，很高兴认识你'); setNotice(pending ? (type === 1 ? '入群申请已发送' : '好友申请已发送') : '已加入群聊'); setSearchResult(null); if (!pending) await load(user) } catch (e) { setNotice(e.message) } }
  const handleApplyAction = async (item, status) => { setProcessingApply(`${item.applyId}-${status}`); try { await api.solveApply(item.applyId, status); setApplications(old => old.filter(x => x.applyId !== item.applyId)); if (status === 1) await load(user); const isGroup = item.contactType === 1; setNotice(status === 1 ? (isGroup ? '已批准入群申请' : '已添加为好友') : status === 2 ? '已拒绝申请' : '已加入黑名单') } catch (e) { setNotice(e.message) } finally { setProcessingApply(null) } }
  const logout = () => { localStorage.removeItem('easychat-token'); socketRef.current?.close(); setUser(null) }
  if (loading) return <div className="loading-screen"><div className="brand-mark">E</div><span>正在打开 EasyChat...</span></div>
  if (!user) return <Auth onLogin={data => load(data)} />
  const list = section === 'chats' ? sessions : section === 'contacts' ? contacts : groups
  return (
    <div className="app-shell">
      {notice && <div className="toast">{notice}<button onClick={() => setNotice('')}><X size={14} /></button></div>}
      <aside className="rail">
        <Avatar name={user.nickName} tone="dark" />
        <div className="rail-nav">
          <button className={section === 'chats' && !showApplications ? 'active' : ''} onClick={() => { setSection('chats'); setShowApplications(false) }} title="聊天"><MessageCircle /></button>
          <button className={section === 'contacts' && !showApplications ? 'active' : ''} onClick={() => { setSection('contacts'); setShowApplications(false) }} title="通讯录"><Users /></button>
          <button className={section === 'groups' && !showApplications ? 'active' : ''} onClick={() => { setSection('groups'); setShowApplications(false) }} title="群组"><Compass /></button>
        </div>
        <div className="rail-bottom"><button title="设置"><Settings /></button><button title="退出" onClick={logout}><LogOut /></button></div>
      </aside>
      <aside className="sidebar">
        <div className="sidebar-top">
          <div><span className="kicker">EASYCHAT</span><h2>{showApplications ? '好友与群聊申请' : section === 'chats' ? '聊天' : section === 'contacts' ? '通讯录' : '我的群组'}</h2></div>
          <button className="circle-btn" title="创建群聊" onClick={() => { setShowApplications(false); setShowCreateGroup(true) }}><Plus size={18} /></button>
        </div>
        <div className="search-box"><Search size={16} /><input value={search} onChange={e => { setSearch(e.target.value); setSearchResult(null) }} onKeyDown={doSearch} placeholder="搜索联系人或群组" /></div>
        {searchResult && <div className="search-result">
          <div className="result-heading">搜索结果</div>
          {searchResult.userInfoList?.map(item => <div className="result-row" key={item.userId}><Avatar name={item.nickName} small /><div><strong>{item.nickName}</strong><span>{item.email || item.userId}</span></div><button onClick={() => doApply(item.userId)}>添加</button></div>)}
          {searchResult.groupInfo && <div className="result-row"><Avatar name={searchResult.groupInfo.groupName} small tone="orange" /><div><strong>{searchResult.groupInfo.groupName}</strong><span>{searchResult.groupInfo.joinType === 0 ? '可直接加入' : '需要群主审批'}</span></div><button onClick={() => doApply(searchResult.groupInfo.groupId, 1)}>{searchResult.groupInfo.joinType === 0 ? '加入' : '申请'}</button></div>}
        </div>}
        <div className="list-head"><span>{section === 'chats' ? '最近消息' : '全部'}</span><span className="count">{list.length}</span></div>
        <div className="list-scroll">
          {list.map((item, index) => {
            const name = item.contactName || item.nickName || item.groupName || `联系人 ${index + 1}`
            const contact = { ...item, name, contactName: name, contactId: item.contactId || item.groupId, contactType: item.contactType ?? (section === 'groups' ? 1 : 0) }
            return <button className={`list-item ${active?.contactId === contact.contactId ? 'selected' : ''}`} key={item.sessionId || contact.contactId || index} onClick={() => selectChat(contact)}>
              <Avatar name={name} tone={contact.contactType === 1 ? 'orange' : index % 3 === 1 ? 'blue' : 'green'} onClick={event => { event.stopPropagation(); openContactDetails(contact) }} title="查看联系人详情" />
              <span className="item-copy"><strong>{name}</strong><small>{item.lastMessage || (section === 'contacts' ? '点击开始聊天' : '暂无消息')}</small></span>
              {item.lastReceiveTime && <time>{timeText(item.lastReceiveTime)}</time>}
            </button>
          })}
          {!list.length && <div className="empty-list"><div className="empty-icon">{section === 'chats' ? '◎' : '＋'}</div><p>{section === 'chats' ? '还没有聊天' : '这里还没有内容'}</p><small>搜索联系人，开始一段新的对话</small></div>}
        </div>
        {applications.length > 0 && <button className="apply-banner" onClick={() => { setShowApplications(true); setActive(null) }}><span><UserPlus size={16} />待处理申请</span><b>{applications.length}</b></button>}
      </aside>
      <main className="chat-pane">
        {showApplications ? <ApplicationsPanel applications={applications} processing={processingApply} onAction={handleApplyAction} onClose={() => setShowApplications(false)} /> : active ? <>
          <header className="chat-header"><button className="mobile-back" onClick={() => setActive(null)}><ChevronLeft /></button><div className="chat-peer"><Avatar name={activeTitle} tone={active.contactType === 1 ? 'orange' : 'blue'} onClick={() => openContactDetails(active)} title="查看联系人详情" /><div><h3>{activeTitle}</h3><span><i className={socketState === 'open' ? 'online' : ''}></i>{socketState === 'open' ? '在线' : '连接中'}</span></div></div><button className="icon-btn" title="更多"><MoreHorizontal /></button></header>
          <div className="message-scroll">{currentMessages.length ? currentMessages.map((msg, index) => [3, 8, 9, 10, 11, 12].includes(msg.messageType)
            ? <div className="group-event" key={msg.messageId || `${msg.sendTime}-${index}`}>{msg.messageContent}</div>
            : <div className={`message-row ${msg.sendUserId === user.userId ? 'mine' : ''}`} key={msg.messageId || `${msg.sendTime}-${index}`}><Avatar name={msg.sendUserNickName || activeTitle} small tone={msg.sendUserId === user.userId ? 'green' : 'blue'} onClick={msg.sendUserId !== user.userId ? () => openContactDetails({ ...active, contactId: msg.sendUserId, contactName: msg.sendUserNickName || activeTitle, nickName: msg.sendUserNickName || activeTitle }) : undefined} title={msg.sendUserId !== user.userId ? '查看联系人详情' : undefined} /><div><span className="message-author">{msg.sendUserId === user.userId ? '我' : msg.sendUserNickName || activeTitle}</span><div className="bubble">{msg.messageContent}</div><time>{timeText(msg.sendTime)}</time></div></div>
          ) : <div className="chat-empty"><div className="empty-icon">✦</div><h3>开始聊天</h3><p>发送一条消息，和 {activeTitle} 打个招呼吧</p></div>}</div>
          <footer className="composer"><div className="composer-tools"><button title="表情"><Smile /></button><button title="附件"><Paperclip /></button><button title="语音"><Mic /></button></div><textarea value={composer} onChange={e => setComposer(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }} placeholder="输入消息，按 Enter 发送" /><button className="send-btn" onClick={send}><Send size={17} /></button></footer>
        </> : <div className="welcome"><div className="welcome-orbit"><MessageCircle size={30} /></div><h1>让每次相遇，都有回应</h1><p>从左侧选择一个聊天，开始你的 EasyChat 时光</p><span className="welcome-status"><i className={socketState === 'open' ? 'online' : ''}></i>{socketState === 'open' ? '实时连接已建立' : '等待实时连接'}</span></div>}
      </main>
      {contactDetails && <ContactDetails contact={contactDetails.contact} loading={contactDetails.loading} onClose={closeContactDetails} onChat={chatFromDetails} />}
      {showCreateGroup && <CreateGroupDialog onClose={() => setShowCreateGroup(false)} onCreated={async () => { setSection('groups'); await load(user); setNotice('群聊已创建') }} />}
    </div>
  )
}

createRoot(document.getElementById('root')).render(<App />)
