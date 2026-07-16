import { ChevronDown, ExternalLink, RotateCcw, Send, ShoppingBag } from 'lucide-react';
import { KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';

type ApiResponse<T> = { code: string; message: string; data: T; traceId: string };
type AssistantStatus = { available: boolean };
type AssistantSku = { id: number; skuName: string; priceCents: number; vipPriceCents: number };
export type AssistantReference = {
  kind: 'WORK' | 'PRODUCT' | 'FAQ';
  key: string;
  id: number | null;
  title: string;
  subtitle: string | null;
  description: string | null;
  coverUrl: string | null;
  limited: boolean;
  skus: AssistantSku[];
  source: string | null;
};
type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  references: AssistantReference[];
  state: 'streaming' | 'complete' | 'error';
};
type SseEvent = { event: string; data: unknown };

const quickQuestions = ['怎么购买章节', '推荐一本轻小说', '搜索限定商品', 'VIP 有什么权益'];
const sourceLabels: Record<string, string> = {
  'account-and-login.md': '账号与登录',
  'reading-and-bookshelf.md': '阅读与书架',
  'points-and-vip.md': '积分与 VIP',
  'cart-and-orders.md': '购物车与订单',
  'payments.md': '支付'
};

export function AiAssistant({
  blocked,
  isVip,
  token,
  onAddProduct,
  onOpenProduct,
  onOpenWork
}: {
  blocked: boolean;
  isVip: boolean;
  token: string;
  onAddProduct: (reference: AssistantReference, sku: AssistantSku) => void | Promise<void>;
  onOpenProduct: (productId: number) => void;
  onOpenWork: (workId: number) => void;
}) {
  const [available, setAvailable] = useState(false);
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(false);
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sending, setSending] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const openRef = useRef(false);

  useEffect(() => {
    openRef.current = open;
    if (open) setUnread(false);
  }, [open]);

  useEffect(() => {
    let active = true;
    setAvailable(false);
    void fetch('/api/v1/assistant/status', {
      credentials: 'include',
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const body = (await response.json()) as ApiResponse<AssistantStatus>;
        return body.data.available;
      })
      .then((enabled) => active && setAvailable(enabled))
      .catch(() => active && setAvailable(false));
    return () => {
      active = false;
      abortRef.current?.abort();
    };
  }, [token]);

  useEffect(() => {
    if (blocked) setOpen(false);
  }, [blocked]);

  useEffect(() => {
    if (open) window.setTimeout(() => inputRef.current?.focus(), 100);
  }, [open]);

  useEffect(() => {
    const list = messageListRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages]);

  const history = useMemo(
    () => messages
      .filter((message) => message.state === 'complete' && message.content.trim())
      .slice(-20)
      .map(({ role, content }) => ({ role, content })),
    [messages]
  );

  if (!available) return null;

  async function sendMessage(value: string) {
    const question = value.trim();
    if (!question || sending) return;
    const userId = crypto.randomUUID();
    const assistantId = crypto.randomUUID();
    setDraft('');
    setSending(true);
    setMessages((current) => [
      ...current,
      { id: userId, role: 'user', content: question, references: [], state: 'complete' },
      { id: assistantId, role: 'assistant', content: '', references: [], state: 'streaming' }
    ]);

    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const response = await fetch('/api/v1/assistant/messages', {
        method: 'POST',
        credentials: 'include',
        signal: controller.signal,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          Accept: 'text/event-stream'
        },
        body: JSON.stringify({ message: question, history })
      });
      if (!response.ok || !response.body) {
        throw new Error(await responseMessage(response));
      }
      await readEventStream(response.body, (event) => {
        if (event.event === 'delta') {
          const text = (event.data as { text?: string }).text ?? '';
          setMessages((current) => updateAssistant(current, assistantId, (message) => ({
            ...message,
            content: message.content + text
          })));
        }
        if (event.event === 'references') {
          const references = (event.data as { items?: AssistantReference[] }).items ?? [];
          setMessages((current) => updateAssistant(current, assistantId, (message) => ({ ...message, references })));
        }
        if (event.event === 'error') {
          const error = event.data as { message?: string };
          throw new Error(error.message || '客服暂时不可用，请稍后再试');
        }
        if (event.event === 'done') {
          setMessages((current) => updateAssistant(current, assistantId, (message) => ({
            ...message,
            content: message.content || '暂时没有找到可靠答案。',
            state: 'complete'
          })));
          if (!openRef.current) setUnread(true);
        }
      });
    } catch (error) {
      if (controller.signal.aborted) return;
      const message = error instanceof Error ? error.message : '客服暂时不可用，请稍后再试';
      setMessages((current) => updateAssistant(current, assistantId, (item) => ({
        ...item,
        content: item.content || message,
        state: 'error'
      })));
      if (!openRef.current) setUnread(true);
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      setSending(false);
    }
  }

  function resetConversation() {
    if (sending) return;
    setMessages([]);
    setDraft('');
    setUnread(false);
    inputRef.current?.focus();
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void sendMessage(draft);
    }
  }

  return (
    <aside className={`aiAssistant ${open ? 'isOpen' : ''}`} aria-label="AI 客服">
      {open && (
        <section className="aiPanel" role="dialog" aria-modal="false" aria-labelledby="ai-assistant-title">
          <header className="aiPanelHeader">
            <img src="/ai-assistant-avatar.png" alt="" />
            <div>
              <h2 id="ai-assistant-title">AI 客服</h2>
              <span><i /> 在线</span>
            </div>
            <div className="aiHeaderActions">
              {messages.length > 0 && (
                <button type="button" aria-label="重新开始对话" title="重新开始" disabled={sending} onClick={resetConversation}>
                  <RotateCcw size={17} />
                </button>
              )}
              <button type="button" aria-label="收起 AI 客服" title="收起" onClick={() => setOpen(false)}>
                <ChevronDown size={19} />
              </button>
            </div>
          </header>

          <div className="aiMessages" ref={messageListRef} aria-live="polite">
            {messages.length === 0 ? (
              <div className="aiWelcome">
                <strong>想找什么？</strong>
                <p>可以问站内功能，也可以让我帮你找书或商品。</p>
                <div className="aiQuickQuestions">
                  {quickQuestions.map((question) => (
                    <button type="button" key={question} onClick={() => void sendMessage(question)}>{question}</button>
                  ))}
                </div>
              </div>
            ) : (
              messages.map((message) => (
                <article className={`aiMessage ${message.role} ${message.state}`} key={message.id}>
                  <div className="aiMessageBubble">
                    {message.content || <span className="aiTyping"><i /><i /><i /></span>}
                  </div>
                  {message.references.length > 0 && (
                    <AssistantReferences
                      isVip={isVip}
                      references={message.references}
                      onAddProduct={onAddProduct}
                      onOpenProduct={(productId) => {
                        setOpen(false);
                        onOpenProduct(productId);
                      }}
                      onOpenWork={(workId) => {
                        setOpen(false);
                        onOpenWork(workId);
                      }}
                    />
                  )}
                </article>
              ))
            )}
          </div>

          <footer className="aiComposer">
            <textarea
              ref={inputRef}
              rows={1}
              maxLength={500}
              value={draft}
              disabled={sending}
              aria-label="向 AI 客服提问"
              placeholder={sending ? '正在回复...' : '问问客服'}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleInputKeyDown}
            />
            <button type="button" aria-label="发送" disabled={!draft.trim() || sending} onClick={() => void sendMessage(draft)}>
              <Send size={18} />
            </button>
          </footer>
        </section>
      )}

      <button
        className="aiOrb"
        type="button"
        aria-label={open ? '收起 AI 客服' : '打开 AI 客服'}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <img src="/ai-assistant-avatar.png" alt="" />
        <span className="aiOrbStatus" />
        {unread && <span className="aiUnread" />}
      </button>
    </aside>
  );
}

function AssistantReferences({
  isVip,
  references,
  onAddProduct,
  onOpenProduct,
  onOpenWork
}: {
  isVip: boolean;
  references: AssistantReference[];
  onAddProduct: (reference: AssistantReference, sku: AssistantSku) => void | Promise<void>;
  onOpenProduct: (productId: number) => void;
  onOpenWork: (workId: number) => void;
}) {
  const catalog = references.filter((item) => item.kind !== 'FAQ');
  const sources = references.filter((item) => item.kind === 'FAQ');
  return (
    <div className="aiReferences">
      {catalog.map((item) => {
        const sku = item.skus[0];
        const price = sku ? (isVip ? sku.vipPriceCents : sku.priceCents) : null;
        return (
          <section className="aiReferenceCard" key={item.key}>
            {item.coverUrl && <img src={item.coverUrl} alt={item.title} />}
            <div>
              <small>{item.kind === 'WORK' ? item.subtitle : `${item.subtitle ?? '商品'}${item.limited ? ' · 限定' : ''}`}</small>
              <strong>{item.title}</strong>
              {price !== null && (
                <span className="aiReferencePrice">
                  {isVip && sku.vipPriceCents < sku.priceCents && <del>{money(sku.priceCents)}</del>}
                  <b>{money(price)}</b>
                </span>
              )}
              <div className="aiReferenceActions">
                <button type="button" onClick={() => item.id && (item.kind === 'WORK' ? onOpenWork(item.id) : onOpenProduct(item.id))}>
                  <ExternalLink size={14} /> 查看
                </button>
                {item.kind === 'PRODUCT' && item.skus.length === 1 && (
                  <button type="button" onClick={() => void onAddProduct(item, item.skus[0])}>
                    <ShoppingBag size={14} /> 加入购物车
                  </button>
                )}
              </div>
            </div>
          </section>
        );
      })}
      {sources.length > 0 && (
        <p className="aiSources">参考：{sources.map((item) => sourceLabels[item.source ?? ''] ?? item.title).join('、')}</p>
      )}
    </div>
  );
}

function updateAssistant(messages: ChatMessage[], id: string, update: (message: ChatMessage) => ChatMessage) {
  return messages.map((message) => message.id === id ? update(message) : message);
}

async function responseMessage(response: Response) {
  try {
    const body = (await response.json()) as Partial<ApiResponse<unknown>>;
    return body.message || '客服暂时不可用，请稍后再试';
  } catch {
    return '客服暂时不可用，请稍后再试';
  }
}

async function readEventStream(stream: ReadableStream<Uint8Array>, onEvent: (event: SseEvent) => void) {
  const reader = stream.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? '';
    for (const block of blocks) {
      const parsed = parseEvent(block);
      if (parsed) onEvent(parsed);
    }
    if (done) break;
  }
  if (buffer.trim()) {
    const parsed = parseEvent(buffer);
    if (parsed) onEvent(parsed);
  }
}

function parseEvent(block: string): SseEvent | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  }
  if (data.length === 0) return null;
  try {
    return { event, data: JSON.parse(data.join('\n')) as unknown };
  } catch {
    return { event, data: data.join('\n') };
  }
}

function money(cents: number) {
  return `¥${(cents / 100).toFixed(2)}`;
}
