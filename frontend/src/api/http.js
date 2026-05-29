import axios from 'axios'

const TOKEN_KEY = 'rag_token'
const PROFILE_KEY = 'rag_profile'

/** 规范化本地 JWT，避免复制进引号、前后空格或误存了 Bearer 前缀导致校验失败 */
export function getToken() {
  try {
    const s = localStorage.getItem(TOKEN_KEY)
    if (s == null || s === '') return null
    let t = String(s).trim()
    if (
      (t.startsWith('"') && t.endsWith('"')) ||
      (t.startsWith("'") && t.endsWith("'"))
    ) {
      t = t.slice(1, -1).trim()
    }
    const lower = t.toLowerCase()
    if (lower.startsWith('bearer ')) t = t.slice(7).trim()
    return t || null
  } catch {
    return null
  }
}

/** 会话失效时清除本地状态并跳转登录（整页刷新，避免路由与 axios 循环依赖） */
export function clearSessionAndRedirectToLogin() {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  } catch {
    /* ignore */
  }
  const p = window.location.pathname
  if (p.startsWith('/login') || p.startsWith('/register')) return
  const q = window.location.pathname + window.location.search
  window.location.assign(`/login?redirect=${encodeURIComponent(q)}`)
}

/** @param {object | null} [profile] 登录接口返回的 user，用于前端展示角色等 */
export function setToken(t, profile) {
  if (t) localStorage.setItem(TOKEN_KEY, t)
  else localStorage.removeItem(TOKEN_KEY)
  if (profile) localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
  else localStorage.removeItem(PROFILE_KEY)
}

export function getProfile() {
  try {
    const s = localStorage.getItem(PROFILE_KEY)
    return s ? JSON.parse(s) : null
  } catch {
    return null
  }
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 120000,
})

http.interceptors.request.use((config) => {
  const t = getToken()
  if (t) config.headers.Authorization = `Bearer ${t}`
  return config
})

http.interceptors.response.use(
  (r) => r,
  (err) => {
    const st = err.response?.status
    const data = err.response?.data
    const server = data?.error || data?.message
    let msg = server
    if (!msg) {
      if (err.code === 'ECONNABORTED') msg = '请求超时，请稍后重试'
      else if (err.message === 'Network Error') msg = '网络不可用，请检查后端是否已启动'
      else msg = err.message || '请求失败'
    }
    // 勿覆盖后端返回的 error；登录/注册 401 应为账号密码问题，其它请求才是会话失效
    if (st === 401 && !server) {
      const path = err.config?.url || ''
      const isAuthForm =
        path.includes('/auth/login') || path.includes('/auth/register')
      msg = isAuthForm ? '用户名或密码错误' : msg || '登录已失效，请重新登录'
    }
    else if (st === 403 && !server) msg = '暂无权限执行此操作（403）'
    else if (st === 404 && !server) msg = '资源不存在（404）'
    else if (st === 409) msg = server || '资源冲突（409），例如重复上传'
    else if (st === 429) msg = server || '请求过于频繁，请稍后再试'
    else if (st >= 500) msg = server || `服务器错误（${st}）`
    if (st === 401) {
      const path = err.config?.url || ''
      const isAuthForm =
        path.includes('/auth/login') || path.includes('/auth/register')
      if (
        !isAuthForm &&
        (data?.code === 'UNAUTHORIZED' ||
          (typeof data?.error === 'string' && data.error.includes('令牌')))
      ) {
        clearSessionAndRedirectToLogin()
      }
    }
    const e = new Error(msg)
    e.status = st
    e.data = data
    return Promise.reject(e)
  }
)

export default http
