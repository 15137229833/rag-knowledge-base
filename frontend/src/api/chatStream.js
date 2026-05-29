import { clearSessionAndRedirectToLogin, getToken } from './http'

/**
 * 解析 SSE：event + data（done 为 ChatStreamDone JSON：含 historyId、sessionId、sessionTitle、citations 等）
 * - ping：后端保活，忽略即可（用于穿透代理空闲断开）
 * - 断线重连：SSE 无标准 resume，中途断开需用户重新发起提问；此处仅对「尚未建立流」的 fetch 失败做有限重试
 */
export async function streamChatRequest(payload, handlers = {}, options = {}) {
  const { onToken, onDone, onError } = handlers
  const maxRetries = options.maxRetries ?? 2
  const retryDelayMs = options.retryDelayMs ?? 600

  let lastErr
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    if (attempt > 0) {
      await new Promise((r) => setTimeout(r, retryDelayMs * attempt))
    }
    try {
      await runStreamOnce(payload, { onToken, onDone, onError })
      return
    } catch (e) {
      lastErr = e
      const msg = e?.message || ''
      const retryable =
        msg === 'Network Error' ||
        msg.includes('Failed to fetch') ||
        msg.includes('network')
      if (!retryable || attempt === maxRetries) {
        break
      }
    }
  }
  throw lastErr ?? new Error('流式连接失败')
}

async function runStreamOnce(payload, { onToken, onDone, onError } = {}) {
  const token = getToken()
  if (!token || !String(token).trim()) {
    throw new Error('未登录或令牌已失效，请重新登录后再试')
  }
  const res = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token.trim()}`,
    },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    if (res.status === 401) {
      clearSessionAndRedirectToLogin()
    }
    let detail = ''
    try {
      detail = await res.text()
    } catch {
      /* ignore */
    }
    throw new Error(detail || `流式请求失败（HTTP ${res.status}）`)
  }
  const reader = res.body.getReader()
  const dec = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += dec.decode(value, { stream: true })
      let sep
      while ((sep = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        eventName = 'message'
        const dataLines = []
        for (const line of raw.split('\n')) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        const dataStr = dataLines.join('')
        if (!dataStr) continue
        if (eventName === 'ping') {
          continue
        }
        if (eventName === 'token') {
          try {
            const o = JSON.parse(dataStr)
            const c = o.c ?? o
            if (c) onToken?.(c)
          } catch {
            onToken?.(dataStr)
          }
        } else if (eventName === 'done') {
          const o = JSON.parse(dataStr)
          onDone?.(o)
        }
      }
    }
  } catch (e) {
    onError?.(e)
    throw e
  }
}

