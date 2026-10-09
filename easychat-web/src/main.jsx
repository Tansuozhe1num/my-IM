import React, { useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { api, connectSocket } from './api'
import { Search, Plus, MessageCircle, Users, Compass, Settings, Smile, Paperclip, Mic, Send, UserPlus, MoreHorizontal, LogOut, RefreshCw, X, Check, ShieldOff, ChevronLeft, Mail, MapPin, CalendarDays } from 'lucide-react'
import './styles.css'

const initials = value => (value || '?').trim().slice(0, 1).toUpperCase()
const timeText = value => value ? new Date(Number(value) || value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
const sessionTime = value => Number(value) || Date.parse(value) || 0
const normalizeId = value => value == null ? value : String(value)
const normalizeSession = value => value && ({ ...value, sessionId: normalizeId(value.sessionId), contactId: normalizeId(value.contactId) })
const normalizeMessage = value => {
  if (!value || typeof value !== 'object') return value
  let extendData = value.extendData
  if (typeof extendData === 'string') {
    try { extendData = JSON.parse(extendData) } catch { /* preserve non-JSON payloads */ }
  }
  return {
    ...value,
    messageType: value.messageType == null ? value.messageType : Number(value.messageType),
    sessionId: normalizeId(value.sessionId),
    contactId: normalizeId(value.contactId),
    extendData: extendData && typeof extendData === 'object' ? normalizeSession(extendData) : extendData,
  }
}
const messageKey = value => value?.messageId != null
  ? `id:${value.messageId}`
  : value?.clientMessageId
    ? `client:${value.clientMessageId}`
    : `fallback:${value?.sessionId || ''}:${value?.sendUserId || ''}:${value?.sendTime || ''}:${value?.messageContent || ''}`
const mergeMessages = (...lists) => {
  const merged = new Map()
  lists.flat().filter(Boolean).forEach(item => {
    const message = normalizeMessage(item)
    if (message) merged.set(messageKey(message), message)
  })
  return [...merged.values()].sort((a, b) => sessionTime(a.sendTime) - sessionTime(b.sendTime))
}

function Auth({ onLogin }) {
  const [mode, setMode] = useState('login'); const [code, setCode] = useState(null); const [form, setForm] = useState({ email: '', nickname: '', password: '', checkCode: '' }); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const refresh = async () => { try { setCode(await api.checkCode()) } catch (e) { setError(e.message) } }
  useEffect(() => { refresh() }, [])
  const submit = async e => { e.preventDefault(); if (!code?.checkCodeKey) { setError('验证码尚未加载，请刷新后重试'); await refresh(); return } setBusy(true); setError(''); try { const data = await (mode === 'login' ? api.login({ ...form, checkcodekey: code.checkCodeKey }) : api.register({ ...form, checkcodekey: code.checkCodeKey })); if (mode === 'login') { localStorage.setItem('easychat-token', data.token); onLogin(data) } else { setMode('login'); setForm(f => ({ ...f, password: '', checkCode: '' })); await refresh() } } catch (err) { setError(err.message); await refresh() } finally { setBusy(false) } }
  return <main className="auth-shell"><div className="auth-card"><div className="brand"><div className="brand-mark">E</div><div><strong>EasyChat</strong><span>连接每一刻</span></div></div><div className="auth-copy"><p className="eyebrow">WECHAT INSPIRED IM</p><h1>{mode === 'login' ? '欢迎回来' : '创建账号'}</h1><p>{mode === 'login' ? '登录 EasyChat，继续你的对话。' : '用一个昵称开始你的聊天空间。'}</p></div><form onSubmit={submit} className="auth-form">{mode === 'register' && <input required placeholder="昵称" value={form.nickname} onChange={e => setForm({ ...form, nickname: e.target.value })} />}<input required type="email" placeholder="邮箱" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /><input required type="password" minLength="6" placeholder="密码" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /><div className="code-row"><input required placeholder="验证码结果" value={form.checkCode} onChange={e => setForm({ ...form, checkCode: e.target.value })} />{code?.checkCode && <button type="button" className="captcha" onClick={refresh}><img src={code.checkCode} alt="验证码" /></button>}<button type="button" className="icon-btn subtle" title="刷新验证码" onClick={refresh}><RefreshCw size={16} /></button></div>{error && <div className="error">{error}</div>}<button className="primary-btn" disabled={busy}>{busy ? '处理中...' : mode === 'login' ? '登录' : '注册'}</button></form><button className="text-btn" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); refresh() }}>{mode === 'login' ? '没有账号？立即注册' : '已有账号？返回登录'}</button></div><div className="auth-note">API: {import.meta.env.VITE_API_BASE || 'http://localhost:5050/api'}</div></main>
}

function Avatar({ name, id, version, tone = 'green', small = false, onClick, title }) {
  const [failed, setFailed] = useState(false)
  const src = id ? api.avatarUrl(id, version) : null
  useEffect(() => setFailed(false), [src])
  return <div className={'avatar ' + tone + (small ? ' small' : '') + (onClick ? ' clickable' : '')} onClick={onClick} title={title}>
    {src && !failed ? <img src={src} alt="" onError={() => setFailed(true)} /> : initials(name)}
  </div>
}
const asList = value => Array.isArray(value) ? value : []

function ContactDetails({ contact, loading, userId, onClose, onChat, onEdit }) {
  const isGroup = contact?.contactType === 1 || contact?.contactId?.startsWith('G')
  const name = isGroup
    ? contact?.groupName || contact?.contactName || contact?.name || '群聊'
    : contact?.nickName || contact?.contactName || contact?.name || '联系人'
  const sex = contact?.sex === 1 ? '男' : contact?.sex === 0 ? '女' : ''
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <section className="contact-details" role="dialog" aria-modal="true" aria-label={`${name}的联系人详情`} onMouseDown={e => e.stopPropagation()}>
      <button className="icon-btn details-close" onClick={onClose} title="关闭"><X size={18} /></button>
      <div className="details-hero"><Avatar name={name} id={contact?.userId || contact?.contactId} version={contact?.avatarVersion} tone="blue" /><div><h2>{name}</h2><span>{contact?.userId || contact?.contactId || '联系人'}</span></div></div>
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
      <div className="details-actions">{isGroup && contact?.groupOwnerId === userId && <button className="cancel-btn" onClick={onEdit}>编辑群资料</button>}<button className="primary-btn" onClick={onChat}><MessageCircle size={16} />发消息</button></div>
    </section>
  </div>
}

function ApplicationsPanel({ applications, processing, onAction, onClose }) {
  return <section className="applications-panel"><header className="applications-header"><div><span className="kicker">CONTACT REQUESTS</span><h2>好友与群聊申请</h2><p>处理新的联系人请求</p></div><button className="icon-btn" onClick={onClose} title="关闭"><X size={18} /></button></header><div className="applications-list">{applications.map(item => { const isGroup = Number(item.contactType) === 1; return <article className="application-card" key={item.applyId}><Avatar name={item.applyUserId} id={item.applyUserId} tone="blue" /><div className="application-content"><div className="application-title"><strong>{item.applyUserId}</strong><time>{item.lastApplyTime ? new Date(item.lastApplyTime).toLocaleString('zh-CN') : ''}</time></div><p>{item.applyInfo || (isGroup ? `申请加入群组 ${item.contactId}` : '请求添加你为好友')}</p><div className="application-actions"><button className="apply-accept" disabled={processing === `${item.applyId}-1`} onClick={() => onAction(item, 1)}><Check size={15} />{isGroup ? '批准入群' : '接受'}</button><button className="apply-reject" disabled={processing === `${item.applyId}-2`} onClick={() => onAction(item, 2)}><X size={15} />拒绝</button>{!isGroup && <button className="apply-block" disabled={processing === `${item.applyId}-3`} onClick={() => onAction(item, 3)}><ShieldOff size={15} />拉黑</button>}</div></div></article>})}{!applications.length && <div className="applications-empty"><div className="empty-icon">✓</div><h3>没有待处理申请</h3><p>新的好友和群聊申请会显示在这里</p></div>}</div></section>
}

function GroupDialog({ group, onClose, onSaved }) {
  const editing = Boolean(group?.groupId)
  const [form, setForm] = useState(() => ({ groupId: group?.groupId || null, groupName: group?.groupName || '', groupNotice: group?.groupNotice || '', joinType: String(group?.joinType ?? 1), avaterfile: null }))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async event => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api.saveGroup(form)
      await onSaved(form)
      onClose()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <section className="create-group" role="dialog" aria-modal="true" aria-label={editing ? '编辑群资料' : '创建群聊'} onMouseDown={e => e.stopPropagation()}>
      <header><div><span className="kicker">{editing ? 'GROUP SETTINGS' : 'NEW GROUP'}</span><h2>{editing ? '编辑群资料' : '创建群聊'}</h2></div><button className="icon-btn" onClick={onClose} title="关闭"><X size={18} /></button></header>
      <form onSubmit={submit}>
        <label>群名称<input required maxLength={40} value={form.groupName} onChange={e => setForm({ ...form, groupName: e.target.value })} placeholder="给群聊取个名字" /></label>
        <label>群公告<textarea maxLength={300} value={form.groupNotice} onChange={e => setForm({ ...form, groupNotice: e.target.value })} placeholder="介绍一下这个群" /></label>
        <fieldset><legend>加入方式</legend><label><input type="radio" name="joinType" value="1" checked={form.joinType === '1'} onChange={e => setForm({ ...form, joinType: e.target.value })} />需要群主审批</label><label><input type="radio" name="joinType" value="0" checked={form.joinType === '0'} onChange={e => setForm({ ...form, joinType: e.target.value })} />允许直接加入</label></fieldset>
        <label className="group-avatar-upload">群头像<input type="file" accept="image/png" onChange={e => setForm({ ...form, avaterfile: e.target.files?.[0] || null })} /><span>{form.avaterfile?.name || '选择 PNG 图片（可选）'}</span></label>
        {error && <div className="error">{error}</div>}
        <footer><button type="button" className="cancel-btn" onClick={onClose}>取消</button><button className="primary-btn" disabled={busy}>{busy ? '保存中...' : editing ? '保存资料' : '创建群聊'}</button></footer>
      </form>
    </section>
  </div>
}

function ProfileDialog({ user, onClose, onSaved }) {
  const [form, setForm] = useState({ nickName: user?.nickName || '', personalSignature: user?.personalSignature || '', sex: user?.sex == null ? '' : String(user.sex), areaName: user?.areaName || '', areaCode: user?.areaCode || '', avator: null })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async event => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const values = { ...form, sex: form.sex === '' ? null : form.sex, avator: form.avator }
      await api.updateUserInfo(values)
      await onSaved(values)
      onClose()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}>
    <section className="create-group" role="dialog" aria-modal="true" aria-label="编辑个人资料" onMouseDown={e => e.stopPropagation()}>
      <header><div><span className="kicker">PROFILE</span><h2>编辑个人资料</h2></div><button className="icon-btn" onClick={onClose} title="关闭"><X size={18} /></button></header>
      <form onSubmit={submit}>
        <label>昵称<input required maxLength={40} value={form.nickName} onChange={e => setForm({ ...form, nickName: e.target.value })} /></label>
        <label>个性签名<textarea maxLength={200} value={form.personalSignature} onChange={e => setForm({ ...form, personalSignature: e.target.value })} placeholder="介绍一下自己" /></label>
        <label>性别<select value={form.sex} onChange={e => setForm({ ...form, sex: e.target.value })}><option value="">保持不变</option><option value="0">女</option><option value="1">男</option></select></label>
        <div className="profile-area"><label>地区<input maxLength={80} value={form.areaName} onChange={e => setForm({ ...form, areaName: e.target.value })} placeholder="例如：上海" /></label><label>地区编号<input maxLength={20} value={form.areaCode} onChange={e => setForm({ ...form, areaCode: e.target.value })} placeholder="可选" /></label></div>
        <label className="group-avatar-upload">头像<input type="file" accept="image/png" onChange={e => setForm({ ...form, avator: e.target.files?.[0] || null })} /><span>{form.avator?.name || '选择 PNG 图片（可选）'}</span></label>
        {error && <div className="error">{error}</div>}
        <footer><button type="button" className="cancel-btn" onClick={onClose}>取消</button><button className="primary-btn" disabled={busy}>{busy ? '保存中...' : '保存资料'}</button></footer>
      </form>
    </section>
  </div>
}

function App() {
  const [user, setUser] = useState(null); const [loading, setLoading] = useState(!!localStorage.getItem('easychat-token')); const [section, setSection] = useState('chats'); const [contacts, setContacts] = useState([]); const [groups, setGroups] = useState([]); const [applications, setApplications] = useState([]); const [sessions, setSessions] = useState([]); const [active, setActive] = useState(null); const [showApplications, setShowApplications] = useState(false); const [showCreateGroup, setShowCreateGroup] = useState(false); const [showProfile, setShowProfile] = useState(false); const [editingGroup, setEditingGroup] = useState(null); const [processingApply, setProcessingApply] = useState(null); const [messages, setMessages] = useState([]); const [search, setSearch] = useState(''); const [searchResult, setSearchResult] = useState(null); const [composer, setComposer] = useState(''); const [notice, setNotice] = useState(''); const [socketState, setSocketState] = useState('offline'); const [contactDetails, setContactDetails] = useState(null); const socketRef = useRef(null); const selectionRef = useRef(''); const pendingMessagesRef = useRef(new Map()); const activeRef = useRef(active); activeRef.current = active
  const applicationRevisionRef = useRef(0)
  const dataRevisionRef = useRef(0)
  const loadRevisionRef = useRef(0)
  const load = async current => {
    const loadRevision = ++loadRevisionRef.current
    const me = current || await api.me()
    setUser(me)
    const applicationRevision = applicationRevisionRef.current
    const dataRevision = dataRevisionRef.current
    const results = await Promise.allSettled([api.contacts(0), api.groups(), api.applications(), api.sessions(me.userId)])
    if (loadRevision !== loadRevisionRef.current) return
    const [friendData, groupData, applyData, sessionData] = results
    const friendList = friendData.status === 'fulfilled' ? asList(friendData.value?.friendUserInfo) : []
    const groupList = groupData.status === 'fulfilled'
      ? asList(groupData.value).map(group => ({ ...group, contactId: group.groupId, contactName: group.groupName, contactType: 1 }))
      : []
    if (dataRevision === dataRevisionRef.current) {
      setContacts(old => friendList.map(item => ({ ...item, avatarVersion: item.avatarVersion || old.find(previous => previous.contactId === item.contactId)?.avatarVersion })))
      setGroups(old => groupList.map(item => ({ ...item, avatarVersion: item.avatarVersion || old.find(previous => (previous.groupId || previous.contactId) === item.contactId)?.avatarVersion })))
      const sessionResult = sessionData.status === 'fulfilled' ? sessionData.value : null
      setSessions(asList(sessionResult?.list || sessionResult).map(session => {
        const normalized = normalizeSession(session)
        return normalized.contactId?.startsWith('G') ? { ...normalized, contactType: 1 } : normalized
      }))
    }
    if (applicationRevision === applicationRevisionRef.current) {
      setApplications(applyData.status === 'fulfilled' ? asList(applyData.value) : [])
    }
    const failed = results.map((result, index) => result.status === 'rejected' ? ['联系人', '群组', '好友申请', '会话'][index] : null).filter(Boolean)
    if (failed.length) setNotice(`部分数据加载失败：${failed.join('、')}。请检查后端接口和浏览器控制台。`)
    setLoading(false)
  }
  const refreshApplications = async (revision, optimistic) => {
    const requestRevision = revision ?? ++applicationRevisionRef.current
    try {
      const data = await api.applications()
      if (requestRevision === applicationRevisionRef.current) {
        const latest = asList(data)
        if (!optimistic || Number(optimistic.status) !== 0) {
          setApplications(latest)
        } else {
          const optimisticKey = String(optimistic.applyId ?? `${optimistic.applyUserId || ''}:${optimistic.contactId || ''}:${optimistic.contactType ?? ''}`)
          setApplications(old => {
            const merged = latest.some(item => String(item?.applyId ?? `${item?.applyUserId || ''}:${item?.contactId || ''}:${item?.contactType ?? ''}`) === optimisticKey)
              ? latest
              : [optimistic, ...latest]
            return merged.sort((a, b) => Number(b.lastApplyTime || 0) - Number(a.lastApplyTime || 0))
          })
        }
      }
    } catch {
      if (requestRevision === applicationRevisionRef.current) setNotice('申请已收到，但列表同步失败，请稍后重试')
    }
  }
  useEffect(() => { if (localStorage.getItem('easychat-token')) load().catch(e => { localStorage.removeItem('easychat-token'); setLoading(false); setNotice(e.message) }) }, [])
  useEffect(() => {
    if (!user) return
    let connected = false
    const ws = connectSocket(localStorage.getItem('easychat-token'), message => {
      const init = message?.extendData || message
      if (Number(message?.messageType) === 0 && init?.chatMessages) {
        setMessages(old => mergeMessages(old, asList(init.chatMessages)))
        if (init.chatSessionUsers) setSessions(asList(init.chatSessionUsers).map(session => {
          const normalized = normalizeSession(session)
          return normalized.contactId?.startsWith('G') ? { ...normalized, contactType: 1 } : normalized
        }))
        return
      }
      message = normalizeMessage(message)
      if (Number(message?.messageType) === 10) {
        ++dataRevisionRef.current
        const detail = message.extendData && typeof message.extendData === 'object' ? message.extendData : {}
        const entityId = detail.groupId || detail.userId || message.sendUserId || message.contactId
        const isGroup = message.contactType === 1 || detail.groupId?.startsWith('G') || entityId?.startsWith('G')
        const name = detail.groupName || detail.nickName || (typeof message.extendData === 'string' ? message.extendData : '') || message.sendUserNickName
        if (isGroup) {
          const changes = { ...detail, ...(name ? { groupName: name, contactName: name } : {}) }
          setGroups(old => old.map(item => (item.groupId || item.contactId) === entityId ? { ...item, ...changes } : item))
          setSessions(old => old.map(item => item.contactId === entityId ? { ...item, ...changes, contactName: name || item.contactName } : item))
          setActive(old => old?.contactId === entityId ? { ...old, ...changes, avatarVersion: changes.avatarVersion || old.avatarVersion, contactName: name || old.contactName } : old)
          setContactDetails(old => old?.contact?.contactId === entityId ? { ...old, contact: { ...old.contact, ...changes, avatarVersion: changes.avatarVersion || old.contact.avatarVersion } } : old)
        } else if (entityId) {
          setContacts(old => old.map(item => item.contactId === entityId ? { ...item, ...detail, avatarVersion: detail.avatarVersion || item.avatarVersion, ...(name ? { nickName: name } : {}) } : item))
          setSessions(old => old.map(item => item.contactId === entityId ? { ...item, ...detail, avatarVersion: detail.avatarVersion || item.avatarVersion, contactName: name || item.contactName } : item))
          setActive(old => old?.contactId === entityId ? { ...old, ...detail, avatarVersion: detail.avatarVersion || old.avatarVersion, contactName: name || old.contactName } : old)
          setContactDetails(old => old?.contact?.contactId === entityId ? { ...old, contact: { ...old.contact, ...detail, avatarVersion: detail.avatarVersion || old.contact.avatarVersion, ...(name ? { nickName: name } : {}) } } : old)
          if (entityId === user.userId) setUser(old => ({ ...old, ...detail, nickName: name || old.nickName }))
        }
        return
      }
      if (Number(message?.messageType) === 4) {
        let application = message.extendData
        if (typeof application === 'string') {
          try { application = JSON.parse(application) } catch { application = null }
        }
        if (!application || typeof application !== 'object') {
          const revision = ++applicationRevisionRef.current
          setActive(null)
          setSection('contacts')
          setShowApplications(true)
          refreshApplications(revision)
          return
        }
        const status = Number(application.status)
        const contactType = Number(application.contactType)
        const isOwnRequest = application.applyUserId === user.userId
        const isReceiver = application.receiveUserId === user.userId || message.contactId === user.userId
        const keyOf = item => String(item?.applyId ?? ((item?.applyUserId || '') + ':' + (item?.contactId || '') + ':' + (item?.contactType ?? '')))
        const applicationKey = keyOf(application)
        if (status === 0 && isReceiver && !isOwnRequest) {
          const revision = ++applicationRevisionRef.current
          setApplications(old => [application, ...old.filter(item => keyOf(item) !== applicationKey)]
            .sort((a, b) => Number(b.lastApplyTime || 0) - Number(a.lastApplyTime || 0)))
          setSection('contacts')
          setShowApplications(true)
          setActive(null)
          setSearchResult(null)
          setNotice('收到' + (contactType === 1 ? '入群' : '好友') + '申请')
          refreshApplications(revision, application)
        } else if (status === 0 && isOwnRequest) {
          setNotice('申请已发送，等待' + (contactType === 1 ? '群主' : '对方') + '处理')
        } else if ([1, 2, 3].includes(status)) {
          ++applicationRevisionRef.current
          setApplications(old => old.filter(item => keyOf(item) !== applicationKey))
          if (status === 1) ++dataRevisionRef.current
          if (isOwnRequest) {
            const resultText = status === 1 ? (contactType === 1 ? '入群' : '好友') + '申请已通过'
              : status === 2 ? (contactType === 1 ? '入群' : '好友') + '申请已拒绝'
                : '申请已处理'
            setNotice(resultText)
            if (status === 1) load(user).catch(() => setNotice(resultText))
          } else if (isReceiver) {
            setSection('contacts')
            setShowApplications(true)
            setActive(null)
            setNotice(status === 1 ? '申请已通过' : status === 2 ? '申请已拒绝' : '申请已处理')
            if (status === 1) load(user).catch(() => setNotice('申请已通过，但联系人同步失败，请稍后重试'))
          }
        } else {
          refreshApplications()
        }
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

      if (message.messageType === 9 && message.contactId?.startsWith('G')) {
        const memberSession = message.extendData
        const memberCount = memberSession?.memberCount
        const isCurrentMember = memberSession?.userId === user.userId
        setMessages(old => mergeMessages(old, [message]))
        setGroups(old => {
          const found = old.some(item => (item.groupId || item.contactId) === message.contactId)
          const updated = old.map(item => (item.groupId || item.contactId) === message.contactId
            ? { ...item, memberCount: memberCount ?? item.memberCount }
            : item)
          return !found && isCurrentMember
            ? [...updated, { groupId: message.contactId, contactId: message.contactId, groupName: memberSession.contactName, contactName: memberSession.contactName, contactType: 1, memberCount }]
            : updated
        })
        setSessions(old => {
          const updated = old.map(item => item.sessionId === message.sessionId
            ? { ...item, memberCount: memberCount ?? item.memberCount, lastMessage: message.messageContent, lastReceiveTime: message.sendTime }
            : item)
          return !old.some(item => item.sessionId === message.sessionId) && isCurrentMember
            ? [{ ...memberSession, contactType: 1, lastMessage: message.messageContent, lastReceiveTime: message.sendTime }, ...updated]
            : updated
        })
        setActive(old => old?.contactId === message.contactId ? { ...old, memberCount: memberCount ?? old.memberCount } : old)
        return
      }
      ++dataRevisionRef.current
      setMessages(old => mergeMessages(old, [message]))
      const sessionData = message.extendData?.sessionId ? {
        ...message.extendData,
        contactType: message.extendData.contactId?.startsWith('G') ? 1 : 0,
        lastMessage: message.messageContent,
        lastReceiveTime: message.sendTime || message.extendData.lastReceiveTime,
      } : null
      if (sessionData?.contactId) {
        if (sessionData.contactType === 1) {
          setGroups(old => old.some(item => item.contactId === sessionData.contactId)
            ? old.map(item => item.contactId === sessionData.contactId ? { ...item, ...sessionData, groupName: sessionData.contactName || item.groupName, contactName: sessionData.contactName || item.contactName } : item)
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
    }, state => {
      setSocketState(state)
      if (state === 'open') {
        if (connected) load().catch(() => setNotice('实时连接已恢复，但数据同步失败，请稍后重试'))
        connected = true
      }
    })
    socketRef.current = ws
    const syncWhenVisible = () => {
      if (connected && document.visibilityState === 'visible') load().catch(() => setNotice('页面恢复后数据同步失败，请稍后重试'))
    }
    document.addEventListener('visibilitychange', syncWhenVisible)
    return () => {
      document.removeEventListener('visibilitychange', syncWhenVisible)
      ws.close()
    }
  }, [user?.userId])
  const activeTitle = active?.contactName || active?.name || '选择一个聊天'; const currentMessages = active?.sessionId ? messages.filter(m => m.sessionId === active.sessionId) : []
  const contactKey = item => `${item?.contactType ?? 0}:${item?.contactId || item?.groupId || item?.sessionId || item?.name || ''}`
  const selectChat = async item => {
    const session = item.sessionId ? item : sessions.find(candidate => candidate.contactId === item.contactId && (candidate.contactType ?? 0) === (item.contactType ?? 0))
    const selected = normalizeSession({ ...item, ...(session || {}), name: item.name || session?.contactName, contactName: item.contactName || session?.contactName || item.name })
    const key = contactKey(selected)
    selectionRef.current = key
    setActive(selected)
    setMessages([])
    setComposer('')
    if (!selected.sessionId) return
    try {
      const data = await api.messages(selected.sessionId)
      if (selectionRef.current === key) {
        const history = asList(data?.list || data).filter(message => normalizeId(message.sessionId) === selected.sessionId)
        setMessages(old => mergeMessages(old.filter(message => message.sessionId !== selected.sessionId), history))
      }
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
  const handleApplyAction = async (item, status) => {
    setProcessingApply(`${item.applyId}-${status}`)
    try {
      await api.solveApply(item.applyId, status)
      ++applicationRevisionRef.current
      setApplications(old => old.filter(application => String(application.applyId) !== String(item.applyId)))
      if (status === 1) {
        ++dataRevisionRef.current
        await load(user)
      }
      const isGroup = Number(item.contactType) === 1
      setNotice(status === 1 ? (isGroup ? '已批准入群申请' : '已添加为好友') : status === 2 ? '已拒绝申请' : '已加入黑名单')
    } catch (e) {
      setNotice(e.message)
    } finally {
      setProcessingApply(null)
    }
  }
  const logout = () => { localStorage.removeItem('easychat-token'); socketRef.current?.close(); setUser(null) }
  if (loading) return <div className="loading-screen"><div className="brand-mark">E</div><span>正在打开 EasyChat...</span></div>
  if (!user) return <Auth onLogin={data => load(data)} />
  const list = section === 'chats' ? sessions : section === 'contacts' ? contacts : groups
  return (
    <div className="app-shell">
      {notice && <div className="toast">{notice}<button onClick={() => setNotice('')}><X size={14} /></button></div>}
      <aside className="rail">
        <Avatar name={user.nickName} id={user.userId} version={user.avatarVersion} tone="dark" />
        <div className="rail-nav">
          <button className={section === 'chats' && !showApplications ? 'active' : ''} onClick={() => { setSection('chats'); setShowApplications(false) }} title="聊天"><MessageCircle /></button>
          <button className={section === 'contacts' && !showApplications ? 'active' : ''} onClick={() => { setSection('contacts'); setShowApplications(false) }} title="通讯录"><Users /></button>
          <button className={section === 'groups' && !showApplications ? 'active' : ''} onClick={() => { setSection('groups'); setShowApplications(false) }} title="群组"><Compass /></button>
        </div>
        <div className="rail-bottom"><button title="个人资料" onClick={() => setShowProfile(true)}><Settings /></button><button title="退出" onClick={logout}><LogOut /></button></div>
      </aside>
      <aside className="sidebar">
        <div className="sidebar-top">
          <div><span className="kicker">EASYCHAT</span><h2>{showApplications ? '好友与群聊申请' : section === 'chats' ? '聊天' : section === 'contacts' ? '通讯录' : '我的群组'}</h2></div>
          <button className="circle-btn" title="创建群聊" onClick={() => { setShowApplications(false); setShowCreateGroup(true) }}><Plus size={18} /></button>
        </div>
        <div className="search-box"><Search size={16} /><input value={search} onChange={e => { setSearch(e.target.value); setSearchResult(null) }} onKeyDown={doSearch} placeholder="搜索联系人或群组" /></div>
        {searchResult && <div className="search-result">
          <div className="result-heading">搜索结果</div>
          {searchResult.userInfoList?.map(item => <div className="result-row" key={item.userId}><Avatar name={item.nickName} id={item.userId} small /><div><strong>{item.nickName}</strong><span>{item.email || item.userId}</span></div><button onClick={() => doApply(item.userId)}>添加</button></div>)}
          {searchResult.groupInfo && <div className="result-row"><Avatar name={searchResult.groupInfo.groupName} id={searchResult.groupInfo.groupId} small tone="orange" /><div><strong>{searchResult.groupInfo.groupName}</strong><span>{searchResult.groupInfo.joinType === 0 ? '可直接加入' : '需要群主审批'}</span></div><button onClick={() => doApply(searchResult.groupInfo.groupId, 1)}>{searchResult.groupInfo.joinType === 0 ? '加入' : '申请'}</button></div>}
        </div>}
        <div className="list-head"><span>{section === 'chats' ? '最近消息' : '全部'}</span><span className="count">{list.length}</span></div>
        <div className="list-scroll">
          {list.map((item, index) => {
            const name = item.contactName || item.nickName || item.groupName || `联系人 ${index + 1}`
            const contact = { ...item, name, contactName: name, contactId: item.contactId || item.groupId, contactType: item.contactType ?? (section === 'groups' ? 1 : 0) }
            return <button className={`list-item ${active?.contactId === contact.contactId ? 'selected' : ''}`} key={item.sessionId || contact.contactId || index} onClick={() => selectChat(contact)}>
              <Avatar name={name} id={contact.contactId} version={item.avatarVersion} tone={contact.contactType === 1 ? 'orange' : index % 3 === 1 ? 'blue' : 'green'} onClick={event => { event.stopPropagation(); openContactDetails(contact) }} title="查看联系人详情" />
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
          <header className="chat-header"><button className="mobile-back" onClick={() => setActive(null)}><ChevronLeft /></button><div className="chat-peer"><Avatar name={activeTitle} id={active.contactId} version={active.avatarVersion} tone={active.contactType === 1 ? 'orange' : 'blue'} onClick={() => openContactDetails(active)} title="查看联系人详情" /><div><h3>{activeTitle}</h3><span><i className={socketState === 'open' ? 'online' : ''}></i>{socketState === 'open' ? '在线' : '连接中'}</span></div></div><button className="icon-btn" title="更多" onClick={() => active.contactType === 1 && openContactDetails(active)}><MoreHorizontal /></button></header>
          <div className="message-scroll">{currentMessages.length ? currentMessages.map((msg, index) => [3, 8, 9, 10, 11, 12].includes(msg.messageType)
            ? <div className="group-event" key={msg.messageId || `${msg.sendTime}-${index}`}>{msg.messageContent}</div>
            : <div className={`message-row ${msg.sendUserId === user.userId ? 'mine' : ''}`} key={msg.messageId || `${msg.sendTime}-${index}`}><Avatar name={msg.sendUserNickName || activeTitle} small tone={msg.sendUserId === user.userId ? 'green' : 'blue'} onClick={msg.sendUserId !== user.userId ? () => openContactDetails({ ...active, contactId: msg.sendUserId, contactName: msg.sendUserNickName || activeTitle, nickName: msg.sendUserNickName || activeTitle }) : undefined} title={msg.sendUserId !== user.userId ? '查看联系人详情' : undefined} /><div><span className="message-author">{msg.sendUserId === user.userId ? '我' : msg.sendUserNickName || activeTitle}</span><div className="bubble">{msg.messageContent}</div><time>{timeText(msg.sendTime)}</time></div></div>
          ) : <div className="chat-empty"><div className="empty-icon">✦</div><h3>开始聊天</h3><p>发送一条消息，和 {activeTitle} 打个招呼吧</p></div>}</div>
          <footer className="composer"><div className="composer-tools"><button title="表情"><Smile /></button><button title="附件"><Paperclip /></button><button title="语音"><Mic /></button></div><textarea value={composer} onChange={e => setComposer(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }} placeholder="输入消息，按 Enter 发送" /><button className="send-btn" onClick={send}><Send size={17} /></button></footer>
        </> : <div className="welcome"><div className="welcome-orbit"><MessageCircle size={30} /></div><h1>让每次相遇，都有回应</h1><p>从左侧选择一个聊天，开始你的 EasyChat 时光</p><span className="welcome-status"><i className={socketState === 'open' ? 'online' : ''}></i>{socketState === 'open' ? '实时连接已建立' : '等待实时连接'}</span></div>}
      </main>
      {contactDetails && <ContactDetails contact={contactDetails.contact} loading={contactDetails.loading} userId={user.userId} onClose={closeContactDetails} onChat={chatFromDetails} onEdit={() => { setEditingGroup(contactDetails.contact); closeContactDetails() }} />}
      {(showCreateGroup || editingGroup) && <GroupDialog group={editingGroup} onClose={() => { setShowCreateGroup(false); setEditingGroup(null) }} onSaved={async values => { setSection('groups'); await load(user); if (editingGroup && values.avaterfile) setGroups(old => old.map(group => group.groupId === editingGroup.groupId ? { ...group, avatarVersion: Date.now() } : group)); setNotice(editingGroup ? '群资料已更新' : '群聊已创建') }} />}
      {showProfile && <ProfileDialog user={user} onClose={() => setShowProfile(false)} onSaved={async values => { const latest = await api.me(); const next = { ...latest, avatarVersion: values.avator ? Date.now() : user.avatarVersion }; await load(next); setNotice('个人资料已更新') }} />}
    </div>
  )
}

createRoot(document.getElementById('root')).render(<App />)
