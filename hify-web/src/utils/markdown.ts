/**
 * Markdown 渲染：marked 解析 + DOMPurify 净化，输出可安全 v-html 的 HTML。
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: true, // 单换行也渲染为 <br>，更贴合聊天习惯
})

export function renderMarkdown(text: string): string {
  if (!text) return ''
  const raw = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(raw)
}
