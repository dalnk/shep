import React, { useState, useEffect, useRef } from 'react';
import { 
  Sparkles, 
  Send, 
  Zap, 
  ChevronRight, 
  ChevronDown, 
  Wifi, 
  WifiOff, 
  Terminal,
  ArrowDown 
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface Pane {
  id: string;
  title: string;
  agentName: string;
  modelShortname?: string;
  state: 'WORKING' | 'BLOCKED' | 'DONE' | 'IDLE';
  isBackground?: boolean;
  waitingDurationSeconds?: number;
  activeAction?: string;
}

interface Message {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  tools?: string[];
  thinking?: string;
  timestamp: number;
}

export function App() {
  const host = window.location.hostname || 'localhost';
  const port = '8765';
  const [connected, setConnected] = useState(false);
  const [panes, setPanes] = useState<Pane[]>([]);
  const [selectedPaneId, setSelectedPaneId] = useState<string>('');
  const [activeModel, setActiveModel] = useState<string>('Claude');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);
  const canvasRef = useRef<HTMLDivElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const isAutoScrollRef = useRef(true);

  // Auto-scroll to bottom when messages update
  useEffect(() => {
    if (isAutoScrollRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    // If user is within 100px of bottom, stick to bottom
    const atBottom = distanceToBottom < 100;
    isAutoScrollRef.current = atBottom;
    setShowScrollBottom(distanceToBottom > 160);
  };

  const scrollToBottom = () => {
    isAutoScrollRef.current = true;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    setShowScrollBottom(false);
  };

  // Connect to Herdr Daemon WebSocket
  useEffect(() => {
    let active = true;
    let socket: WebSocket | null = null;
    let pollTimer: any = null;

    const connect = () => {
      const wsUrl = `ws://${host}:${port}`;
      socket = new WebSocket(wsUrl);

      socket.onopen = () => {
        if (!active) return;
        setConnected(true);
        // Request enriched pane list
        socket?.send(JSON.stringify({ method: 'pane.list', id: 'init-panes', params: {} }));
        // Also subscribe to events
        socket?.send(JSON.stringify({ method: 'events.subscribe', params: {} }));
      };

      socket.onclose = () => {
        if (!active) return;
        setConnected(false);
        setTimeout(connect, 3000);
      };

      socket.onerror = () => {
        if (!active) return;
        setConnected(false);
      };

      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          
          // Handle pane.list response
          if (data.id === 'init-panes' || (data.result && data.result.panes)) {
            const rawPanes = (data.result?.panes || []) as any[];
            const mapped: Pane[] = rawPanes.map(p => {
              const agent = p.agent || (p.title?.toLowerCase().includes('claude') ? 'claude' :
                                       p.title?.toLowerCase().includes('codex') ? 'codex' :
                                       p.title?.toLowerCase().includes('grok') ? 'grok' : 'shell');
              const statusRaw = (p.agent_status || 'idle').toUpperCase();
              const state: 'WORKING' | 'BLOCKED' | 'DONE' | 'IDLE' = 
                statusRaw === 'WORKING' ? 'WORKING' :
                statusRaw === 'BLOCKED' ? 'BLOCKED' :
                statusRaw === 'DONE' ? 'DONE' : 'IDLE';

              const isBg = Boolean(
                p.title?.toLowerCase().includes('task-') ||
                p.title?.toLowerCase().includes('background') ||
                p.title?.toLowerCase().includes('spoon') ||
                p.title?.toLowerCase().includes('subagent') ||
                p.is_background
              );

              return {
                id: p.pane_id,
                title: p.title || `Pane ${p.pane_id}`,
                agentName: agent === '_' ? 'under' : agent,
                modelShortname: p.model_shortname,
                state,
                isBackground: isBg,
                waitingDurationSeconds: p.waiting_duration_seconds,
                activeAction: p.active_action
              };
            });

            if (mapped.length > 0) {
              setPanes(mapped);
              setSelectedPaneId(prev => prev && mapped.some(m => m.id === prev) ? prev : mapped[0].id);
            }
          }

          // Handle pane.transcript response
          if (data.result && data.result.turns !== undefined) {
            const res = data.result;
            if (res.model_shortname) {
              setActiveModel(res.model_shortname);
            }
            const turns = res.turns || [];
            if (turns.length > 0) {
              const msgs: Message[] = turns.map((t: any, idx: number) => ({
                id: `turn-${idx}`,
                sender: t.role === 'user' ? 'user' : 'assistant',
                text: t.text || '',
                tools: t.tools || undefined,
                thinking: t.thinking || undefined,
                timestamp: Date.now() - (turns.length - idx) * 5000
              }));
              setMessages(msgs);
            }
          }
        } catch (err) {
          console.error('Failed to parse herdr frame', err);
        }
      };

      setWs(socket);
    };

    connect();

    // Poll pane list periodically
    pollTimer = setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ method: 'pane.list', id: 'poll-panes', params: {} }));
      }
    }, 5000);

    return () => {
      active = false;
      clearInterval(pollTimer);
      socket?.close();
    };
  }, [host, port]);

  // When selected pane changes, fetch its structured transcript
  useEffect(() => {
    if (ws && connected && selectedPaneId) {
      ws.send(JSON.stringify({
        method: 'pane.transcript',
        id: `transcript-${selectedPaneId}`,
        params: { pane_id: selectedPaneId }
      }));
    }
  }, [ws, connected, selectedPaneId]);

  const handleSend = () => {
    if (!input.trim()) return;
    const userMsg: Message = {
      id: String(Date.now()),
      sender: 'user',
      text: input,
      timestamp: Date.now()
    };
    setMessages(prev => [...prev, userMsg]);
    const sentText = input;
    setInput('');

    if (ws && connected && selectedPaneId) {
      ws.send(JSON.stringify({
        method: 'pane.send_input',
        params: { pane_id: selectedPaneId, input: sentText + '\n' }
      }));
      // Poll transcript shortly after sending
      setTimeout(() => {
        ws.send(JSON.stringify({
          method: 'pane.transcript',
          id: `refresh-${selectedPaneId}`,
          params: { pane_id: selectedPaneId }
        }));
      }, 800);
    }
  };

  const selectedPane = panes.find(p => p.id === selectedPaneId) || panes[0];

  const mainPanes = panes.filter(p => !p.isBackground);
  const bgPanes = panes.filter(p => p.isBackground);

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', background: 'var(--bg-page)' }}>
      {/* Sidebar: Agents & Workspaces */}
      <div style={{
        width: '320px',
        borderRight: '1px solid var(--border-subtle)',
        background: 'var(--bg-surface)',
        display: 'flex',
        flexDirection: 'column'
      }}>
        {/* Top Header */}
        <div style={{
          padding: '16px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid var(--border-subtle)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontWeight: 800, fontSize: '17px', letterSpacing: '-0.02em' }}>Shepard</span>
            <span style={{ 
              fontSize: '11px', 
              padding: '2px 6px', 
              borderRadius: '4px', 
              background: 'var(--bg-surface-high)',
              color: 'var(--text-secondary)',
              fontWeight: 500
            }}>WebUX</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px' }}>
            {connected ? (
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--cds-clay)', fontSize: '12px', fontWeight: 600 }}>
                <Wifi size={15} />
                <span>Live</span>
              </span>
            ) : (
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#888', fontSize: '12px' }}>
                <WifiOff size={15} />
                <span>Offline</span>
              </span>
            )}
          </div>
        </div>

        {/* Panes list */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px' }}>
          {/* Main Agent Sessions */}
          <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '0.04em', color: 'var(--text-secondary)', marginBottom: '8px', paddingLeft: '8px' }}>
            AGENT SESSIONS ({mainPanes.length})
          </div>
          {mainPanes.map(pane => {
            const isSelected = pane.id === selectedPaneId;
            return (
              <div
                key={pane.id}
                onClick={() => setSelectedPaneId(pane.id)}
                style={{
                  padding: '9px 12px',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  marginBottom: '3px',
                  background: isSelected ? 'var(--bg-surface-high)' : 'transparent',
                  border: isSelected ? '1px solid var(--border-subtle)' : '1px solid transparent',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px'
                }}
              >
                <div style={{
                  width: '28px',
                  height: '28px',
                  borderRadius: '6px',
                  background: isSelected ? 'var(--cds-clay)' : 'var(--bg-surface-high)',
                  color: isSelected ? '#fff' : 'var(--text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '12px',
                  fontWeight: 700
                }}>
                  {pane.agentName.slice(0, 1).toUpperCase()}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '13px', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {pane.title}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span>{pane.agentName}</span>
                    <span>·</span>
                    <span style={{ textTransform: 'lowercase' }}>{pane.state}</span>
                  </div>
                </div>
                {pane.state === 'WORKING' && (
                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--cds-clay)' }} />
                )}
                {pane.state === 'BLOCKED' && (
                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#eab308' }} />
                )}
              </div>
            );
          })}

          {/* Background Tasks / Subagents */}
          {bgPanes.length > 0 && (
            <>
              <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '0.04em', color: 'var(--text-secondary)', marginTop: '20px', marginBottom: '8px', paddingLeft: '8px' }}>
                BACKGROUND TASKS ({bgPanes.length})
              </div>
              {bgPanes.map(pane => {
                const isSelected = pane.id === selectedPaneId;
                return (
                  <div
                    key={pane.id}
                    onClick={() => setSelectedPaneId(pane.id)}
                    style={{
                      padding: '8px 12px',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      marginBottom: '3px',
                      background: isSelected ? 'var(--bg-surface-high)' : 'transparent',
                      border: isSelected ? '1px solid var(--border-subtle)' : '1px solid transparent',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      opacity: 0.85
                    }}
                  >
                    <Terminal size={16} color="var(--text-secondary)" />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '12.5px', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {pane.title}
                      </div>
                      <div style={{ fontSize: '10.5px', color: 'var(--text-secondary)' }}>
                        {pane.agentName} · {pane.state.toLowerCase()}
                      </div>
                    </div>
                    {pane.state === 'WORKING' && (
                      <div style={{ width: '7px', height: '7px', borderRadius: '50%', background: 'var(--cds-clay)' }} />
                    )}
                  </div>
                );
              })}
            </>
          )}
        </div>
      </div>

      {/* Main Canvas: Claude-like Chat Flow */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        minWidth: 0,
        background: 'var(--bg-page)'
      }}>
        {/* Agent Header */}
        <div style={{
          padding: '16px 24px',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: '15px' }}>{selectedPane?.title}</div>
            <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
              Connected to {selectedPane?.agentName || 'agent'} {selectedPane?.modelShortname ? `(${selectedPane.modelShortname})` : ''} via herdr
            </div>
          </div>
          {selectedPane?.activeAction && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '12px',
              padding: '4px 10px',
              borderRadius: '20px',
              background: 'var(--bg-surface-high)',
              color: 'var(--cds-clay)',
              fontWeight: 500
            }}>
              <span className="cds-spinner" style={{ width: '10px', height: '10px', border: '2px solid var(--cds-clay)', borderTopColor: 'transparent', borderRadius: '50%', display: 'inline-block', animation: 'spin 1s linear infinite' }} />
              <span>{selectedPane.activeAction}</span>
            </div>
          )}
        </div>

        {/* Message Canvas */}
        <div 
          ref={canvasRef}
          onScroll={handleScroll}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '32px 0',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            position: 'relative'
          }}
        >
          <div style={{ width: '100%', maxWidth: '740px', padding: '0 24px' }}>
            {messages.length === 0 && (
              <div style={{ textAlign: 'center', color: 'var(--text-secondary)', marginTop: '80px', fontSize: '14px' }}>
                <Sparkles size={24} color="var(--cds-clay)" style={{ margin: '0 auto 12px auto' }} />
                <div>Ready for conversation with {selectedPane?.agentName || 'agent'}.</div>
              </div>
            )}
            {messages.map(msg => (
              <div key={msg.id} style={{ marginBottom: '32px' }}>
                {msg.sender === 'user' ? (
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <div style={{
                      background: 'var(--bg-surface-high)',
                      padding: '12px 18px',
                      borderRadius: '18px',
                      maxWidth: '85%',
                      fontSize: '14.5px',
                      lineHeight: '1.5'
                    }}>
                      {msg.text}
                    </div>
                  </div>
                ) : (
                  <div>
                    {/* Agent Header */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                      <Sparkles size={16} color="var(--cds-clay)" />
                      <span style={{ fontWeight: 600, fontSize: '13px' }}>
                        {selectedPane?.modelShortname || activeModel || selectedPane?.agentName?.toUpperCase() || 'Agent'}
                      </span>
                    </div>

                    {/* Thought Pill (Claude Style) */}
                    {msg.thinking && (
                      <ThoughtPill text={msg.thinking} />
                    )}

                    {/* Tool Summary Pill (Claude Style) */}
                    {msg.tools && msg.tools.length > 0 && (
                      <ToolSummaryPill tools={msg.tools} />
                    )}

                    {/* Clean response text directly on canvas */}
                    <div style={{ marginTop: '10px' }} className="prose-content">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {msg.text}
                      </ReactMarkdown>
                    </div>
                  </div>
                )}
              </div>
            ))}
            <div ref={messagesEndRef} style={{ height: '1px' }} />
          </div>

          {/* Floating Scroll-to-Bottom Button (Claude style) */}
          {showScrollBottom && (
            <button
              onClick={scrollToBottom}
              style={{
                position: 'fixed',
                bottom: '88px',
                right: 'calc(50% - 20px)',
                width: '36px',
                height: '36px',
                borderRadius: '50%',
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                color: 'var(--text-secondary)',
                zIndex: 10,
                transition: 'transform 0.15s ease'
              }}
              title="Scroll to bottom"
            >
              <ArrowDown size={17} />
            </button>
          )}
        </div>

        {/* Composer: Claude-style Pill Input */}
        <div style={{ padding: '0 24px 24px 24px', display: 'flex', justifyContent: 'center' }}>
          <div style={{
            width: '100%',
            maxWidth: '740px',
            border: '1px solid var(--border-subtle)',
            borderRadius: '24px',
            background: 'var(--bg-surface)',
            padding: '10px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.04)'
          }}>
            <input
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSend()}
              placeholder={`Message ${selectedPane?.agentName || 'agent'}…`}
              style={{
                flex: 1,
                border: 'none',
                outline: 'none',
                background: 'transparent',
                fontSize: '14px',
                color: 'var(--text-primary)'
              }}
            />
            <button
              onClick={handleSend}
              disabled={!input.trim()}
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                background: input.trim() ? 'var(--cds-clay)' : 'transparent',
                color: input.trim() ? '#fff' : 'var(--text-secondary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease'
              }}
            >
              <Send size={15} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ThoughtPill({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div style={{ marginBottom: '6px' }}>
      <button
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '4px',
          fontSize: '12px',
          color: 'var(--text-secondary)',
          padding: '2px 6px',
          borderRadius: '6px'
        }}
      >
        <span>Thought</span>
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
      </button>
      {expanded && (
        <div style={{
          marginTop: '6px',
          paddingLeft: '10px',
          borderLeft: '2px solid var(--border-subtle)',
          fontSize: '13px',
          fontStyle: 'italic',
          color: 'var(--text-secondary)'
        }}>
          {text}
        </div>
      )}
    </div>
  );
}

function ToolSummaryPill({ tools }: { tools: string[] }) {
  const [expanded, setExpanded] = useState(false);

  // Group tools into Claude Desktop phrasing
  const summary = React.useMemo(() => {
    const counts: Record<string, number> = {};
    for (const t of tools) {
      const tl = t.toLowerCase();
      const cat = tl.includes('spawn') || tl.includes('subagent') ? 'background agents' :
                  tl.includes('task') ? 'background tasks' :
                  tl.includes('schedule') || tl.includes('timer') ? 'timers' :
                  tl.includes('run') || tl.includes('command') ? 'commands' :
                  tl.includes('read') || tl.includes('view') ? 'read files' :
                  tl.includes('edit') || tl.includes('replace') || tl.includes('write') ? 'edits' : 'tools';
      counts[cat] = (counts[cat] || 0) + 1;
    }
    const parts = Object.entries(counts).map(([k, v]) => `${v} ${k}`);
    return `${tools.length} actions (${parts.join(', ')})`;
  }, [tools]);

  return (
    <div style={{ marginBottom: '8px' }}>
      <button
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '6px',
          fontSize: '12px',
          color: 'var(--text-secondary)',
          padding: '3px 10px',
          borderRadius: '8px',
          background: 'var(--bg-surface-high)',
          border: '1px solid var(--border-subtle)',
          cursor: 'pointer'
        }}
      >
        <Zap size={12} color="var(--cds-clay)" />
        <span>{summary}</span>
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
      </button>
      {expanded && (
        <div style={{
          marginTop: '6px',
          padding: '10px 14px',
          borderRadius: '8px',
          background: 'var(--bg-surface-high)',
          border: '1px solid var(--border-subtle)',
          fontFamily: 'var(--cds-font-mono)',
          fontSize: '11.5px',
          lineHeight: '1.6',
          color: 'var(--text-primary)'
        }}>
          {tools.map((t, i) => {
            const tl = t.toLowerCase();
            const isBg = tl.includes('spawn') || tl.includes('task') || tl.includes('subagent');
            return (
              <div key={i} style={{ 
                display: 'flex', 
                alignItems: 'center', 
                gap: '8px',
                padding: '2px 0',
                color: isBg ? 'var(--cds-clay)' : 'var(--text-primary)'
              }}>
                <span style={{ color: 'var(--text-secondary)' }}>•</span>
                <span>{t}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
