<template>
  <div class="chat-page">
    <!-- ══════════ 左侧：会话列表 ══════════ -->
    <aside class="session-pane">
      <div class="session-head">
        <span class="session-head-title">会话</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openNewDialog">
          新对话
        </el-button>
      </div>

      <div class="session-list">
        <el-empty
          v-if="!sessions.length"
          description="还没有会话，点「新对话」开始"
          :image-size="64"
        />
        <button
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ 'is-active': s.id === activeId }"
          @click="selectSession(s.id)"
        >
          <el-icon class="session-item-icon"><ChatLineRound /></el-icon>
          <div class="session-item-body">
            <div class="session-item-title">{{ s.title || '新对话' }}</div>
            <div class="session-item-time">{{ relativeTime(s.updatedAt) }}</div>
          </div>
          <el-icon class="session-item-del" title="删除会话" @click.stop="removeSession(s)">
            <Delete />
          </el-icon>
        </button>
      </div>
    </aside>

    <!-- ══════════ 右侧：聊天窗口 ══════════ -->
    <section class="chat-pane">
      <template v-if="activeSession">
        <header class="chat-head">
          <div class="chat-head-title">{{ activeSession.title || '新对话' }}</div>
          <div class="chat-head-sub">{{ agentName(activeSession.agentId) }}</div>
        </header>

        <!-- 消息区 -->
        <div ref="scrollRef" class="msg-scroll" @scroll="onScroll">
          <el-empty
            v-if="!messages.length && !loadingHistory"
            description="开始你们的第一句对话吧"
            :image-size="72"
          />
          <div
            v-for="m in messages"
            :key="m.key"
            class="msg-row"
            :class="m.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <div class="msg-avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
            <div class="msg-bubble">
              <template v-if="m.role === 'assistant'">
                <!-- 空 AI 气泡：加载动画（首个 delta 到达前） -->
                <div v-if="m.streaming && !m.content" class="typing-dots">
                  <span></span><span></span><span></span>
                </div>
                <!-- 有内容：Markdown 正文 + 打字机光标 -->
                <template v-else>
                  <div class="markdown-body" v-html="m.html"></div>
                  <span v-if="m.streaming" class="type-caret"></span>
                </template>
              </template>
              <div v-else class="msg-plain">{{ m.content }}</div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <footer class="composer">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            resize="none"
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            :disabled="streaming"
            @keydown="onKeydown"
          />
          <el-button
            v-if="!streaming"
            type="primary"
            :icon="Promotion"
            :disabled="!draft.trim()"
            @click="send"
          >
            发送
          </el-button>
          <el-button v-else type="danger" :icon="CircleClose" @click="stop">
            停止
          </el-button>
        </footer>
      </template>

      <el-empty v-else description="选择左侧会话，或新建一个开始对话" :image-size="96" />
    </section>

    <!-- 新对话：选择 Agent -->
    <el-dialog v-model="newDialog" title="新对话" width="420px" align-center>
      <el-form label-width="72px">
        <el-form-item label="选择 Agent">
          <el-select
            v-model="newAgentId"
            placeholder="请选择一个 Agent"
            style="width: 100%"
            :loading="agentsLoading"
          >
            <el-option
              v-for="a in agents"
              :key="a.id"
              :label="a.name"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!newAgentId" @click="confirmNew">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import {
  ChatLineRound,
  CircleClose,
  Delete,
  Plus,
  Promotion,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AgentConfig, ChatSession } from '@/types'
import { getAgentList } from '@/api/agent'
import {
  createSession,
  deleteSession,
  listMessages,
  listSessions,
  streamMessage,
} from '@/api/chat'
import { renderMarkdown } from '@/utils/markdown'

/** 视图层消息模型：content 为已显示文本（打字机逐步揭示），html 为其 Markdown 渲染结果。 */
interface ViewMessage {
  key: string
  role: 'user' | 'assistant'
  content: string
  html: string
  streaming: boolean
}

const sessions = ref<ChatSession[]>([])
const activeId = ref<number | null>(null)
const activeSession = ref<ChatSession | null>(null)
const messages = ref<ViewMessage[]>([])
const loadingHistory = ref(false)

const draft = ref('')
const streaming = ref(false)
let abort: AbortController | null = null

const agents = ref<AgentConfig[]>([])
const agentsLoading = ref(false)
const newDialog = ref(false)
const newAgentId = ref<number | null>(null)

const scrollRef = ref<HTMLElement | null>(null)
// 是否「黏底」：用户停留在底部时自动滚动；向上翻看历史时不打扰
let stick = true

let keySeq = 0
const nextKey = () => `m${Date.now()}_${keySeq++}`

// ──────────────────────────── 初始化 ────────────────────────────

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length) selectSession(sessions.value[0].id)
})

async function loadSessions() {
  sessions.value = (await listSessions()) ?? []
}

function agentName(agentId: number): string {
  return agents.value.find((a) => a.id === agentId)?.name ?? `Agent #${agentId}`
}

/** 标题是否仍为「未命名」状态（空 / 默认占位），用于决定是否自动生成。 */
function isDefaultTitle(title?: string): boolean {
  return !title || title === '新对话'
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 日期。 */
function relativeTime(iso?: string): string {
  if (!iso) return ''
  const t = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return ''
  const diff = Date.now() - t
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  return new Date(t).toLocaleDateString()
}

// ──────────────────────────── 新对话 ────────────────────────────

async function openNewDialog() {
  newDialog.value = true
  newAgentId.value = null
  if (agents.value.length) return
  agentsLoading.value = true
  try {
    const res = await getAgentList({ page: 1, size: 100 })
    agents.value = (res.records ?? []).filter((a) => a.enabled === 1)
  } finally {
    agentsLoading.value = false
  }
}

async function confirmNew() {
  if (!newAgentId.value) return
  const session = await createSession(newAgentId.value)
  sessions.value.unshift(session)
  newDialog.value = false
  await selectSession(session.id)
}

// ──────────────────────────── 选择 / 删除会话 ────────────────────────────

async function selectSession(id: number) {
  if (streaming.value) return
  activeId.value = id
  activeSession.value = sessions.value.find((s) => s.id === id) ?? null
  await loadHistory(id)
}

async function loadHistory(id: number) {
  loadingHistory.value = true
  try {
    // 后端按 id 倒序返回（最新在前），翻转为时间正序展示
    const list = (await listMessages(id, undefined, 50)) ?? []
    messages.value = list
      .filter((m) => m.role === 'user' || m.role === 'assistant')
      .reverse()
      .map((m) => ({
        key: nextKey(),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        html: m.role === 'assistant' ? renderMarkdown(m.content) : '',
        streaming: false,
      }))
    stick = true
    await scrollToBottom()
  } finally {
    loadingHistory.value = false
  }
}

async function removeSession(s: ChatSession) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${s.title || '新对话'}」？`, '删除会话', {
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteSession(s.id)
  sessions.value = sessions.value.filter((x) => x.id !== s.id)
  if (activeId.value === s.id) {
    activeId.value = null
    activeSession.value = null
    messages.value = []
  }
  ElMessage.success('已删除')
}

// ──────────────────────────── 发送 / 流式接收 ────────────────────────────

function onKeydown(evt: Event | KeyboardEvent) {
  const e = evt as KeyboardEvent
  // Enter 发送，Shift+Enter 换行；输入法组合中（isComposing）不拦截
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text || streaming.value || activeId.value == null) return
  const sessionId = activeId.value
  const isFirstTurn = messages.value.length === 0

  draft.value = ''
  // 首轮：用前 20 字乐观更新标题，界面立刻生效（后端会落库同样的标题）
  if (isFirstTurn && activeSession.value && isDefaultTitle(activeSession.value.title)) {
    activeSession.value.title = text.length > 20 ? text.slice(0, 20) : text
  }
  messages.value.push({
    key: nextKey(),
    role: 'user',
    content: text,
    html: '',
    streaming: false,
  })
  const reply: ViewMessage = {
    key: nextKey(),
    role: 'assistant',
    content: '',
    html: '',
    streaming: true,
  }
  messages.value.push(reply)
  // 关键：push 后必须拿数组里的「响应式代理」来改，直接改原始 reply 对象不会触发渲染
  // （表现就是流式期间界面不动，直到最后一次响应式变更才整段刷出来）
  const view = messages.value[messages.value.length - 1]

  stick = true
  await scrollToBottom()

  streaming.value = true
  abort = new AbortController()

  // 打字机：收到的原文累积到 full，按帧把字符揭示到 view.content。
  // step 取「最小 2 字 / 帧」保证稳定的逐字打印手感；落后较多时按比例提速追赶，避免长回复拖太久。
  let full = ''
  let revealing = false
  const reveal = () => {
    if (view.content.length < full.length) {
      const remaining = full.length - view.content.length
      const step = Math.max(2, Math.ceil(remaining / 40))
      view.content = full.slice(0, view.content.length + step)
      view.html = renderMarkdown(view.content)
      scrollIfStick()
      requestAnimationFrame(reveal)
    } else {
      revealing = false
      if (!view.streaming) {
        view.html = renderMarkdown(view.content)
        scrollIfStick()
      }
    }
  }
  const kick = () => {
    if (!revealing) {
      revealing = true
      requestAnimationFrame(reveal)
    }
  }

  try {
    let serverDone = false
    await streamMessage(
      sessionId,
      text,
      {
        onChunk: (t) => { full += t; kick() },
        onDone: () => { serverDone = true },
      },
      abort.signal,
    )
    // 流结束但没收到 done 事件：连接被中途切断（后端已落库部分回复）
    if (!serverDone && !full) {
      view.content = '⚠️ 连接中断，未能生成回复。'
    }
  } catch (e) {
    const err = e as Error
    if (err.name !== 'AbortError') {
      view.content = full || '⚠️ 回复失败，请稍后重试。'
      ElMessage.error(err.message || '对话出错')
    }
  } finally {
    view.streaming = false // 移除加载动画 / 打字机光标
    kick() // 冲刷剩余字符并定稿渲染
    streaming.value = false // 恢复发送按钮
    abort = null
    await refreshSessionMeta(sessionId)
  }
}

function stop() {
  abort?.abort()
}

/** 发送后刷新会话标题与排序（首条消息后端会自动生成标题）。 */
async function refreshSessionMeta(sessionId: number) {
  await loadSessions()
  activeSession.value = sessions.value.find((s) => s.id === sessionId) ?? activeSession.value
}

// ──────────────────────────── 滚动 ────────────────────────────

function onScroll() {
  const el = scrollRef.value
  if (!el) return
  stick = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

function scrollIfStick() {
  if (stick) scrollToBottom()
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  background: var(--color-bg-secondary);
  overflow: hidden;
}

/* ════════ 左侧会话列表 ════════ */
.session-pane {
  width: 264px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
  border-right: 1px solid var(--border-light);
}
.session-head {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--border-light);
}
.session-head-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semi);
  color: var(--text-primary);
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-2);
}
.session-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  margin-bottom: 2px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background var(--duration-fast) var(--ease-standard);
}
.session-item:hover {
  background: var(--bg-subtle);
}
.session-item.is-active {
  background: var(--brand-50);
}
.session-item-icon {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--text-tertiary);
}
.session-item.is-active .session-item-icon {
  color: var(--brand-500);
}
.session-item-body {
  flex: 1;
  min-width: 0;
}
.session-item-title {
  font-size: var(--text-sm);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item-time {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}
.session-item-del {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--text-tertiary);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-standard);
}
.session-item:hover .session-item-del {
  opacity: 1;
}
.session-item-del:hover {
  color: var(--color-danger);
}

/* ════════ 右侧聊天窗口 ════════ */
.chat-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.chat-head {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 var(--space-6);
  border-bottom: 1px solid var(--border-light);
  background: var(--color-bg-primary);
}
.chat-head-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semi);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chat-head-sub {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.msg-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.msg-row {
  display: flex;
  gap: var(--space-3);
  max-width: 760px;
}
.msg-row.is-user {
  flex-direction: row-reverse;
  align-self: flex-end;
}
.msg-row.is-assistant {
  align-self: flex-start;
}
.msg-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--weight-semi);
  color: #fff;
}
.is-user .msg-avatar {
  background: var(--brand-500);
}
.is-assistant .msg-avatar {
  background: var(--cyan-600);
}
.msg-bubble {
  position: relative;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  word-break: break-word;
}
.is-user .msg-bubble {
  background: var(--brand-500);
  color: #fff;
  border-top-right-radius: var(--radius-xs);
}
.is-assistant .msg-bubble {
  background: var(--color-bg-primary);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-top-left-radius: var(--radius-xs);
}
.msg-plain {
  white-space: pre-wrap;
}

/* 空 AI 气泡加载动画（三点跳动） */
.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 1.2em;
}
.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--text-tertiary);
  animation: typing-bounce 1.2s infinite ease-in-out both;
}
.typing-dots span:nth-child(1) {
  animation-delay: -0.24s;
}
.typing-dots span:nth-child(2) {
  animation-delay: -0.12s;
}
@keyframes typing-bounce {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 打字机光标 */
.type-caret {
  display: inline-block;
  width: 7px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--brand-400);
  border-radius: 1px;
  animation: caret-blink 1s steps(2, start) infinite;
}
@keyframes caret-blink {
  to {
    visibility: hidden;
  }
}

/* ════════ 输入区 ════════ */
.composer {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--border-light);
  background: var(--color-bg-primary);
}
.composer :deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
}

/* ════════ Markdown 正文样式 ════════ */
.markdown-body :deep(p) {
  margin: 0 0 0.5em;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(pre) {
  margin: 0.5em 0;
  padding: var(--space-3);
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}
.markdown-body :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.92em;
  background: var(--bg-muted);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
}
.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.4em;
}
.markdown-body :deep(blockquote) {
  margin: 0.5em 0;
  padding-left: var(--space-3);
  border-left: 3px solid var(--border-strong);
  color: var(--text-secondary);
}
.markdown-body :deep(a) {
  color: var(--brand-500);
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.5em 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border-base);
  padding: 4px 8px;
}
</style>
