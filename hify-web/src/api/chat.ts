/**
 * 对话引擎 API
 *
 * 会话/历史走统一 axios 封装；发消息是 SSE 流式接口，axios 不适合流式读取，
 * 故单独用 fetch + ReadableStream 逐帧解析。
 */
import { get, post, del } from '@/utils/request'
import type { ChatMessage, ChatSession } from '@/types'

// ============ 会话 CRUD ============

export function listSessions(agentId?: number) {
  return get<ChatSession[]>('/v1/chat/sessions', {
    params: agentId != null ? { agentId } : {},
  })
}

export function createSession(agentId: number, title?: string) {
  return post<ChatSession>('/v1/chat/sessions', { agentId, title })
}

export function deleteSession(id: number) {
  return del<void>(`/v1/chat/sessions/${id}`)
}

/** 游标分页历史消息（后端按 id 倒序返回，最新在前）。 */
export function listMessages(sessionId: number, beforeId?: number, size = 20) {
  return get<ChatMessage[]>(`/v1/chat/sessions/${sessionId}/messages`, {
    params: { beforeId, size },
  })
}

// ============ 流式发消息（SSE over fetch） ============

export interface StreamHandlers {
  /** 收到一个增量文本片段 */
  onChunk: (text: string) => void
  /** 收到后端 done 事件（正常完成，区别于连接中断） */
  onDone?: () => void
}

/**
 * 发送消息并消费 SSE 流。
 *
 * 协议：
 * - delta 帧：`data:"<json-string>"\n\n`，内容经 JSON 编码（换行/引号已转义），
 *   故可安全按 `\n\n` 分帧、取 `data:` 前缀、再 `JSON.parse` 还原原文；
 * - done 帧：`event:done\ndata:{}\n\n`，标记正常完成。
 *
 * @returns 流正常结束时 resolve；网络错误或被 abort 时 reject（AbortError）。
 */
export async function streamMessage(
  sessionId: number,
  content: string,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const resp = await fetch(`/api/v1/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ content, stream: true }),
    signal,
  })

  if (!resp.ok || !resp.body) {
    throw new Error(`流式请求失败：HTTP ${resp.status}`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  // 解析缓冲区里所有完整事件（以空行 \n\n 分隔）
  const drain = () => {
    let sep: number
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const rawEvent = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)

      let eventName = 'message'
      const dataLines: string[] = []
      for (const line of rawEvent.split('\n')) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5))
      }

      if (eventName === 'done') {
        handlers.onDone?.()
        continue
      }
      for (const payload of dataLines) {
        if (!payload) continue
        try {
          handlers.onChunk(JSON.parse(payload) as string)
        } catch {
          // 非 JSON 片段（理论不会出现）：当作原文兜底
          handlers.onChunk(payload)
        }
      }
    }
  }

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    drain()
  }
  // 末尾可能残留未以空行结尾的最后一帧
  buffer += decoder.decode()
  if (buffer && !buffer.endsWith('\n\n')) buffer += '\n\n'
  drain()
}
