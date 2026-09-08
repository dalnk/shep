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
  ArrowDown,
  PanelRightClose,
  PanelRightOpen,
  CheckCircle2,
  ExternalLink,
  RefreshCw,
  Copy,
  Check,
  X,
  Smartphone,
  ArrowUpRight,
  Cloud
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

interface TaskItem {
  id: string;
  title: string;
  category: 'command' | 'subagent' | 'task' | 'timer' | 'tool';
  status: 'running' | 'done' | 'failed';
  timestamp: number;
  output?: string;
}

export function App() {
  // Support URL params (?h=...&p=... or #h=... or ?host=... or tailscale MagicDNS) with localStorage memory
  const urlParams = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  
  const initialHost = urlParams.get('host') || urlParams.get('h') 
    || hashParams.get('host') || hashParams.get('h')
    || localStorage.getItem('shep_server_host')
    || window.location.hostname 
    || 'localhost';

  const initialPort = urlParams.get('port') || urlParams.get('p')
    || hashParams.get('port') || hashParams.get('p')
    || localStorage.getItem('shep_server_port')
    || '8765';

  const [host, setHost] = useState(initialHost);
  const [port, setPort] = useState(initialPort);

  // Save successful pairing host to localStorage
  useEffect(() => {
    if (host && host !== 'localhost') {
      localStorage.setItem('shep_server_host', host);
    }
    if (port) {
      localStorage.setItem('shep_server_port', port);
    }
  }, [host, port]);
  const [connected, setConnected] = useState(false);
  const [showPairingModal, setShowPairingModal] = useState(false);
  const [panes, setPanes] = useState<Pane[]>([]);
  const [selectedPaneId, setSelectedPaneId] = useState<string>('');
  const [activeModel, setActiveModel] = useState<string>('Claude');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);

  // Claude Desktop Secondary Pane state (starts collapsed, opens on clicking task/terminal/tool)
  const [showAuxPane, setShowAuxPane] = useState<boolean>(false);
  const [auxPaneWidth, setAuxPaneWidth] = useState<number>(400);
  const isDraggingRef = useRef<boolean>(false);
  const startXRef = useRef<number>(0);
  const startWidthRef = useRef<number>(400);

  const [auxTab, setAuxTab] = useState<'tasks' | 'terminal' | 'preview'>('tasks');
  const [terminalOutput, setTerminalOutput] = useState<string>('');
  const [terminalLoading, setTerminalLoading] = useState<boolean>(false);
  const [terminalPaneId, setTerminalPaneId] = useState<string>('');
  const [copiedTerminal, setCopiedTerminal] = useState<boolean>(false);
  const [copiedMsgId, setCopiedMsgId] = useState<string | null>(null);

  const [showHistoryMenu, setShowHistoryMenu] = useState(false);
  const canvasRef = useRef<HTMLDivElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const isAutoScrollRef = useRef(true);
  const initialLoadRef = useRef(true);
  const lastPaneIdRef = useRef<string>('');

  // Auto-scroll logic: instant to bottom when opening/switching threads, smooth when streaming active messages
  useEffect(() => {
    if (messages.length === 0) return;
    
    if (initialLoadRef.current || lastPaneIdRef.current !== selectedPaneId) {
      initialLoadRef.current = false;
      lastPaneIdRef.current = selectedPaneId;
      // Instant scroll to bottom so there is no disorienting top-to-bottom scroll animation on opening a thread
      if (canvasRef.current) {
        canvasRef.current.scrollTop = canvasRef.current.scrollHeight;
      } else {
        messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      }
    } else if (isAutoScrollRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, selectedPaneId]);

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const atBottom = distanceToBottom < 100;
    isAutoScrollRef.current = atBottom;
    setShowScrollBottom(distanceToBottom > 160);
  };

  const scrollToBottom = () => {
    isAutoScrollRef.current = true;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    setShowScrollBottom(false);
  };

  const jumpToTurn = (index: number) => {
    setShowHistoryMenu(false);
    const targetEl = document.getElementById(`msg-turn-${index}`);
    if (targetEl) {
      targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      isAutoScrollRef.current = false;
      setShowScrollBottom(true);
    }
  };

  // Keyboard navigation shortcuts matching Claude Desktop:
  // - Cmd+Alt+Up / Alt+Up: jump to previous prompt
  // - Cmd+Alt+Down / Alt+Down: jump to next prompt
  // - Cmd+B: toggle tasks side pane
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Toggle side pane with Cmd+B / Ctrl+B
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'b') {
        e.preventDefault();
        setShowAuxPane(prev => !prev);
        return;
      }

      // Jump to previous/next prompt with Cmd+Alt+Up/Down
      const isAlt = e.altKey;
      const isMacCmd = navigator.platform.toUpperCase().indexOf('MAC') >= 0 ? e.metaKey : true;
      if (isAlt && isMacCmd) {
        if (e.key === 'ArrowUp') {
          e.preventDefault();
          // Find current prompt closest to or above view
          const userMsgIndices = messages
            .map((m, idx) => (m.sender === 'user' ? idx : -1))
            .filter(i => i >= 0);
          if (userMsgIndices.length > 0) {
            const container = canvasRef.current;
            const curTop = container ? container.scrollTop : 0;
            let targetIdx = userMsgIndices[0];
            for (let i = userMsgIndices.length - 1; i >= 0; i--) {
              const el = document.getElementById(`msg-turn-${userMsgIndices[i]}`);
              if (el && el.offsetTop < curTop - 20) {
                targetIdx = userMsgIndices[i];
                break;
              }
            }
            jumpToTurn(targetIdx);
          }
        } else if (e.key === 'ArrowDown') {
          e.preventDefault();
          const userMsgIndices = messages
            .map((m, idx) => (m.sender === 'user' ? idx : -1))
            .filter(i => i >= 0);
          if (userMsgIndices.length > 0) {
            const container = canvasRef.current;
            const curTop = container ? container.scrollTop : 0;
            let targetIdx = userMsgIndices[userMsgIndices.length - 1];
            for (let i = 0; i < userMsgIndices.length; i++) {
              const el = document.getElementById(`msg-turn-${userMsgIndices[i]}`);
              if (el && el.offsetTop > curTop + 40) {
                targetIdx = userMsgIndices[i];
                break;
              }
            }
            jumpToTurn(targetIdx);
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [messages]);

  // Handle panel resizing via drag handle (matching Claude Desktop draggable split handle)
  const handleMouseDownResize = (e: React.MouseEvent) => {
    e.preventDefault();
    isDraggingRef.current = true;
    startXRef.current = e.clientX;
    startWidthRef.current = auxPaneWidth;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const onMouseMove = (moveEvent: MouseEvent) => {
      if (!isDraggingRef.current) return;
      const delta = startXRef.current - moveEvent.clientX;
      const newWidth = Math.min(Math.max(280, startWidthRef.current + delta), window.innerWidth * 0.75);
      setAuxPaneWidth(newWidth);
    };

    const onMouseUp = () => {
      isDraggingRef.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
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

          // Handle pane.read response (terminal view)
          if (data.id?.startsWith('read-term-')) {
            setTerminalLoading(false);
            const text = data.result?.read?.text || data.result?.text || '';
            setTerminalOutput(text || '(no recent terminal output)');
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

  // When selected pane changes, fetch its structured transcript and terminal buffer
  useEffect(() => {
    if (ws && connected && selectedPaneId) {
      ws.send(JSON.stringify({
        method: 'pane.transcript',
        id: `transcript-${selectedPaneId}`,
        params: { pane_id: selectedPaneId }
      }));
      fetchTerminal(selectedPaneId);
    }
  }, [ws, connected, selectedPaneId]);

  const fetchTerminal = (targetPaneId?: string) => {
    const pid = targetPaneId || selectedPaneId;
    if (!ws || !connected || !pid) return;
    setTerminalLoading(true);
    setTerminalPaneId(pid);
    ws.send(JSON.stringify({
      method: 'pane.read',
      id: `read-term-${pid}-${Date.now()}`,
      params: { pane_id: pid, source: 'recent', lines: 80 }
    }));
  };

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

  const copyTerminalOutput = () => {
    if (!terminalOutput) return;
    navigator.clipboard.writeText(terminalOutput);
    setCopiedTerminal(true);
    setTimeout(() => setCopiedTerminal(false), 2000);
  };

  const openToolInTerminal = (toolText: string) => {
    setShowAuxPane(true);
    setAuxTab('terminal');
    // If it mentions a background task or subagent, see if we have a matching background pane
    const matchPane = panes.find(p => p.isBackground && toolText.toLowerCase().includes(p.id.toLowerCase()));
    if (matchPane) {
      fetchTerminal(matchPane.id);
    } else {
      fetchTerminal(selectedPaneId);
    }
  };

  const selectedPane = panes.find(p => p.id === selectedPaneId) || panes[0];
  const mainPanes = panes.filter(p => !p.isBackground);
  const bgPanes = panes.filter(p => p.isBackground);

  // Extract all background tasks and completed tool actions for the Claude Tasks Pane
  const collectedTasks = React.useMemo<TaskItem[]>(() => {
    const list: TaskItem[] = [];
    
    // First include live background panes/tasks
    bgPanes.forEach(bp => {
      list.push({
        id: `pane-${bp.id}`,
        title: bp.title,
        category: bp.title.toLowerCase().includes('subagent') ? 'subagent' : 'task',
        status: bp.state === 'WORKING' ? 'running' : bp.state === 'BLOCKED' ? 'failed' : 'done',
        timestamp: Date.now()
      });
    });

    // Then extract tools from messages
    messages.forEach((m, msgIdx) => {
      if (m.tools) {
        m.tools.forEach((t, tIdx) => {
          const tl = t.toLowerCase();
          const category: TaskItem['category'] = 
            tl.includes('subagent') || tl.includes('spawn') ? 'subagent' :
            tl.includes('task') ? 'task' :
            tl.includes('schedule') || tl.includes('timer') ? 'timer' :
            tl.includes('run') || tl.includes('command') ? 'command' : 'tool';
          
          const isLastMessage = msgIdx === messages.length - 1;
          const isRunning = isLastMessage && selectedPane?.state === 'WORKING' && tIdx === m.tools!.length - 1;

          list.push({
            id: `tool-${msgIdx}-${tIdx}`,
            title: t,
            category,
            status: isRunning ? 'running' : 'done',
            timestamp: m.timestamp
          });
        });
      }
    });

    return list.reverse(); // newest first
  }, [bgPanes, messages, selectedPane]);

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', background: 'var(--bg-page)', overflow: 'hidden' }}>
      {/* Sidebar: Agents & Workspaces */}
      <div style={{
        width: '300px',
        minWidth: '280px',
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
            <span style={{ fontWeight: 800, fontSize: '17px', letterSpacing: '-0.02em' }}>Shep</span>
            <span style={{ 
              fontSize: '11px', 
              padding: '2px 6px', 
              borderRadius: '4px', 
              background: 'var(--bg-surface-high)',
              color: 'var(--text-secondary)',
              fontWeight: 500
            }}>Desktop</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px' }}>
            <button
              onClick={() => setShowPairingModal(true)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                padding: '4px 8px',
                borderRadius: '6px',
                background: connected ? 'rgba(217, 119, 87, 0.1)' : 'rgba(255, 255, 255, 0.06)',
                border: '1px solid var(--border-subtle)',
                color: connected ? 'var(--cds-clay)' : 'var(--text-secondary)',
                fontSize: '11.5px',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.15s ease'
              }}
              title="Setup & Pairing Helper"
            >
              {connected ? (
                <>
                  <Wifi size={13} color="var(--cds-clay)" />
                  <span>Live</span>
                </>
              ) : (
                <>
                  <WifiOff size={13} color="#888" />
                  <span>Pair</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Panes list */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px' }}>
          {panes.length === 0 && (
            <div style={{
              padding: '16px 12px',
              borderRadius: '8px',
              background: 'var(--bg-surface-high)',
              border: '1px dashed var(--border-subtle)',
              textAlign: 'center',
              margin: '8px 4px',
              fontSize: '12px'
            }}>
              <Terminal size={20} color="var(--cds-clay)" style={{ margin: '0 auto 8px auto', opacity: 0.8 }} />
              <div style={{ fontWeight: 600, marginBottom: '4px' }}>No Active Agents</div>
              <div style={{ color: 'var(--text-secondary)', fontSize: '11px', lineHeight: '1.4', marginBottom: '10px' }}>
                Run an agent on your terminal to connect here automatically.
              </div>
              <button
                onClick={() => setShowPairingModal(true)}
                style={{
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--cds-clay)',
                  background: 'transparent',
                  border: '1px solid var(--cds-clay)',
                  borderRadius: '6px',
                  padding: '4px 10px',
                  cursor: 'pointer'
                }}
              >
                Setup Guide
              </button>
            </div>
          )}

          {/* Main Agent Sessions */}
          {mainPanes.length > 0 && (
            <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '0.04em', color: 'var(--text-secondary)', marginBottom: '8px', paddingLeft: '8px' }}>
              AGENT SESSIONS ({mainPanes.length})
            </div>
          )}
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
                BACKGROUND WORKTREES ({bgPanes.length})
              </div>
              {bgPanes.map(pane => {
                const isSelected = pane.id === selectedPaneId;
                return (
                  <div
                    key={pane.id}
                    onClick={() => {
                      setSelectedPaneId(pane.id);
                      fetchTerminal(pane.id);
                    }}
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
                    <Terminal size={15} color="var(--text-secondary)" />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '12px', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
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

      {/* Main Center Canvas: Claude Chat Stream */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        minWidth: 0,
        background: 'var(--bg-page)',
        borderRight: showAuxPane ? '1px solid var(--border-subtle)' : 'none',
        position: 'relative'
      }}>
        {/* Agent Header with Claude-style Split Pane Toggle */}
        <div style={{
          padding: '14px 24px',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'var(--bg-surface)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', position: 'relative' }}>
            {/* Claude Desktop Subtle Timeline Stripes / Beats Scrubber in top corner */}
            {messages.length > 0 && (
              <div 
                style={{ 
                  position: 'relative',
                  display: 'flex',
                  alignItems: 'center',
                  height: '28px',
                  padding: '4px 6px',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  background: showHistoryMenu ? 'var(--bg-surface-high)' : 'transparent',
                  transition: 'background 0.15s ease'
                }}
                onMouseEnter={() => setShowHistoryMenu(true)}
                onMouseLeave={() => setShowHistoryMenu(false)}
              >
                {/* The subtle timeline stripes (horizontal line of vertical tick stripes) */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '3px',
                  padding: '2px 4px'
                }}>
                  {messages.slice(-12).map((m, i, arr) => {
                    const isLatest = i === arr.length - 1;
                    const isUser = m.sender === 'user';
                    return (
                      <div
                        key={m.id || i}
                        style={{
                          width: '2px',
                          height: isLatest ? '14px' : isUser ? '10px' : '7px',
                          borderRadius: '1px',
                          backgroundColor: isLatest 
                            ? 'var(--cds-clay)' 
                            : isUser 
                              ? 'var(--text-secondary)' 
                              : 'rgba(120, 120, 120, 0.35)',
                          transition: 'all 0.15s ease'
                        }}
                      />
                    );
                  })}
                </div>

                {/* Hover Tooltip / Beats Timeline Popover */}
                {showHistoryMenu && (
                  <div 
                    style={{
                      position: 'absolute',
                      top: '32px',
                      left: '0',
                      width: '320px',
                      maxHeight: '380px',
                      background: 'var(--bg-surface)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: '10px',
                      boxShadow: '0 8px 24px rgba(0,0,0,0.16)',
                      zIndex: 100,
                      display: 'flex',
                      flexDirection: 'column',
                      overflow: 'hidden'
                    }}
                    onMouseEnter={() => setShowHistoryMenu(true)}
                    onMouseLeave={() => setShowHistoryMenu(false)}
                  >
                    <div style={{
                      padding: '8px 12px',
                      borderBottom: '1px solid var(--border-subtle)',
                      background: 'var(--bg-surface-high)',
                      fontSize: '11px',
                      fontWeight: 700,
                      letterSpacing: '0.04em',
                      color: 'var(--text-secondary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}>
                      <span>CONVERSATION BEATS</span>
                      <span>{messages.length} TURNS</span>
                    </div>

                    <div style={{ flex: 1, overflowY: 'auto', padding: '6px' }}>
                      {messages.map((m, idx) => (
                        <div
                          key={m.id}
                          onClick={() => jumpToTurn(idx)}
                          style={{
                            padding: '7px 10px',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            fontSize: '12px',
                            marginBottom: '2px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '2px',
                            transition: 'background 0.12s ease'
                          }}
                          onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-surface-high)')}
                          onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '10.5px', color: 'var(--text-secondary)' }}>
                            <span style={{ fontWeight: 600, color: m.sender === 'user' ? 'var(--text-primary)' : 'var(--cds-clay)' }}>
                              {m.sender === 'user' ? 'You' : (selectedPane?.modelShortname || 'Assistant')}
                            </span>
                            <span>Turn #{idx + 1}</span>
                          </div>
                          <div style={{
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            color: 'var(--text-primary)',
                            fontSize: '11.5px'
                          }}>
                            {m.text.slice(0, 70) || (m.tools ? `[${m.tools.length} actions]` : '…')}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            <div>
              <div style={{ fontWeight: 700, fontSize: '15px' }}>{selectedPane?.title}</div>
              <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                Connected to {selectedPane?.agentName || 'agent'} {selectedPane?.modelShortname ? `(${selectedPane.modelShortname})` : ''} via herdr
              </div>
            </div>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
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

            {/* Claude Desktop Split View Toggle Button */}
            <button
              onClick={() => setShowAuxPane(!showAuxPane)}
              title={showAuxPane ? 'Close panel' : 'Open tasks & terminal panel'}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                padding: '6px 10px',
                borderRadius: '8px',
                border: '1px solid var(--border-subtle)',
                background: showAuxPane ? 'var(--bg-surface-high)' : 'transparent',
                color: showAuxPane ? 'var(--cds-clay)' : 'var(--text-secondary)',
                fontSize: '12px',
                fontWeight: 500,
                transition: 'all 0.15s ease'
              }}
            >
              {showAuxPane ? <PanelRightClose size={15} /> : <PanelRightOpen size={15} />}
              <span>{showAuxPane ? 'Close' : 'Tasks'}</span>
              {collectedTasks.length > 0 && !showAuxPane && (
                <span style={{
                  fontSize: '10px',
                  fontWeight: 700,
                  background: 'var(--cds-clay)',
                  color: '#fff',
                  padding: '1px 5px',
                  borderRadius: '10px'
                }}>
                  {collectedTasks.length}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Message Canvas */}
        <div 
          ref={canvasRef}
          onScroll={handleScroll}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '28px 0',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            position: 'relative'
          }}
        >
          <div style={{ width: '100%', maxWidth: '720px', padding: '0 24px' }}>
            {messages.length === 0 && (
              <div style={{ textAlign: 'center', color: 'var(--text-secondary)', marginTop: '40px', fontSize: '14px', maxWidth: '580px', margin: '40px auto 0 auto' }}>
                <div style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: '12px',
                  background: 'rgba(217, 119, 87, 0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 16px auto'
                }}>
                  <Sparkles size={24} color="var(--cds-clay)" />
                </div>
                <div style={{ fontWeight: 700, fontSize: '18px', color: 'var(--text-primary)', marginBottom: '8px' }}>
                  {selectedPane ? `Ready with ${selectedPane.agentName}` : 'Welcome to Shep'}
                </div>
                <div style={{ fontSize: '13px', lineHeight: '1.6', color: 'var(--text-secondary)', marginBottom: '24px' }}>
                  {selectedPane
                    ? `You can send instructions below or watch this session's background worktrees in real-time.`
                    : 'A native remote interface for Claude, Codex, Grok, and multi-agent background tasks.'}
                </div>

                {/* Quickstart Beginner Cards */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '12px', textAlign: 'left' }}>
                  <div style={{
                    padding: '14px',
                    borderRadius: '10px',
                    background: 'var(--bg-surface)',
                    border: '1px solid var(--border-subtle)'
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                      <Terminal size={15} color="var(--cds-clay)" />
                      <span style={{ fontSize: '12.5px', fontWeight: 600, color: 'var(--text-primary)' }}>1. Start Bridge</span>
                    </div>
                    <div style={{ fontSize: '11.5px', color: 'var(--text-secondary)', lineHeight: '1.5' }}>
                      Run <code>python3 bridge/herdr-bridge.py</code> in your project repository on any machine.
                    </div>
                  </div>

                  <div 
                    onClick={() => setShowPairingModal(true)}
                    style={{
                      padding: '14px',
                      borderRadius: '10px',
                      background: 'var(--bg-surface)',
                      border: '1px solid var(--border-subtle)',
                      cursor: 'pointer',
                      transition: 'border-color 0.15s ease'
                    }}
                    onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--cds-clay)')}
                    onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border-subtle)')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Smartphone size={15} color="var(--cds-clay)" />
                        <span style={{ fontSize: '12.5px', fontWeight: 600, color: 'var(--text-primary)' }}>2. Pair Device / Tailscale</span>
                      </div>
                      <ArrowUpRight size={13} color="var(--text-secondary)" />
                    </div>
                    <div style={{ fontSize: '11.5px', color: 'var(--text-secondary)', lineHeight: '1.5' }}>
                      Pair with 1-click URL, Tailscale MagicDNS, or scan QR on Android.
                    </div>
                  </div>
                </div>

                {!connected && (
                  <div style={{ marginTop: '20px' }}>
                    <button
                      onClick={() => setShowPairingModal(true)}
                      style={{
                        padding: '8px 16px',
                        borderRadius: '20px',
                        background: 'var(--cds-clay)',
                        color: '#fff',
                        fontSize: '13px',
                        fontWeight: 600,
                        border: 'none',
                        cursor: 'pointer',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px'
                      }}
                    >
                      <WifiOff size={14} />
                      <span>Open Pairing & Setup Guide</span>
                    </button>
                  </div>
                )}
              </div>
            )}
            {messages.map((msg, idx) => (
              <div id={`msg-turn-${idx}`} key={msg.id} style={{ marginBottom: '32px', scrollMarginTop: '20px' }}>
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

                    {/* Tool Summary Pill with Click to Open Terminal/Tasks */}
                    {msg.tools && msg.tools.length > 0 && (
                      <ToolSummaryPill 
                        tools={msg.tools} 
                        onInspectTool={openToolInTerminal}
                      />
                    )}

                    {/* Clean response text directly on canvas */}
                    <div style={{ marginTop: '10px' }} className="prose-content">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {msg.text}
                      </ReactMarkdown>
                    </div>

                    {/* Claude Desktop-style bottom message actions (Copy text) */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '8px' }}>
                      <button
                        onClick={() => {
                          navigator.clipboard.writeText(msg.text);
                          setCopiedMsgId(msg.id);
                          setTimeout(() => setCopiedMsgId(null), 2000);
                        }}
                        title="Copy message"
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          padding: '3px 6px',
                          borderRadius: '4px',
                          fontSize: '11px',
                          color: 'var(--text-secondary)',
                          cursor: 'pointer',
                          transition: 'color 0.15s ease'
                        }}
                        onMouseEnter={e => (e.currentTarget.style.color = 'var(--text-primary)')}
                        onMouseLeave={e => (e.currentTarget.style.color = 'var(--text-secondary)')}
                      >
                        {copiedMsgId === msg.id ? (
                          <>
                            <Check size={12} color="var(--cds-clay)" />
                            <span style={{ color: 'var(--cds-clay)' }}>Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy size={12} />
                            <span>Copy</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
            <div ref={messagesEndRef} style={{ height: '1px' }} />
          </div>

          {/* Floating Scroll-to-Bottom Button */}
          {showScrollBottom && (
            <button
              onClick={scrollToBottom}
              style={{
                position: 'fixed',
                bottom: '88px',
                right: showAuxPane ? `calc(50% + ${Math.round(auxPaneWidth / 2)}px)` : 'calc(50% - 20px)',
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
                transition: 'all 0.15s ease'
              }}
              title="Scroll to bottom"
            >
              <ArrowDown size={17} />
            </button>
          )}
        </div>

        {/* Composer: Claude-style Pill Input */}
        <div style={{ padding: '0 24px 20px 24px', display: 'flex', justifyContent: 'center' }}>
          <div style={{
            width: '100%',
            maxWidth: '720px',
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

      {/* Claude Desktop Secondary Split Pane with Draggable Resize Handle */}
      {showAuxPane && (
        <div style={{
          position: 'relative',
          width: `${auxPaneWidth}px`,
          minWidth: '280px',
          maxWidth: '75vw',
          background: 'var(--bg-surface)',
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          overflow: 'hidden',
          flexShrink: 0
        }}>
          {/* Draggable Vertical Split Handle */}
          <div
            onMouseDown={handleMouseDownResize}
            title="Drag to resize panel"
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              bottom: 0,
              width: '5px',
              cursor: 'col-resize',
              zIndex: 30,
              background: 'transparent',
              transition: 'background 0.15s ease'
            }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--cds-clay)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
          />

          {/* Pane Header with Tabs */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '10px 16px',
            borderBottom: '1px solid var(--border-subtle)',
            background: 'var(--bg-surface-high)'
          }}>
            <div style={{ display: 'flex', gap: '4px' }}>
              <button
                onClick={() => setAuxTab('tasks')}
                style={{
                  padding: '5px 10px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 600,
                  background: auxTab === 'tasks' ? 'var(--bg-surface)' : 'transparent',
                  color: auxTab === 'tasks' ? 'var(--cds-clay)' : 'var(--text-secondary)',
                  border: auxTab === 'tasks' ? '1px solid var(--border-subtle)' : '1px solid transparent'
                }}
              >
                Tasks ({collectedTasks.length})
              </button>
              <button
                onClick={() => {
                  setAuxTab('terminal');
                  fetchTerminal();
                }}
                style={{
                  padding: '5px 10px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 600,
                  background: auxTab === 'terminal' ? 'var(--bg-surface)' : 'transparent',
                  color: auxTab === 'terminal' ? 'var(--cds-clay)' : 'var(--text-secondary)',
                  border: auxTab === 'terminal' ? '1px solid var(--border-subtle)' : '1px solid transparent'
                }}
              >
                Terminal
              </button>
              <button
                onClick={() => setAuxTab('preview')}
                style={{
                  padding: '5px 10px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 600,
                  background: auxTab === 'preview' ? 'var(--bg-surface)' : 'transparent',
                  color: auxTab === 'preview' ? 'var(--cds-clay)' : 'var(--text-secondary)',
                  border: auxTab === 'preview' ? '1px solid var(--border-subtle)' : '1px solid transparent'
                }}
              >
                Inspector
              </button>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              {auxTab === 'terminal' && (
                <>
                  <button
                    onClick={() => fetchTerminal(terminalPaneId || selectedPaneId)}
                    title="Refresh terminal"
                    style={{
                      padding: '4px',
                      borderRadius: '4px',
                      color: 'var(--text-secondary)',
                      cursor: 'pointer'
                    }}
                  >
                    <RefreshCw size={13} className={terminalLoading ? 'cds-spinner' : ''} />
                  </button>
                  <button
                    onClick={copyTerminalOutput}
                    title="Copy terminal output"
                    style={{
                      padding: '4px',
                      borderRadius: '4px',
                      color: 'var(--text-secondary)',
                      cursor: 'pointer'
                    }}
                  >
                    {copiedTerminal ? <Check size={13} color="var(--cds-clay)" /> : <Copy size={13} />}
                  </button>
                </>
              )}

              {/* Claude Desktop 'X' Close Button */}
              <button
                onClick={() => setShowAuxPane(false)}
                title="Close side panel"
                style={{
                  padding: '4px',
                  borderRadius: '6px',
                  color: 'var(--text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }}
                onMouseEnter={e => (e.currentTarget.style.color = 'var(--text-primary)')}
                onMouseLeave={e => (e.currentTarget.style.color = 'var(--text-secondary)')}
              >
                <X size={15} />
              </button>
            </div>
          </div>

          {/* Tab 1: Tasks (Claude Desktop Style Background & Finished Tasks) */}
          {auxTab === 'tasks' && (
            <div style={{ flex: 1, overflowY: 'auto', padding: '14px 16px' }}>
              <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '0.04em', color: 'var(--text-secondary)', marginBottom: '12px' }}>
                BACKGROUND & COMPLETED TASKS
              </div>

              {collectedTasks.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 16px', color: 'var(--text-secondary)', fontSize: '13px' }}>
                  No tasks spawned yet in this conversation.
                </div>
              ) : (
                collectedTasks.map(task => (
                  <div
                    key={task.id}
                    onClick={() => openToolInTerminal(task.title)}
                    style={{
                      padding: '10px 12px',
                      borderRadius: '8px',
                      background: 'var(--bg-surface-high)',
                      border: '1px solid var(--border-subtle)',
                      marginBottom: '8px',
                      cursor: 'pointer',
                      transition: 'border-color 0.15s ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                      <span style={{
                        fontSize: '10.5px',
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: '0.04em',
                        color: task.category === 'subagent' ? 'var(--cds-clay)' : 'var(--text-secondary)'
                      }}>
                        {task.category}
                      </span>

                      {task.status === 'running' ? (
                        <span style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontSize: '11px',
                          fontWeight: 600,
                          color: 'var(--cds-clay)'
                        }}>
                          <span className="cds-spinner" style={{ width: '8px', height: '8px', border: '1.5px solid var(--cds-clay)', borderTopColor: 'transparent', borderRadius: '50%', display: 'inline-block' }} />
                          running
                        </span>
                      ) : (
                        <span style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontSize: '11px',
                          fontWeight: 500,
                          color: '#22c55e'
                        }}>
                          <CheckCircle2 size={12} />
                          finished
                        </span>
                      )}
                    </div>

                    <div style={{
                      fontSize: '12.5px',
                      fontFamily: 'var(--cds-font-mono)',
                      lineHeight: '1.4',
                      color: 'var(--text-primary)',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap'
                    }}>
                      {task.title}
                    </div>
                  </div>
                ))
              )}
            </div>
          )}

          {/* Tab 2: Terminal / Stdout Stream */}
          {auxTab === 'terminal' && (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{
                padding: '6px 14px',
                fontSize: '11px',
                color: 'var(--text-secondary)',
                borderBottom: '1px solid var(--border-subtle)',
                background: 'var(--bg-surface)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }}>
                <span>Output buffer: {terminalPaneId || selectedPaneId}</span>
                {terminalLoading && <span style={{ color: 'var(--cds-clay)' }}>streaming…</span>}
              </div>
              <div style={{
                flex: 1,
                overflowY: 'auto',
                padding: '12px 14px',
                fontFamily: 'var(--cds-font-mono)',
                fontSize: '11.5px',
                lineHeight: '1.6',
                background: '#121212',
                color: '#e4e4e7',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all'
              }}>
                {terminalOutput || '(no terminal output recorded)'}
              </div>
            </div>
          )}

          {/* Tab 3: Artifacts / Inspector */}
          {auxTab === 'preview' && (
            <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>
              <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '0.04em', color: 'var(--text-secondary)', marginBottom: '12px' }}>
                SESSION DETAILS & ARTIFACTS
              </div>
              <div style={{
                background: 'var(--bg-surface-high)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '8px',
                padding: '12px',
                fontSize: '12px',
                lineHeight: '1.6'
              }}>
                <div><strong>Current Pane:</strong> {selectedPane?.id}</div>
                <div><strong>Agent:</strong> {selectedPane?.agentName}</div>
                <div><strong>Model:</strong> {selectedPane?.modelShortname || 'Default'}</div>
                <div><strong>Status:</strong> {selectedPane?.state}</div>
                <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--border-subtle)' }}>
                  <div><strong>Total Messages:</strong> {messages.length}</div>
                  <div><strong>Daemon Host:</strong> {host}:{port}</div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Beginner-Friendly Pairing & Onboarding Modal */}
      {showPairingModal && (
        <PairingOnboardingModal
          host={host}
          port={port}
          connected={connected}
          onClose={() => setShowPairingModal(false)}
          onSaveHost={(newHost, newPort) => {
            setHost(newHost);
            setPort(newPort);
            localStorage.setItem('shep_server_host', newHost);
            localStorage.setItem('shep_server_port', newPort);
          }}
        />
      )}
    </div>
  );
}

interface PairingModalProps {
  host: string;
  port: string;
  connected: boolean;
  onClose: () => void;
  onSaveHost: (newHost: string, newPort: string) => void;
}

function PairingOnboardingModal({ host, port, connected, onClose, onSaveHost }: PairingModalProps) {
  const [activeTab, setActiveTab] = useState<'quick' | 'tailscale' | 'cloud' | 'android'>('quick');
  const [inputHost, setInputHost] = useState(host);
  const [inputPort, setInputPort] = useState(port);
  const [copiedLink, setCopiedLink] = useState(false);
  const [copiedCmd, setCopiedCmd] = useState(false);
  const [copiedInstallCmd, setCopiedInstallCmd] = useState(false);

  // Detect current origin
  const origin = window.location.origin;
  const webPairUrl = `${origin}/?h=${encodeURIComponent(inputHost)}&p=${encodeURIComponent(inputPort)}`;
  const androidDeepLink = `shep://pair?host=${encodeURIComponent(inputHost)}&port=${encodeURIComponent(inputPort)}`;
  const bridgeCmd = `python3 bridge/herdr-bridge.py`;
  const installCmd = `curl -fsSL https://shep.work/sheperd.sh | bash`;

  const copyToClipboard = (text: string, type: 'link' | 'cmd' | 'install') => {
    navigator.clipboard.writeText(text);
    if (type === 'link') {
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    } else if (type === 'install') {
      setCopiedInstallCmd(true);
      setTimeout(() => setCopiedInstallCmd(false), 2000);
    } else {
      setCopiedCmd(true);
      setTimeout(() => setCopiedCmd(false), 2000);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      background: 'rgba(0, 0, 0, 0.65)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: '20px'
    }}>
      <div style={{
        background: 'var(--bg-surface)',
        borderRadius: '16px',
        border: '1px solid var(--border-subtle)',
        width: '100%',
        maxWidth: '560px',
        maxHeight: '90vh',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 20px 40px rgba(0, 0, 0, 0.4)'
      }}>
        {/* Modal Header */}
        <div style={{
          padding: '18px 24px',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'var(--bg-surface-high)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              background: 'rgba(217, 119, 87, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Sparkles size={18} color="var(--cds-clay)" />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: '15px' }}>Shep Setup & Pairing</div>
              <div style={{ fontSize: '11.5px', color: 'var(--text-secondary)' }}>
                Connect this interface to your workstation or cloud runner
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '6px'
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Tab Navigation */}
        <div style={{
          display: 'flex',
          borderBottom: '1px solid var(--border-subtle)',
          padding: '0 24px',
          background: 'var(--bg-surface)'
        }}>
          <button
            onClick={() => setActiveTab('quick')}
            style={{
              padding: '12px 14px',
              fontSize: '12.5px',
              fontWeight: 600,
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'quick' ? '2px solid var(--cds-clay)' : '2px solid transparent',
              color: activeTab === 'quick' ? 'var(--cds-clay)' : 'var(--text-secondary)',
              cursor: 'pointer'
            }}
          >
            1. Quick Connect
          </button>
          <button
            onClick={() => setActiveTab('cloud')}
            style={{
              padding: '12px 14px',
              fontSize: '12.5px',
              fontWeight: 600,
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'cloud' ? '2px solid var(--cds-clay)' : '2px solid transparent',
              color: activeTab === 'cloud' ? 'var(--cds-clay)' : 'var(--text-secondary)',
              cursor: 'pointer'
            }}
          >
            2. Cloud Account
          </button>
          <button
            onClick={() => setActiveTab('tailscale')}
            style={{
              padding: '12px 14px',
              fontSize: '12.5px',
              fontWeight: 600,
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'tailscale' ? '2px solid var(--cds-clay)' : '2px solid transparent',
              color: activeTab === 'tailscale' ? 'var(--cds-clay)' : 'var(--text-secondary)',
              cursor: 'pointer'
            }}
          >
            3. Tailscale
          </button>
          <button
            onClick={() => setActiveTab('android')}
            style={{
              padding: '12px 14px',
              fontSize: '12.5px',
              fontWeight: 600,
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'android' ? '2px solid var(--cds-clay)' : '2px solid transparent',
              color: activeTab === 'android' ? 'var(--cds-clay)' : 'var(--text-secondary)',
              cursor: 'pointer'
            }}
          >
            4. Android App
          </button>
        </div>

        {/* Modal Content */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
          {activeTab === 'quick' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
              {/* Step 1 */}
              <div>
                <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--cds-clay)', marginBottom: '4px' }}>
                  STEP 1: START OR INSTALL HERDR & BRIDGE
                </div>
                <div style={{ fontSize: '12.5px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
                  If you have the repository cloned, run:
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#121212',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  fontFamily: 'var(--cds-font-mono)',
                  fontSize: '12.5px',
                  color: '#e4e4e7',
                  border: '1px solid var(--border-subtle)',
                  marginBottom: '10px'
                }}>
                  <code>{bridgeCmd}</code>
                  <button
                    onClick={() => copyToClipboard(bridgeCmd, 'cmd')}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'var(--text-secondary)',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      fontSize: '11px'
                    }}
                  >
                    {copiedCmd ? <Check size={14} color="var(--cds-clay)" /> : <Copy size={14} />}
                    <span>{copiedCmd ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>

                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '6px' }}>
                  Don't have herdr yet? Run the automated 1-line installer (installs herdr + bridge):
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#121212',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  fontFamily: 'var(--cds-font-mono)',
                  fontSize: '12px',
                  color: '#38bdf8',
                  border: '1px solid var(--border-subtle)'
                }}>
                  <code>{installCmd}</code>
                  <button
                    onClick={() => copyToClipboard(installCmd, 'install')}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'var(--text-secondary)',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      fontSize: '11px'
                    }}
                  >
                    {copiedInstallCmd ? <Check size={14} color="#38bdf8" /> : <Copy size={14} />}
                    <span>{copiedInstallCmd ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
              </div>

              {/* Step 2: Configure Host & Port */}
              <div>
                <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--cds-clay)', marginBottom: '4px' }}>
                  STEP 2: WORKSTATION IP / HOSTNAME
                </div>
                <div style={{ fontSize: '12.5px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
                  Use <code>localhost</code>, your local network IP (e.g. <code>192.168.1.x</code>), or Tailscale host:
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <input
                    type="text"
                    value={inputHost}
                    onChange={e => setInputHost(e.target.value)}
                    placeholder="localhost or IP / MagicDNS"
                    style={{
                      flex: 3,
                      padding: '8px 12px',
                      borderRadius: '8px',
                      background: 'var(--bg-surface-high)',
                      border: '1px solid var(--border-subtle)',
                      color: 'var(--text-primary)',
                      fontSize: '13px',
                      fontFamily: 'var(--cds-font-mono)'
                    }}
                  />
                  <input
                    type="text"
                    value={inputPort}
                    onChange={e => setInputPort(e.target.value)}
                    placeholder="8765"
                    style={{
                      flex: 1,
                      padding: '8px 12px',
                      borderRadius: '8px',
                      background: 'var(--bg-surface-high)',
                      border: '1px solid var(--border-subtle)',
                      color: 'var(--text-primary)',
                      fontSize: '13px',
                      fontFamily: 'var(--cds-font-mono)'
                    }}
                  />
                  <button
                    onClick={() => onSaveHost(inputHost, inputPort)}
                    style={{
                      padding: '8px 16px',
                      borderRadius: '8px',
                      background: 'var(--cds-clay)',
                      color: '#fff',
                      border: 'none',
                      fontSize: '12.5px',
                      fontWeight: 600,
                      cursor: 'pointer'
                    }}
                  >
                    Apply
                  </button>
                </div>
              </div>

              {/* Step 3: 1-Click Link */}
              <div>
                <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--cds-clay)', marginBottom: '4px' }}>
                  STEP 3: 1-CLICK BOOKMARKABLE URL
                </div>
                <div style={{ fontSize: '12.5px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
                  Share this link or open it anywhere to auto-pair instantly:
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: 'var(--bg-surface-high)',
                  padding: '8px 12px',
                  borderRadius: '8px',
                  border: '1px solid var(--border-subtle)',
                  fontSize: '11.5px',
                  color: 'var(--text-primary)',
                  overflow: 'hidden'
                }}>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginRight: '8px' }}>
                    {webPairUrl}
                  </span>
                  <button
                    onClick={() => copyToClipboard(webPairUrl, 'link')}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'var(--cds-clay)',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      fontSize: '11.5px',
                      fontWeight: 600,
                      flexShrink: 0
                    }}
                  >
                    {copiedLink ? <Check size={13} /> : <Copy size={13} />}
                    <span>{copiedLink ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'cloud' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', fontSize: '13px', lineHeight: '1.6' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Cloud size={18} color="var(--cds-clay)" />
                <span style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-primary)' }}>
                  Pairing with Herdr / Shep Cloud
                </span>
              </div>

              <div style={{ color: 'var(--text-secondary)' }}>
                You can try your cloud runners and remote boxes through Shep immediately. Herdr runs headless on your cloud servers, EC2, VPS, or remote machines, and relays sessions directly to your browser.
              </div>

              <div style={{
                background: 'var(--bg-surface-high)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '10px',
                padding: '14px'
              }}>
                <div style={{ fontWeight: 600, marginBottom: '6px', color: 'var(--text-primary)' }}>
                  How to link your remote or cloud server:
                </div>
                <ol style={{ paddingLeft: '20px', margin: 0, color: 'var(--text-secondary)', fontSize: '12.5px', lineHeight: '1.6' }}>
                  <li>SSH into your cloud server or VM.</li>
                  <li>Run the 1-liner installer: <code>curl -fsSL https://shep.work/sheperd.sh | bash</code> (auto-installs herdr and bridge).</li>
                  <li>Copy the 1-click pairing URL output by the bridge (e.g. <code>https://shep.work/?h=your-cloud-ip&p=8765</code>).</li>
                  <li>Paste your cloud host / DNS into the Quick Connect tab, or open the link directly!</li>
                </ol>
              </div>

              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
                background: '#121212',
                padding: '12px 14px',
                borderRadius: '8px',
                border: '1px solid var(--border-subtle)'
              }}>
                <div style={{ fontSize: '11px', color: '#888', fontWeight: 600 }}>CONNECT VIA SSH / REMOTE HERDR</div>
                <div style={{ fontFamily: 'var(--cds-font-mono)', fontSize: '12px', color: '#e4e4e7' }}>
                  herdr --remote user@your-cloud-box
                </div>
              </div>

              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 14px',
                borderRadius: '8px',
                background: 'rgba(217, 119, 87, 0.08)',
                color: 'var(--cds-clay)',
                fontSize: '12px'
              }}>
                <CheckCircle2 size={16} style={{ flexShrink: 0 }} />
                <span>Zero vendor lock-in: work directly with your own cloud VPS, AWS, or local workstations.</span>
              </div>
            </div>
          )}

          {activeTab === 'tailscale' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '13px', lineHeight: '1.6' }}>
              <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                Zero-Configuration Remote Access with Tailscale & Tailcat
              </div>
              <div style={{ color: 'var(--text-secondary)' }}>
                Tailscale gives your devices secure encrypted mesh IPs. Tailcat lets you link directly without port forwarding or exposing your workstation to the public internet.
              </div>

              <div style={{
                background: 'var(--bg-surface-high)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '10px',
                padding: '14px'
              }}>
                <div style={{ fontWeight: 600, marginBottom: '6px' }}>How to connect via Tailscale MagicDNS:</div>
                <ol style={{ paddingLeft: '20px', margin: 0, color: 'var(--text-secondary)', fontSize: '12.5px' }}>
                  <li>Make sure Tailscale is active on your host machine and your mobile device/laptop.</li>
                  <li>Check your machine's MagicDNS name (e.g. <code>my-macbook.tailnet.ts.net</code> or <code>100.x.y.z</code>).</li>
                  <li>In the Quick Connect tab, set Host to that MagicDNS name or 100.x IP.</li>
                  <li>Cloud Shep (<code>https://shep.work</code>) will connect directly over your secure mesh tunnel!</li>
                </ol>
              </div>

              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 14px',
                borderRadius: '8px',
                background: 'rgba(217, 119, 87, 0.08)',
                color: 'var(--cds-clay)',
                fontSize: '12px'
              }}>
                <CheckCircle2 size={16} style={{ flexShrink: 0 }} />
                <span>Shep never proxies your code through any central servers — everything stays strictly peer-to-peer.</span>
              </div>
            </div>
          )}

          {activeTab === 'android' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '13px', lineHeight: '1.6' }}>
              <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                1-Tap Android App Pairing
              </div>
              <div style={{ color: 'var(--text-secondary)' }}>
                The HurrDurr Android companion app supports native deep linking. Opening the pairing link automatically configures the daemon host, port, and initiates real-time monitoring.
              </div>

              <div style={{
                background: 'var(--bg-surface-high)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '10px',
                padding: '14px'
              }}>
                <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                  ANDROID DEEP LINK
                </div>
                <div style={{
                  fontFamily: 'var(--cds-font-mono)',
                  fontSize: '12px',
                  color: 'var(--text-primary)',
                  marginBottom: '10px',
                  wordBreak: 'break-all'
                }}>
                  {androidDeepLink}
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    onClick={() => copyToClipboard(androidDeepLink, 'link')}
                    style={{
                      padding: '6px 12px',
                      borderRadius: '6px',
                      background: 'var(--cds-clay)',
                      color: '#fff',
                      border: 'none',
                      fontSize: '12px',
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '5px'
                    }}
                  >
                    {copiedLink ? <Check size={13} /> : <Copy size={13} />}
                    <span>{copiedLink ? 'Copied Deep Link' : 'Copy Deep Link'}</span>
                  </button>
                  <a
                    href={androidDeepLink}
                    style={{
                      padding: '6px 12px',
                      borderRadius: '6px',
                      background: 'transparent',
                      border: '1px solid var(--border-subtle)',
                      color: 'var(--text-primary)',
                      textDecoration: 'none',
                      fontSize: '12px',
                      fontWeight: 500,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '5px'
                    }}
                  >
                    <span>Launch on Android</span>
                    <ExternalLink size={12} />
                  </a>
                </div>
              </div>

              <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                Tip: You can send this link to your phone via Slack, Telegram, or Notes, then tap it once to connect instantly.
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer with Connection Status */}
        <div style={{
          padding: '14px 24px',
          borderTop: '1px solid var(--border-subtle)',
          background: 'var(--bg-surface-high)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12.5px' }}>
            <div style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              background: connected ? '#22c55e' : '#888'
            }} />
            <span style={{ color: connected ? '#22c55e' : 'var(--text-secondary)', fontWeight: 500 }}>
              {connected ? `Connected to ${host}:${port}` : `Disconnected (${host}:${port})`}
            </span>
          </div>
          <button
            onClick={onClose}
            style={{
              padding: '6px 14px',
              borderRadius: '6px',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontSize: '12px',
              fontWeight: 600,
              cursor: 'pointer'
            }}
          >
            Done
          </button>
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

function ToolSummaryPill({ 
  tools, 
  onInspectTool 
}: { 
  tools: string[]; 
  onInspectTool: (tool: string) => void;
}) {
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
              <div 
                key={i} 
                onClick={() => onInspectTool(t)}
                title="Click to inspect in terminal pane"
                style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'space-between',
                  gap: '8px',
                  padding: '3px 6px',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  color: isBg ? 'var(--cds-clay)' : 'var(--text-primary)',
                  transition: 'background 0.15s ease'
                }}
                onMouseEnter={e => (e.currentTarget.style.background = 'rgba(120,120,120,0.1)')}
                onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', minWidth: 0, overflow: 'hidden' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>•</span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t}</span>
                </div>
                <ExternalLink size={12} color="var(--text-secondary)" style={{ flexShrink: 0 }} />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
