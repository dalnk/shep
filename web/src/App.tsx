import React, { useState, useEffect } from 'react';
import { 
  Sparkles, 
  Send, 
  Zap, 
  ChevronRight, 
  ChevronDown, 
  Wifi,
  WifiOff
} from 'lucide-react';

interface Pane {
  id: string;
  title: string;
  agentName: string;
  state: 'WORKING' | 'BLOCKED' | 'DONE' | 'IDLE';
  waitingDurationSeconds?: number;
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
  const [panes, setPanes] = useState<Pane[]>([
    { id: '1', title: 'hurrdurr · agy', agentName: 'agy', state: 'WORKING' },
    { id: '2', title: 'daybreak · claude', agentName: 'claude', state: 'DONE' },
    { id: '3', title: 'underclass · under', agentName: 'under', state: 'BLOCKED', waitingDurationSeconds: 120 }
  ]);
  const [selectedPaneId, setSelectedPaneId] = useState<string>('1');
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'm1',
      sender: 'user',
      text: 'Also rendering the 111 tool calls as a full width grey thing is not really my style. Can we make it minimal like Claude desktop?',
      timestamp: Date.now() - 60000
    },
    {
      id: 'm2',
      sender: 'assistant',
      text: "I've updated the tool call indicator to match Claude Desktop's minimal layout. Tool executions now appear as an unobtrusive inline summary with collapsible monospace details on demand.",
      thinking: "Audit unpacked Vite bundles from /Applications/Claude.app/Contents/Resources/app.asar. Extract CSS tokens and tool summary structures.",
      tools: [
        'run_command: which npx',
        'run_command: npx asar extract /Applications/Claude.app/...',
        'view_file: /tmp/claude_unpack/.../MainWindowPage.css',
        'replace_file_content: ChatScreen.kt'
      ],
      timestamp: Date.now() - 30000
    }
  ]);
  const [input, setInput] = useState('');
  const [ws, setWs] = useState<WebSocket | null>(null);

  // Connect to local Herdr Daemon WebSocket
  useEffect(() => {
    const wsUrl = `ws://${host}:${port}`;
    const socket = new WebSocket(wsUrl);

    socket.onopen = () => {
      setConnected(true);
      socket.send(JSON.stringify({ method: 'workspace.list', id: '1' }));
    };

    socket.onclose = () => setConnected(false);
    socket.onerror = () => setConnected(false);

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.workspaces) {
          const extractedPanes: Pane[] = [];
          for (const ws of data.workspaces) {
            for (const tab of ws.tabs || []) {
              for (const p of tab.panes || []) {
                extractedPanes.push({
                  id: p.pane_id,
                  title: p.title || `Pane ${p.pane_id}`,
                  agentName: p.agent || 'agent',
                  state: p.agent_status === 'WORKING' ? 'WORKING' : p.agent_status === 'BLOCKED' ? 'BLOCKED' : p.agent_status === 'DONE' ? 'DONE' : 'IDLE'
                });
              }
            }
          }
          if (extractedPanes.length > 0) {
            setPanes(extractedPanes);
          }
        }
      } catch (err) {
        console.error('Failed to parse herdr frame', err);
      }
    };

    setWs(socket);
    return () => socket.close();
  }, [host, port]);

  const handleSend = () => {
    if (!input.trim()) return;
    const userMsg: Message = {
      id: String(Date.now()),
      sender: 'user',
      text: input,
      timestamp: Date.now()
    };
    setMessages(prev => [...prev, userMsg]);
    setInput('');

    if (ws && connected && selectedPaneId) {
      ws.send(JSON.stringify({
        method: 'pane.send_input',
        params: { pane_id: selectedPaneId, input: input + '\n' }
      }));
    }
  };

  const selectedPane = panes.find(p => p.id === selectedPaneId) || panes[0];

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
              color: 'var(--text-secondary)'
            }}>WebUX</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px' }}>
            {connected ? (
              <Wifi size={16} color="var(--cds-clay)" />
            ) : (
              <WifiOff size={16} color="#888" />
            )}
          </div>
        </div>

        {/* Panes list */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px' }}>
          <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px', paddingLeft: '8px' }}>
            ACTIVE AGENTS ({panes.length})
          </div>
          {panes.map(pane => {
            const isSelected = pane.id === selectedPaneId;
            return (
              <div
                key={pane.id}
                onClick={() => setSelectedPaneId(pane.id)}
                style={{
                  padding: '10px 12px',
                  borderRadius: '10px',
                  cursor: 'pointer',
                  marginBottom: '4px',
                  background: isSelected ? 'var(--bg-surface-high)' : 'transparent',
                  border: isSelected ? '1px solid var(--border-subtle)' : '1px solid transparent',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px'
                }}
              >
                <div style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  background: isSelected ? 'var(--cds-clay)' : 'var(--bg-surface-high)',
                  color: isSelected ? '#fff' : 'var(--text-secondary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '13px'
                }}>
                  {pane.agentName.slice(0, 1).toUpperCase()}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '13px', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {pane.title}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                    {pane.agentName} · {pane.state.toLowerCase()}
                  </div>
                </div>
                {pane.state === 'WORKING' && (
                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--cds-clay)' }} />
                )}
              </div>
            );
          })}
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
              Connected to local session via herdr
            </div>
          </div>
        </div>

        {/* Message Canvas */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '32px 0',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center'
        }}>
          <div style={{ width: '100%', maxWidth: '740px', padding: '0 24px' }}>
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
                      <span style={{ fontWeight: 600, fontSize: '13px' }}>Gemini / AGY</span>
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
                    <div style={{
                      fontSize: '14.5px',
                      lineHeight: '1.6',
                      marginTop: '10px'
                    }}>
                      {msg.text}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
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
      const cat = t.includes('run_command') ? 'commands' :
                  t.includes('view_file') ? 'read files' :
                  t.includes('replace_file_content') ? 'edits' : 'tools';
      counts[cat] = (counts[cat] || 0) + 1;
    }
    const parts = Object.entries(counts).map(([k, v]) => `${v} ${k}`);
    return `${tools.length} tool calls (${parts.join(', ')})`;
  }, [tools]);

  return (
    <div style={{ marginBottom: '8px' }}>
      <button
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '5px',
          fontSize: '12px',
          color: 'var(--text-secondary)',
          padding: '2px 8px',
          borderRadius: '8px',
          background: 'var(--bg-surface-high)'
        }}
      >
        <Zap size={12} color="var(--cds-clay)" />
        <span>{summary}</span>
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
      </button>
      {expanded && (
        <div style={{
          marginTop: '6px',
          padding: '8px 12px',
          borderRadius: '8px',
          background: 'var(--bg-surface-high)',
          fontFamily: 'var(--cds-font-mono)',
          fontSize: '11.5px',
          lineHeight: '1.6',
          color: 'var(--text-primary)'
        }}>
          {tools.map((t, i) => (
            <div key={i}>{t}</div>
          ))}
        </div>
      )}
    </div>
  );
}
