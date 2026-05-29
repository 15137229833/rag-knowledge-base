import katex from 'katex'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { marked, Renderer } from 'marked'
import mermaid from 'mermaid'

class RichRenderer extends Renderer {
  code({ text, lang }) {
    const l = (lang || '').trim().toLowerCase()
    if (l === 'mermaid') {
      const esc = String(text || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
      return `<pre class="mermaid">${esc}</pre>\n`
    }
    const raw = String(text || '').replace(/\n$/, '') + '\n'
    let hl
    if (lang && hljs.getLanguage(lang)) {
      try {
        hl = hljs.highlight(raw, { language: lang }).value
      } catch {
        hl = hljs.highlightAuto(raw).value
      }
    } else {
      hl = hljs.highlightAuto(raw).value
    }
    const slug = (lang || '').match(/^\S*/)?.[0] || ''
    const langClass = slug ? `language-${slug}` : ''
    return `<pre><code class="hljs ${langClass}">${hl}</code></pre>\n`
  }
}

marked.use({
  gfm: true,
  breaks: true,
  renderer: new RichRenderer(),
})

const PURIFY = {
  USE_PROFILES: { html: true },
  ADD_TAGS: [
    'math',
    'semantics',
    'mrow',
    'mi',
    'mo',
    'mn',
    'msup',
    'msub',
    'mfrac',
    'msqrt',
    'mtext',
    'mspace',
    'menclose',
    'annotation',
    'svg',
    'path',
    'g',
    'line',
    'rect',
    'circle',
    'ellipse',
    'defs',
    'use',
    'marker',
    'foreignObject',
  ],
  ADD_ATTR: [
    'class',
    'style',
    'xmlns',
    'width',
    'height',
    'viewBox',
    'preserveAspectRatio',
    'd',
    'fill',
    'stroke',
    'transform',
    'aria-hidden',
    'role',
    'tabindex',
    'x',
    'y',
    'x1',
    'y1',
    'x2',
    'y2',
    'points',
    'rx',
    'ry',
    'cx',
    'cy',
    'r',
    'stroke-width',
    'stroke-linecap',
    'stroke-linejoin',
    'opacity',
    'font-size',
    'text-anchor',
    'dominant-baseline',
    'marker-end',
    'marker-start',
    'marker-mid',
    'overflow',
  ],
}

function splitByFences(src) {
  const parts = []
  let last = 0
  const re = /^```([\w-]*)\s*\n([\s\S]*?)^```/gm
  let m
  while ((m = re.exec(src)) !== null) {
    if (m.index > last) {
      parts.push({ type: 'text', value: src.slice(last, m.index) })
    }
    parts.push({ type: 'fence', lang: m[1] || '', value: m[2] ?? '' })
    last = re.lastIndex
  }
  if (last < src.length) {
    parts.push({ type: 'text', value: src.slice(last) })
  }
  if (parts.length === 0) {
    parts.push({ type: 'text', value: src })
  }
  return parts
}

function katexOnText(text) {
  let t = text
  t = t.replace(/\\\[([\s\S]+?)\\\]/g, (_, expr) => {
    try {
      return katex.renderToString(expr.trim(), {
        displayMode: true,
        throwOnError: false,
        strict: 'ignore',
      })
    } catch {
      return `\\[${expr}\\]`
    }
  })
  t = t.replace(/\\\(([\s\S]+?)\\\)/g, (_, expr) => {
    try {
      return katex.renderToString(expr.trim(), {
        displayMode: false,
        throwOnError: false,
        strict: 'ignore',
      })
    } catch {
      return `\\(${expr}\\)`
    }
  })
  t = t.replace(/\$\$([\s\S]+?)\$\$/g, (_, expr) => {
    try {
      return katex.renderToString(expr.trim(), {
        displayMode: true,
        throwOnError: false,
        strict: 'ignore',
      })
    } catch {
      return `$$${expr}$$`
    }
  })
  t = t.replace(/(?<!\$)\$(?!\$)([^\n$]+?)\$(?!\$)/g, (full, expr) => {
    if (!String(expr).trim()) {
      return full
    }
    try {
      return katex.renderToString(String(expr).trim(), {
        displayMode: false,
        throwOnError: false,
        strict: 'ignore',
      })
    } catch {
      return full
    }
  })
  return t
}

export function markdownToSafeHtml(markdown) {
  if (!markdown) {
    return ''
  }
  const parts = splitByFences(markdown)
  const rebuilt = parts
    .map((p) => {
      if (p.type === 'fence') {
        const lang = p.lang || ''
        return `\n\`\`\`${lang}\n${p.value}\n\`\`\`\n`
      }
      return katexOnText(p.value)
    })
    .join('')
  const rawHtml = marked.parse(rebuilt, { async: false })
  return DOMPurify.sanitize(String(rawHtml), PURIFY)
}

export function initMermaidTheme(isDark) {
  mermaid.initialize({
    startOnLoad: false,
    theme: isDark ? 'dark' : 'default',
    securityLevel: 'strict',
    fontFamily: 'inherit',
  })
}

export async function runMermaidInContainer(rootEl) {
  if (!rootEl) {
    return
  }
  const nodes = rootEl.querySelectorAll('pre.mermaid')
  if (!nodes.length) {
    return
  }
  try {
    await mermaid.run({ nodes: [...nodes], suppressErrors: true })
  } catch {
    /* 单图语法错误时仍展示其余内容 */
  }
}
