import React, { useEffect, useRef, useState } from 'react'
import { Bot, Check, ChevronDown, Loader2, Send, ShieldAlert, Sparkles, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { createTicket } from '../api/support'
import './SupportChatbot.css'

const API_URL = (import.meta.env.VITE_SUPPORT_AGENT_URL || '').replace(/\/$/, '')
const API_KEY = import.meta.env.VITE_SUPPORT_AGENT_API_KEY || ''
const AVATAR_SRC = '/support-agent-robot.png'
const QUICK_PROMPTS = [
  'How do I track an application?',
  'I forgot my password.',
  'How can I match my resume to a job?',
]

function makeWelcome() {
  return {
    id: 'welcome',
    role: 'assistant',
    text: 'Hi! I’m your Smart Job Tracker guide. Ask me about applications, resume matching, job discovery, or account help.',
  }
}

export default function SupportChatbot() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([makeWelcome()])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [avatarState, setAvatarState] = useState('idle')
  const [tilt, setTilt] = useState({ x: 0, y: 0 })
  const inputRef = useRef(null)
  const panelRef = useRef(null)

  useEffect(() => {
    if (open) inputRef.current?.focus()
  }, [open])

  function handleAvatarMove(event) {
    const rect = event.currentTarget.getBoundingClientRect()
    const x = ((event.clientX - rect.left) / rect.width - 0.5) * 12
    const y = ((event.clientY - rect.top) / rect.height - 0.5) * -12
    setTilt({ x: y, y: x })
  }

  function resetTilt() {
    setTilt({ x: 0, y: 0 })
  }

  function finishAvatarState(state) {
    setAvatarState(state)
    window.setTimeout(() => setAvatarState('idle'), state === 'thinking' ? 1200 : 1800)
  }

  async function createEscalationRequest(text, data) {
    try {
      const ticket = await createTicket({
        subject: `AI support escalation: ${text.slice(0, 150)}`,
        category: 'BUG',
        description: [
          'Automatically created from the Smart Job Tracker AI support chatbot.',
          `Escalation event: ${data.fallback?.event_id || 'not provided'}`,
          `Reason: ${data.reason || 'Human review required.'}`,
          '',
          'Customer message:',
          text,
        ].join('\n'),
      })
      return ticket?.id || null
    } catch {
      return null
    }
  }

  async function sendMessage(event, suppliedText) {
    event?.preventDefault()
    const text = (suppliedText ?? draft).trim()
    if (!text || busy) return
    setDraft('')
    setMessages((current) => [...current, { id: `${Date.now()}-user`, role: 'user', text }])
    setBusy(true)
    setAvatarState('thinking')
    if (!API_URL) {
      setMessages((current) => [...current, {
        id: `${Date.now()}-config`,
        role: 'assistant',
        text: 'The support assistant is not connected yet. Please try again after the support service is configured.',
        error: true,
      }])
      setBusy(false)
      finishAvatarState('error')
      return
    }
    try {
      const headers = { 'Content-Type': 'application/json' }
      if (API_KEY) headers['X-API-Key'] = API_KEY
      const response = await fetch(`${API_URL}/api/agent/run`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ message: text, brand: 'SmartJobTracker' }),
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.detail || `Support service returned ${response.status}`)
      const escalated = data.decision === 'escalate_to_human'
      const ticketId = escalated ? await createEscalationRequest(text, data) : null
      setMessages((current) => [...current, {
        id: `${Date.now()}-assistant`,
        role: 'assistant',
        text: escalated
          ? (data.fallback?.message || data.draft_reply || 'A support specialist will review your message.')
          : (data.draft_reply || 'Thanks for reaching out. A support specialist will review your message.'),
        decision: escalated ? 'escalate' : 'handled',
        reason: data.reason,
        ticketId,
      }])
      finishAvatarState('success')
    } catch (error) {
      setMessages((current) => [...current, {
        id: `${Date.now()}-error`,
        role: 'assistant',
        text: 'I can’t reach the support service right now. Please try again in a moment or use the Support page.',
        error: true,
        detail: error.message,
      }])
      finishAvatarState('error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={`support-chatbot ${open ? 'is-open' : ''} avatar-${avatarState}`} ref={panelRef}>
      {open && (
        <section id="support-chat-panel" className="support-chat-panel" aria-label="Smart Job Tracker support chat">
          <header className="support-chat-header">
            <div className="support-chat-agent-mark"><Bot size={18} aria-hidden="true" /></div>
            <div>
              <p className="support-chat-title">Tracker Guide</p>
              <p className="support-chat-status"><span /> AI support assistant</p>
            </div>
            <button type="button" className="support-chat-icon-button" onClick={() => setOpen(false)} aria-label="Close support chat">
              <X size={18} aria-hidden="true" />
            </button>
          </header>
          <div className="support-chat-messages" aria-live="polite">
            {messages.map((message) => (
              <div key={message.id} className={`support-chat-message-row ${message.role}`}>
                <div className={`support-chat-message ${message.error ? 'error' : ''}`}>
                  <p>{message.text}</p>
                  {message.decision === 'handled' && <span className="support-chat-result handled"><Check size={12} /> Suggested answer</span>}
                  {message.decision === 'escalate' && <>
                    <span className="support-chat-result escalated"><ShieldAlert size={12} /> Human review recommended</span>
                    {message.ticketId ? <Link className="support-chat-ticket" to="/support">Request #{message.ticketId} created in Help &amp; support</Link> : <span className="support-chat-ticket pending">We could not create the support request automatically.</span>}
                  </>}
                </div>
              </div>
            ))}
            {busy && <div className="support-chat-message-row assistant"><div className="support-chat-message typing"><Loader2 size={15} className="spin" /> Thinking through that…</div></div>}
          </div>
          <div className="support-chat-prompts">
            {QUICK_PROMPTS.map((prompt) => <button key={prompt} type="button" onClick={(event) => sendMessage(event, prompt)}>{prompt}</button>)}
          </div>
          <form className="support-chat-compose" onSubmit={sendMessage}>
            <label className="sr-only" htmlFor="support-chat-input">Ask the support assistant</label>
            <input id="support-chat-input" ref={inputRef} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Ask a question…" maxLength={500} disabled={busy} />
            <button type="submit" aria-label="Send message" disabled={busy || !draft.trim()}><Send size={16} aria-hidden="true" /></button>
          </form>
          <p className="support-chat-disclaimer"><Sparkles size={12} /> AI suggestions can be reviewed by a human when needed.</p>
        </section>
      )}
      <button
        type="button"
        className={`support-chat-launcher ${busy ? 'is-thinking' : ''}`}
        aria-expanded={open}
        aria-controls="support-chat-panel"
        aria-label={open ? 'Close support chat' : 'Open Smart Job Tracker support chat'}
        onClick={() => setOpen((value) => !value)}
        onPointerMove={handleAvatarMove}
        onPointerLeave={resetTilt}
        style={{ '--tilt-x': `${tilt.x}deg`, '--tilt-y': `${tilt.y}deg` }}
      >
        <span className="support-chat-orbit orbit-one" />
        <span className="support-chat-orbit orbit-two" />
        <span className="support-chat-avatar-wrap"><img src={AVATAR_SRC} alt="3D robot support guide" className="support-chat-avatar" /></span>
        <span className="support-chat-pulse" />
        <span className="support-chat-launch-label">Need help?</span>
        <ChevronDown className="support-chat-chevron" size={16} aria-hidden="true" />
      </button>
    </div>
  )
}
